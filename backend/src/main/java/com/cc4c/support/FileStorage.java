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

/** 保存上传图片到随机分片目录，并返回磁盘路径与公开访问 URL；不负责内容类型鉴别。 */
public final class FileStorage {
    /** 禁止实例化文件存储工具。 */
    private FileStorage() {}

    /**
     * 去除原文件名中的路径和特殊字符，加上随机标识后写入 img1 至 img5 目录；I/O 失败向上传调用方抛出异常。
     *
     * @param file 待保存的上传文件
     * @param storageBase 图片写入根目录
     * @param requestBase 该根目录对应的公开 URL 前缀
     * @return 已写入文件的磁盘路径与访问 URL
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
     * 承载一次文件写入的磁盘位置和公开访问地址。
     *
     * @param path 已写入文件的绝对路径
     * @param requestUrl 文件的公开访问 URL
     */
    public record StoredFile(Path path, String requestUrl) {}
}
