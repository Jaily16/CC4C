package com.cc4c.shared;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class FileStorage {
    private FileStorage() {
    }

    public static StoredFile storeImage(MultipartFile file, String storageBase, String requestBase) {
        String originalName = file.getOriginalFilename();
        if (file.isEmpty() || originalName == null || originalName.isBlank()) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    BusinessCode.VALIDATION_ERROR,
                    "Uploaded file is empty");
        }

        String normalizedOriginalName = originalName.replace('\\', '/');
        String safeOriginalName = normalizedOriginalName.substring(
                normalizedOriginalName.lastIndexOf('/') + 1)
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

    public record StoredFile(Path path, String requestUrl) {
    }
}
