package com.cc4ctools.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 独立生成观测门户 BCrypt 摘要，不启动 Spring 上下文或业务服务。 */
public final class ObservabilityPasswordHashApplication {
    private static final String PASSWORD_FILE_ENVIRONMENT = "CC4C_OBSERVABILITY_PASSWORD_FILE";

    /** 禁止实例化观测密码摘要工具。 */
    private ObservabilityPasswordHashApplication() {}

    /**
     * 从指定受保护文件生成摘要并输出；失败只输出固定提示并以 1 退出。
     *
     * @param args 命令行参数；本入口不自行解析
     */
    public static void main(String[] args) {
        try {
            System.out.println(hashPassword());
        } catch (RuntimeException | IOException exception) {
            System.err.println("Unable to create the observability password hash from the protected file.");
            System.exit(1);
        }
    }

    /**
     * 校验密码文件为规范普通文件且只有一行，要求 12–64 个码点及最多 72 个 UTF-8 字节，再以工作因子 12 编码。
     *
     * @return BCrypt 密码摘要
     * @throws IOException 密码文件读取失败时抛出
     */
    private static String hashPassword() throws IOException {
        String configured = System.getenv(PASSWORD_FILE_ENVIRONMENT);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("missing password file");
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || !path.equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalStateException("unsafe password file");
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != 1) {
            throw new IllegalStateException("password file must contain one line");
        }
        String password = lines.getFirst();
        int characters = password.codePointCount(0, password.length());
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (characters < 12 || characters > 64 || bytes > 72) {
            throw new IllegalStateException("password policy failed");
        }
        return new BCryptPasswordEncoder(12).encode(password);
    }
}
