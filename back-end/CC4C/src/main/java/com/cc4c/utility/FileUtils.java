package com.cc4c.utility;


import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

//文件工具类
public class FileUtils {
    /**
     * 将博客图片上传并存储
     * @param file (图片)文件
     * @param path1 文件存储的绝对路径
     * @param name 文件名
     * @param path2 文件请求的url路径
     * @return 文件存储是否成功，若成功返回图片的url请求路径，用于md编辑器的回显
     */
    public static String uploadImg(MultipartFile file, String path1, String name, String path2){
        String imgName = createImageName(name);
        String directoryName = randomImageDirectory();
        store(file, Path.of(path1).resolve(directoryName).resolve(imgName));
        return requestPath(path2, directoryName, imgName);
    }

    /**
     * 将用户头像上传并存储
     * @param file (图片)文件
     * @param path1 文件存储的绝对路径
     * @param name 文件名
     * @param path2 文件请求的url路径
     * @return 文件存储是否成功，若成功返回图片的url请求路径以及绝对路径，用于用户信息的存储以及前端图片的回显
     */
    public static Map<String, String> uploadAvatar(MultipartFile file, String path1, String name, String path2){
        String imgName = createImageName(name);
        String directoryName = randomImageDirectory();
        Path storedFile = Path.of(path1).resolve(directoryName).resolve(imgName);
        store(file, storedFile);

        Map<String, String> paths = new HashMap<>();
        paths.put("imgPath", storedFile.toString());
        paths.put("requestPath", requestPath(path2, directoryName, imgName));
        return paths;
    }

    private static String createImageName(String originalName) {
        return UUID.randomUUID().toString().replace("-", "") + originalName;
    }

    private static String randomImageDirectory() {
        return "img" + ThreadLocalRandom.current().nextInt(1, 6);
    }

    private static void store(MultipartFile file, Path targetFile) {
        try {
            Files.createDirectories(targetFile.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store uploaded file", exception);
        }
    }

    private static String requestPath(String basePath, String directoryName, String imageName) {
        return basePath.replaceAll("/+$", "") + "/" + directoryName + "/" + imageName;
    }
}
