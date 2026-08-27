package com.cc4c.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/test")
public class TestController {
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif");
    private final String saveImagePath;
    private final String requestImagePath;

    TestController(
            @Value("${cc4c.save-img-path}") String saveImagePath,
            @Value("${cc4c.request-img-path}") String requestImagePath) {
        this.saveImagePath = saveImagePath;
        this.requestImagePath = requestImagePath;
    }

    @GetMapping
    public String test() {
        return "test ok";
    }

    @PostMapping("/uploadImage")
    public EditorUploadResponse uploadImage(@RequestParam("file") MultipartFile image) {
        String contentType = image.getContentType();
        String suffix = contentType == null
                ? ""
                : contentType.substring(contentType.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_IMAGE_TYPES.contains(suffix)) {
            return EditorUploadResponse.error("上传图片格式非法");
        }
        try {
            return EditorUploadResponse.success(
                    FileStorage.storeImage(image, saveImagePath, requestImagePath).requestUrl());
        } catch (RuntimeException exception) {
            return EditorUploadResponse.error("系统异常，上传图片失败");
        }
    }
}
