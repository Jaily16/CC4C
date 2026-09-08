import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * 检查后端生产 Java 的中文 Javadoc 覆盖率及标签完整性。
 *
 * <p>文件范围完全来自 Git 清单；Git 查询失败时立即退出，不递归遍历工作区，也不读取本机配置或数据。
 */
public final class JavaCommentCoverage {
    private static final Pattern CHINESE = Pattern.compile("[\\u3400-\\u9fff]");
    private static final Pattern ESCAPED_UNICODE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    private static final Pattern RECORD_COMPONENT_NAME =
            Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:\\[\\])?\\s*$");
    private static final int EXPECTED_PACKAGES = 13;
    private static final int EXPECTED_TYPES = 228;
    private static final int EXPECTED_CONSTRUCTORS = 108;
    private static final int EXPECTED_METHODS = 587;
    private static final int EXPECTED_TOTAL = 936;

    private JavaCommentCoverage() {}

    /**
     * 解析命令行、取得 Git 文件清单并执行覆盖检查。
     *
     * @param arguments 仅接受 {@code --repository-root <路径>}
     * @throws Exception Git、文件或 Java 语法检查无法可靠完成时抛出
     */
    public static void main(String[] arguments) throws Exception {
        Path repositoryRoot = parseRepositoryRoot(arguments);
        List<Path> sources = listJavaSources(repositoryRoot);
        Coverage coverage = inspect(repositoryRoot, sources);

        System.out.printf(
                Locale.ROOT,
                "Java 中文 Javadoc 覆盖：package %d/%d，类型 %d/%d，构造器 %d/%d，方法 %d/%d，总计 %d/%d。%n",
                coverage.documentedPackages,
                coverage.packages,
                coverage.documentedTypes,
                coverage.types,
                coverage.documentedConstructors,
                coverage.constructors,
                coverage.documentedMethods,
                coverage.methods,
                coverage.documentedTotal(),
                coverage.total());

        for (String error : coverage.errors) {
            System.err.println(error);
        }
        boolean inventoryMatches = coverage.packages == EXPECTED_PACKAGES
                && coverage.types == EXPECTED_TYPES
                && coverage.constructors == EXPECTED_CONSTRUCTORS
                && coverage.methods == EXPECTED_METHODS
                && coverage.total() == EXPECTED_TOTAL;
        if (!inventoryMatches) {
            System.err.printf(
                    Locale.ROOT,
                    "声明清单与 V6 方面二基线不一致：期望 package/type/constructor/method/total = %d/%d/%d/%d/%d。%n",
                    EXPECTED_PACKAGES,
                    EXPECTED_TYPES,
                    EXPECTED_CONSTRUCTORS,
                    EXPECTED_METHODS,
                    EXPECTED_TOTAL);
        }
        if (!inventoryMatches || !coverage.errors.isEmpty() || coverage.documentedTotal() != EXPECTED_TOTAL) {
            System.exit(1);
        }
    }

    /**
     * 读取并验证仓库根目录参数。
     *
     * @param arguments 命令行参数
     * @return 已规范化且不经过 reparse point 的仓库根目录
     * @throws IOException 路径不存在、不是普通目录或经过符号链接时抛出
     */
    private static Path parseRepositoryRoot(String[] arguments) throws IOException {
        if (arguments.length != 2 || !"--repository-root".equals(arguments[0])) {
            throw new IllegalArgumentException("用法：JavaCommentCoverage --repository-root <仓库根目录>");
        }
        Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("仓库根目录不是可接受的普通目录：" + root);
        }
        return root;
    }

    /**
     * 从 Git tracked 与非忽略未跟踪清单中取得仍存在的生产 Java 文件。
     *
     * @param repositoryRoot 仓库根目录
     * @return 按 Git 路径排序的 Java 源文件
     * @throws Exception Git 查询失败或文件路径不安全时抛出
     */
    private static List<Path> listJavaSources(Path repositoryRoot) throws Exception {
        Set<String> candidates = new LinkedHashSet<>(runGitPaths(
                repositoryRoot,
                "ls-files",
                "-z",
                "--cached",
                "--others",
                "--exclude-standard",
                "--",
                "backend/src/main/java/**/*.java"));
        Set<String> deleted = new HashSet<>(runGitPaths(
                repositoryRoot, "ls-files", "-z", "--deleted", "--", "backend/src/main/java/**/*.java"));
        candidates.removeAll(deleted);

        List<Path> result = new ArrayList<>();
        for (String relative : candidates.stream().sorted().toList()) {
            Path path = repositoryRoot.resolve(relative).normalize();
            requireSafeFile(repositoryRoot, path);
            result.add(path);
        }
        if (result.isEmpty()) {
            throw new IOException("Git 清单中没有后端生产 Java 文件。");
        }
        return result;
    }

    /**
     * 执行只读 Git 路径查询并解析 NUL 分隔结果。
     *
     * @param repositoryRoot Git 工作区
     * @param arguments Git 子命令参数
     * @return 仓库相对路径集合
     * @throws Exception Git 返回非零码时抛出
     */
    private static List<String> runGitPaths(Path repositoryRoot, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repositoryRoot.toString());
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Git 文件清单查询失败：" + output.toString(StandardCharsets.UTF_8));
        }
        byte[] bytes = output.toByteArray();
        List<String> paths = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= bytes.length; index++) {
            if (index == bytes.length || bytes[index] == 0) {
                if (index > start) {
                    paths.add(new String(bytes, start, index - start, StandardCharsets.UTF_8));
                }
                start = index + 1;
            }
        }
        return paths;
    }

    /**
     * 确认候选源码位于仓库内，且文件及全部父路径均不经过符号链接。
     *
     * @param repositoryRoot 仓库根目录
     * @param file 候选文件
     * @throws IOException 路径越界、缺失或包含链接时抛出
     */
    private static void requireSafeFile(Path repositoryRoot, Path file) throws IOException {
        if (!file.startsWith(repositoryRoot) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("源码不是仓库内普通文件：" + file);
        }
        Path cursor = file;
        while (cursor != null && cursor.startsWith(repositoryRoot)) {
            if (Files.isSymbolicLink(cursor)) {
                throw new IOException("源码路径经过符号链接：" + file);
            }
            if (cursor.equals(repositoryRoot)) {
                return;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("无法验证源码父路径：" + file);
    }

    /**
     * 使用 JDK Compiler Tree API 解析全部生产源码并检查文档。
     *
     * @param repositoryRoot 仓库根目录
     * @param sources Java 源文件
     * @return 覆盖计数和逐项错误
     * @throws IOException 编译器或源码读取失败时抛出
     */
    private static Coverage inspect(Path repositoryRoot, List<Path> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("当前 Java 不是完整 JDK，无法取得 Compiler Tree API。");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Coverage coverage = new Coverage();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:none", "-encoding", "UTF-8", "--release", "21"),
                    null,
                    units);
            Iterable<? extends CompilationUnitTree> parsed = task.parse();
            DocTrees docTrees = DocTrees.instance(task);
            SourcePositions positions = docTrees.getSourcePositions();
            for (CompilationUnitTree unit : parsed) {
                Path path = Paths.get(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                String relative = repositoryRoot.relativize(path).toString().replace('\\', '/');
                String source = Files.readString(path, StandardCharsets.UTF_8);
                new CoverageScanner(docTrees, positions, unit, relative, source, coverage).scan(unit, null);
            }
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                String path = diagnostic.getSource() == null
                        ? "<unknown>"
                        : Paths.get(diagnostic.getSource().toUri()).getFileName().toString();
                coverage.errors.add(path + ":" + diagnostic.getLineNumber() + ": Java 语法错误："
                        + diagnostic.getMessage(Locale.ROOT));
            }
        }
        return coverage;
    }

    /** 汇总各类声明及通过文档检查的数量。 */
    private static final class Coverage {
        private int packages;
        private int documentedPackages;
        private int types;
        private int documentedTypes;
        private int constructors;
        private int documentedConstructors;
        private int methods;
        private int documentedMethods;
        private final List<String> errors = new ArrayList<>();

        /**
         * 返回全部需要检查的文档单元数。
         *
         * @return package、类型、构造器和方法计数之和
         */
        private int total() {
            return packages + types + constructors + methods;
        }

        /**
         * 返回通过检查的文档单元数。
         *
         * @return 四类已通过计数之和
         */
        private int documentedTotal() {
            return documentedPackages + documentedTypes + documentedConstructors + documentedMethods;
        }
    }

    /** 遍历单个编译单元，并按声明真实签名校验相邻 Javadoc。 */
    private static final class CoverageScanner extends TreePathScanner<Void, Void> {
        private final DocTrees docTrees;
        private final SourcePositions positions;
        private final CompilationUnitTree unit;
        private final String relativePath;
        private final String source;
        private final Coverage coverage;

        /**
         * 保存当前源码的文档树、位置和计数上下文。
         *
         * @param docTrees Javadoc 查询入口
         * @param positions 源码位置查询入口
         * @param unit 当前编译单元
         * @param relativePath 仓库相对路径
         * @param source UTF-8 源码正文
         * @param coverage 全局覆盖结果
         */
        private CoverageScanner(
                DocTrees docTrees,
                SourcePositions positions,
                CompilationUnitTree unit,
                String relativePath,
                String source,
                Coverage coverage) {
            this.docTrees = docTrees;
            this.positions = positions;
            this.unit = unit;
            this.relativePath = relativePath;
            this.source = source;
            this.coverage = coverage;
        }

        /**
         * 检查 package-info 的中文包说明。
         *
         * @param node 当前编译单元
         * @param unused 未使用的扫描上下文
         * @return 父类遍历结果
         */
        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, Void unused) {
            if (relativePath.endsWith("/package-info.java")) {
                coverage.packages++;
                Tree packageTree = node.getPackage();
                DocCommentTree doc = packageTree == null
                        ? null
                        : docTrees.getDocCommentTree(new TreePath(getCurrentPath(), packageTree));
                if (validateDoc(doc, packageTree == null ? node : packageTree, "包", List.of(), List.of(), false)) {
                    coverage.documentedPackages++;
                }
            }
            return super.visitCompilationUnit(node, unused);
        }

        /**
         * 检查具名类、接口、枚举、注解和 record 的中文说明及类型参数标签。
         *
         * @param node 类型声明
         * @param unused 未使用的扫描上下文
         * @return 父类遍历结果
         */
        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (!node.getSimpleName().toString().isBlank()) {
                coverage.types++;
                List<ExpectedParam> parameters = new ArrayList<>();
                for (TypeParameterTree parameter : node.getTypeParameters()) {
                    parameters.add(new ExpectedParam(parameter.getName().toString(), true));
                }
                if (node.getKind() == Tree.Kind.RECORD) {
                    for (String component : recordComponentNames(node)) {
                        parameters.add(new ExpectedParam(component, false));
                    }
                }
                if (validateDoc(
                        docTrees.getDocCommentTree(getCurrentPath()), node, "类型", parameters, List.of(), false)) {
                    coverage.documentedTypes++;
                }
            }
            return super.visitClass(node, unused);
        }

        /**
         * 检查显式构造器和方法的中文说明、参数、返回值及异常标签。
         *
         * @param node 方法或构造器声明
         * @param unused 未使用的扫描上下文
         * @return 父类遍历结果
         */
        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            boolean constructor = node.getReturnType() == null;
            if (constructor) {
                coverage.constructors++;
            } else {
                coverage.methods++;
            }
            List<ExpectedParam> parameters = new ArrayList<>();
            for (TypeParameterTree parameter : node.getTypeParameters()) {
                parameters.add(new ExpectedParam(parameter.getName().toString(), true));
            }
            for (VariableTree parameter : node.getParameters()) {
                parameters.add(new ExpectedParam(parameter.getName().toString(), false));
            }
            List<String> thrown = node.getThrows().stream().map(ExpressionTree::toString).toList();
            boolean needsReturn = !constructor && !"void".equals(node.getReturnType().toString());
            boolean valid = validateDoc(
                    docTrees.getDocCommentTree(getCurrentPath()),
                    node,
                    constructor ? "构造器" : "方法",
                    parameters,
                    thrown,
                    needsReturn);
            if (valid) {
                if (constructor) {
                    coverage.documentedConstructors++;
                } else {
                    coverage.documentedMethods++;
                }
            }
            return super.visitMethod(node, unused);
        }

        /**
         * 按当前 record 声明头解析组件名称，避免把普通字段误当作组件。
         *
         * @param node record 类型节点
         * @return 保持声明顺序的组件名称
         */
        private List<String> recordComponentNames(ClassTree node) {
            long start = positions.getStartPosition(unit, node);
            long end = positions.getEndPosition(unit, node);
            if (start < 0 || end <= start) {
                addError(node, "无法定位 record 组件声明");
                return List.of();
            }
            int namePosition = source.indexOf(node.getSimpleName().toString(), (int) start);
            int open = namePosition < 0 ? -1 : source.indexOf('(', namePosition + node.getSimpleName().length());
            if (open < 0 || open >= end) {
                addError(node, "无法找到 record 组件列表");
                return List.of();
            }
            int close = findMatchingParenthesis(source, open, (int) end);
            if (close < 0) {
                addError(node, "record 组件括号不完整");
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (String component : splitTopLevel(source.substring(open + 1, close))) {
                if (component.isBlank()) {
                    continue;
                }
                Matcher matcher = RECORD_COMPONENT_NAME.matcher(component.strip());
                if (!matcher.find()) {
                    addError(node, "无法解析 record 组件：" + component.strip());
                } else {
                    names.add(matcher.group(1));
                }
            }
            return names;
        }

        /**
         * 验证一项声明的中文正文和所需 Javadoc 标签。
         *
         * @param doc Javadoc 语法树
         * @param declaration 声明节点
         * @param kind 错误信息中的声明类型
         * @param parameters 所需值参数与类型参数
         * @param thrown 显式 throws 类型
         * @param needsReturn 是否要求返回值说明
         * @return 全部条件满足时为 true
         */
        private boolean validateDoc(
                DocCommentTree doc,
                Tree declaration,
                String kind,
                List<ExpectedParam> parameters,
                List<String> thrown,
                boolean needsReturn) {
            if (doc == null) {
                addError(declaration, kind + "缺少中文 Javadoc");
                return false;
            }
            boolean valid = true;
            StringBuilder body = new StringBuilder();
            doc.getFirstSentence().forEach(part -> body.append(part.toString()));
            doc.getBody().forEach(part -> body.append(part.toString()));
            if (!containsChinese(body.toString())) {
                addError(declaration, kind + "Javadoc 正文不含中文说明");
                valid = false;
            }
            List<? extends DocTree> tags = doc.getBlockTags();
            for (ExpectedParam expected : parameters) {
                ParamTree parameterTag = tags.stream()
                        .filter(ParamTree.class::isInstance)
                        .map(ParamTree.class::cast)
                        .filter(tag -> tag.isTypeParameter() == expected.typeParameter
                                && tag.getName().getName().contentEquals(expected.name))
                        .findFirst()
                        .orElse(null);
                if (parameterTag == null) {
                    addError(
                            declaration,
                            kind + "缺少 @param " + (expected.typeParameter ? "<" + expected.name + ">" : expected.name));
                    valid = false;
                } else if (!containsChinese(parameterTag.getDescription().toString())) {
                    addError(
                            declaration,
                            kind + "的 @param "
                                    + (expected.typeParameter ? "<" + expected.name + ">" : expected.name)
                                    + " 缺少中文含义");
                    valid = false;
                }
            }
            if (needsReturn) {
                ReturnTree returnTag = tags.stream()
                        .filter(ReturnTree.class::isInstance)
                        .map(ReturnTree.class::cast)
                        .findFirst()
                        .orElse(null);
                if (returnTag == null) {
                    addError(declaration, kind + "缺少 @return");
                    valid = false;
                } else if (!containsChinese(returnTag.getDescription().toString())) {
                    addError(declaration, kind + "的 @return 缺少中文含义");
                    valid = false;
                }
            }
            for (String exceptionType : thrown) {
                ThrowsTree throwsTag = tags.stream()
                        .filter(ThrowsTree.class::isInstance)
                        .map(ThrowsTree.class::cast)
                        .filter(tag -> sameException(tag.getExceptionName().toString(), exceptionType))
                        .findFirst()
                        .orElse(null);
                if (throwsTag == null) {
                    addError(declaration, kind + "缺少 @throws " + exceptionType);
                    valid = false;
                } else if (!containsChinese(throwsTag.getDescription().toString())) {
                    addError(declaration, kind + "的 @throws " + exceptionType + " 缺少中文含义");
                    valid = false;
                }
            }
            return valid;
        }

        /**
         * 比较 throws 标签中的简单名或限定名是否对应同一异常。
         *
         * @param documented Javadoc 中的异常名称
         * @param declared 方法签名中的异常名称
         * @return 名称相同或简单名相同时为 true
         */
        private boolean sameException(String documented, String declared) {
            return documented.equals(declared) || simpleName(documented).equals(simpleName(declared));
        }

        /**
         * 识别 Javadoc 树中的中文；Javac 可能把非 ASCII 字符呈现为 Unicode 转义。
         *
         * @param value Javadoc 树的文本表示
         * @return 包含中日韩统一表意字符时为 true
         */
        private boolean containsChinese(String value) {
            if (CHINESE.matcher(value).find()) {
                return true;
            }
            Matcher matcher = ESCAPED_UNICODE.matcher(value);
            while (matcher.find()) {
                int codePoint = Integer.parseInt(matcher.group(1), 16);
                if (codePoint >= 0x3400 && codePoint <= 0x9fff) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 提取 Java 类型名称的最后一段。
         *
         * @param value 限定名或简单名
         * @return 简单类型名
         */
        private String simpleName(String value) {
            int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf('$'));
            return separator < 0 ? value : value.substring(separator + 1);
        }

        /**
         * 添加带仓库路径和源码行号的稳定错误信息。
         *
         * @param tree 发生问题的语法树节点
         * @param message 中文错误原因
         */
        private void addError(Tree tree, String message) {
            long position = positions.getStartPosition(unit, tree);
            long line = position < 0 ? 1 : unit.getLineMap().getLineNumber(position);
            coverage.errors.add(relativePath + ":" + line + ": " + message);
        }
    }

    /** 表示方法、构造器、类型参数或 record 组件需要的一个参数标签。 */
    private record ExpectedParam(String name, boolean typeParameter) {}

    /**
     * 查找与给定左括号配对的右括号，同时跳过字符串和字符字面量。
     *
     * @param text 完整源码
     * @param open 左括号位置
     * @param limit 搜索上限
     * @return 右括号位置，无法配对时返回 -1
     */
    private static int findMatchingParenthesis(String text, int open, int limit) {
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        for (int index = open; index < limit; index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '\\') {
                    index++;
                } else if (current == quote) {
                    quoted = false;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quoted = true;
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 按顶层逗号拆分 record 组件，保留泛型、注解及数组内部的逗号。
     *
     * @param value record 圆括号内部文本
     * @return 组件声明片段
     */
    private static List<String> splitTopLevel(String value) {
        List<String> parts = new ArrayList<>();
        int angle = 0;
        int round = 0;
        int square = 0;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '<' -> angle++;
                case '>' -> angle = Math.max(0, angle - 1);
                case '(' -> round++;
                case ')' -> round = Math.max(0, round - 1);
                case '[' -> square++;
                case ']' -> square = Math.max(0, square - 1);
                case ',' -> {
                    if (angle == 0 && round == 0 && square == 0) {
                        parts.add(value.substring(start, index));
                        start = index + 1;
                    }
                }
                default -> {
                    // 其他字符不改变嵌套层级。
                }
            }
        }
        parts.add(value.substring(start));
        return parts;
    }
}
