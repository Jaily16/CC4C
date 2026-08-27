package com.cc4c.controller;

import com.cc4c.utility.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif");

    @Value("${cc4c.save-img-path}")
    private String saveImgPath;

    @Value("${cc4c.request-img-path}")
    private String requestImgPath;

    @GetMapping
    public String test() {
        return "test ok";
    }

    @PostMapping("/uploadImage")
    public Map<String, String> uploadImage(@RequestParam("file") MultipartFile uploadImage) {
        Map<String, String> response = new LinkedHashMap<>();
        try {
            if (uploadImage == null || uploadImage.isEmpty()) {
                response.put("STATUS", "ERROR");
                response.put("MSG", "上传失败，上传图片数据为空");
                return response;
            }

            String contentType = uploadImage.getContentType();
            String suffix = contentType == null
                    ? ""
                    : contentType.substring(contentType.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (!SUPPORTED_IMAGE_TYPES.contains(suffix)) {
                log.error("系统异常，上传图片格式非法");
                response.put("STATUS", "ERROR");
                response.put("MSG", "上传图片格式非法");
                return response;
            }

            String url = FileUtils.uploadImg(
                    uploadImage,
                    saveImgPath,
                    Objects.requireNonNull(uploadImage.getOriginalFilename()),
                    requestImgPath);
            response.put("success", "1");
            response.put("message", "success");
            response.put("url", url);
            return response;
        } catch (Exception exception) {
            log.error("系统异常，上传图片失败", exception);
            response.put("STATUS", "ERROR");
            response.put("MSG", "系统异常，上传图片失败");
            return response;
        }
    }
}
