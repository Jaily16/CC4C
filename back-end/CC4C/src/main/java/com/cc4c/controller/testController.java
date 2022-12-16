package com.cc4c.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Slf4j
@RestController
@RequestMapping("/test")
public class testController {
    @GetMapping()
    public String test(){
        return "test ok";
    }

    @PostMapping("/uploadImage")
    public Result upLoadImage(@RequestParam("img") MultipartFile uploadImage) {
        JSONObject json = new JSONObject();
        try {
            if (uploadImage == null) {
                json.put("STATUS", "ERROR");
                json.put("MSG", "上传失败，上传图片数据为空");
                return new Result(404, json, "failed");
            }

            String suffix = uploadImage.getContentType().toLowerCase();//图片后缀，用以识别哪种格式数据
            suffix = suffix.substring(suffix.lastIndexOf("/") + 1);

            if (suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("png") || suffix.equals("gif")) {
                String fileName = "test." + suffix;
                String imgFilePath = "D:\\testfile\\";//新生成的图片

                File targetFile = new File(imgFilePath, fileName);
                if (!targetFile.getParentFile().exists()) { //注意，判断父级路径是否存在
                    targetFile.getParentFile().mkdirs();
                }
                //保存
                uploadImage.transferTo(targetFile);

                json.put("STATUS", "200");
                json.put("MSG", "上传图片成功");
                return new Result(200,json, "success");
            }
            log.error("系统异常，上传图片格式非法");
            json.put("STATUS", "ERROR");
            json.put("MSG", "上传图片格式非法");
            return new Result(500, json, "failed");//500,系统异常
        } catch (Exception e) {
            log.error("系统异常，上传图片失败");
            json.put("STATUS", "ERROR");
            json.put("MSG", "系统异常，上传图片失败");
            return new Result(500, json, "failed");//500,系统异常
        }
    }
}

