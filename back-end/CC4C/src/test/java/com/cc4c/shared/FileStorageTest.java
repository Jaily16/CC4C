package com.cc4c.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void uploadedFileNamesCannotEscapeTheConfiguredStorageRoot() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../outside.png",
                "image/png",
                new byte[]{1, 2, 3});

        FileStorage.StoredFile stored = FileStorage.storeImage(
                file, storageRoot.toString(), "http://localhost:5173/test");

        assertTrue(stored.path().startsWith(storageRoot.toAbsolutePath().normalize()));
        assertTrue(Files.exists(stored.path()));
        assertTrue(stored.requestUrl().startsWith("http://localhost:5173/test/img"));
    }
}
