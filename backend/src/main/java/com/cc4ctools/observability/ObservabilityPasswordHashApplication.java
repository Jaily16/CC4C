package com.cc4ctools.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** ObservabilityPasswordHashApplication 从仓库外单行文件生成 BCrypt cost-12 哈希，不启动业务应用。 */
public final class ObservabilityPasswordHashApplication {
    private static final String PASSWORD_FILE_ENVIRONMENT = "CC4C_OBSERVABILITY_PASSWORD_FILE";

    private ObservabilityPasswordHashApplication() {}

    public static void main(String[] args) {
        try {
            System.out.println(hashPassword());
        } catch (RuntimeException | IOException exception) {
            System.err.println("Unable to create the observability password hash from the protected file.");
            System.exit(1);
        }
    }

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
