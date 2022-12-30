package com.cc4c.utility;


import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class FileUtils {
    public static String uploadImg(MultipartFile file, String path1, String name, String path2){
        // 通过uuid产生一个图片名字
        String uuid = UUID.randomUUID().toString().trim().replaceAll("-","");
        String imgName = uuid + name;
        // 随机选择了一文件夹
        String code = Integer.toString(new Random().nextInt(5) + 1);
        // 拼接路径
        String imgPath = path1 + "img" + code + "\\";
        String requestPath = path2 + "img" + code + "/";

        try {
            // 上传操作
            File imgFile = new File(imgPath, imgName);
            if (!imgFile.getParentFile().exists()) { //注意，判断父级路径是否存在
                imgFile.getParentFile().mkdirs();
            }
            file.transferTo(imgFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 返回图片的路径
        return requestPath + imgName;
    }

    public static Map<String, String> uploadAvatar(MultipartFile file, String path1, String name, String path2){
        // 通过uuid产生一个图片名字
        String uuid = UUID.randomUUID().toString().trim().replaceAll("-","");
        String imgName = uuid + name;
        // 随机选择了一文件夹
        String code = Integer.toString(new Random().nextInt(5) + 1);
        // 拼接路径
        String imgPath = path1 + "img" + code + "\\";
        String requestPath = path2 + "img" + code + "/";

        try {
            // 上传操作
            File imgFile = new File(imgPath, imgName);
            if (!imgFile.getParentFile().exists()) { //注意，判断父级路径是否存在
                imgFile.getParentFile().mkdirs();
            }
            file.transferTo(imgFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 返回图片的路径
        Map<String, String> paths = new HashMap<>();
        paths.put("imgPath", imgPath + imgName);
        paths.put("requestPath", requestPath + imgName);
        return paths;
    }
}
