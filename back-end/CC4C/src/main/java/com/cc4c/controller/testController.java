package com.cc4c.controller;

import com.alibaba.fastjson.JSONObject;
import com.cc4c.utility.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/test")
public class testController {
    @Value("${cc4c.save-img-path}")
    private String saveImgPath;

    @Value("${cc4c.request-img-path}")
    private String requestImgPath;

    @GetMapping()
    public String test(){
        return "test ok";
    }

    @PostMapping("/uploadImage")
    public JSONObject upLoadImage(@RequestParam("file") MultipartFile uploadImage) {
        JSONObject json = new JSONObject();
        try {
            if (uploadImage == null) {
                json.put("STATUS", "ERROR");
                json.put("MSG", "上传失败，上传图片数据为空");
                return json;
            }

            String suffix = uploadImage.getContentType().toLowerCase();//图片后缀，用以识别哪种格式数据
            suffix = suffix.substring(suffix.lastIndexOf("/") + 1);

            if (suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("png") || suffix.equals("gif")) {
                String fileName = "test3." + suffix;
                String imgFilePath = "G:\\Code Programming\\SpringBoot_java\\CC4C\\src\\main\\resources\\static\\img";//新生成的图片

                File targetFile = new File(imgFilePath, fileName);
                if (!targetFile.getParentFile().exists()) { //注意，判断父级路径是否存在
                    targetFile.getParentFile().mkdirs();
                }
                //保存
                uploadImage.transferTo(targetFile);

                json.put("success", "1");
                json.put("message", "success");
                json.put("url", "http://localhost:4080//img/test3.png");
                return json;
            }
            log.error("系统异常，上传图片格式非法");
            json.put("STATUS", "ERROR");
            json.put("MSG", "上传图片格式非法");
            return json;
        } catch (Exception e) {
            log.error("系统异常，上传图片失败");
            json.put("STATUS", "ERROR");
            json.put("MSG", "系统异常，上传图片失败");
            return json;
        }
    }

}

