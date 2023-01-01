package com.cc4c.utility;


import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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

    /**
     * 将用户头像上传并存储
     * @param file (图片)文件
     * @param path1 文件存储的绝对路径
     * @param name 文件名
     * @param path2 文件请求的url路径
     * @return 文件存储是否成功，若成功返回图片的url请求路径以及绝对路径，用于用户信息的存储以及前端图片的回显
     */
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
