package com.cc4c.support;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.web.multipart.MultipartFile;

/**
 * FileStorage 负责公共技术支撑的一项明确运行职责，并保持现有外部行为不变。
 */
public final class FileStorage {
    /**
     * 创建 FileStorage 实例，不触发外部 I/O。
     */
    private FileStorage() {}

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param file 待校验或保存的上传文件
     * @param storageBase 调用方提供的 {@code storageBase} 值
     * @param requestBase 调用方提供的 {@code requestBase} 值
     * @return 当前操作产生的 StoredFile 结果
     */
    public static StoredFile storeImage(MultipartFile file, String storageBase, String requestBase) {
        String originalName = file.getOriginalFilename();
        if (file.isEmpty() || originalName == null || originalName.isBlank()) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    BusinessCode.VALIDATION_ERROR,
                    "Uploaded file is empty");
        }

        String normalizedOriginalName = originalName.replace('\\', '/');
        String safeOriginalName = normalizedOriginalName
                .substring(normalizedOriginalName.lastIndexOf('/') + 1)
                .replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (safeOriginalName.isBlank()) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    BusinessCode.VALIDATION_ERROR,
                    "Uploaded file name is invalid");
        }
        String imageName = UUID.randomUUID().toString().replace("-", "") + safeOriginalName;
        String directoryName = "img" + ThreadLocalRandom.current().nextInt(1, 6);
        Path storageRoot = Path.of(storageBase).toAbsolutePath().normalize();
        Path target = storageRoot.resolve(directoryName).resolve(imageName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    BusinessCode.VALIDATION_ERROR,
                    "Uploaded file name is invalid");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store uploaded file", exception);
        }

        String url = requestBase.replaceAll("/+$", "") + "/" + directoryName + "/" + imageName;
        return new StoredFile(target, url);
    }

    /**
     * StoredFile 以不可变结构承载共享基础设施数据，并保持现有字段语义。
     *
     * @param path 已验证边界内的文件或请求路径
     * @param requestUrl 调用方提供的 {@code requestUrl} 值
     */
    public record StoredFile(Path path, String requestUrl) {}
}
