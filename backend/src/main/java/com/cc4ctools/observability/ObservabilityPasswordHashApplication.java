package com.cc4ctools.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * ObservabilityPasswordHashApplication 负责独立观测门户的一项明确运行职责，并保持现有外部行为不变。
 */
public final class ObservabilityPasswordHashApplication {
    private static final String PASSWORD_FILE_ENVIRONMENT = "CC4C_OBSERVABILITY_PASSWORD_FILE";

    /**
     * 创建 ObservabilityPasswordHashApplication 实例，不触发外部 I/O。
     */
    private ObservabilityPasswordHashApplication() {}

    /**
     * 启动对应命令行或 Spring Boot 进程，并把退出状态交给调用环境。
     *
     * @param args 调用方提供的 {@code args} 值
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
     * 判断观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 按当前协议生成或读取的字符串值
     * @throws IOException 当输入、数据或依赖状态不满足当前方法约束时抛出
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
