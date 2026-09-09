package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.CommunityDtos.BlogDraftRequest;
import com.cc4c.dto.CommunityDtos.BlogResponse;
import com.cc4c.dto.CommunityDtos.BlogSubmitRequest;
import com.cc4c.dto.EditorUploadResponse;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResponse;
import com.cc4c.service.CommunityService;
import com.cc4c.support.FileStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 提供博客读取、作者草稿及发布删除入口，并将图片上传适配为编辑器响应。 */
@Validated
@RestController
@RequestMapping("/blogs")
public class CommunityController {
    private final CommunityService service;
    private final String saveImagePath;
    private final String requestImagePath;

    /**
     * 接入博客服务，并保存图片磁盘目录与公开路径前缀。
     *
     * @param service 博客读写服务
     * @param saveImagePath 博客图片磁盘存储目录
     * @param requestImagePath 博客图片公开请求路径前缀
     */
    CommunityController(
            CommunityService service,
            @Value("${cc4c.save-img-path}") String saveImagePath,
            @Value("${cc4c.request-img-path}") String requestImagePath) {
        this.service = service;
        this.saveImagePath = saveImagePath;
        this.requestImagePath = requestImagePath;
    }

    /**
     * 将图片写入配置的存储目录并返回公开 URL；运行异常统一转为编辑器上传失败提示。
     *
     * @param file 通过 multipart 提交的图片文件
     * @return 编辑器成功或失败协议响应
     */
    @PostMapping("/uploadImg")
    public EditorUploadResponse uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            return EditorUploadResponse.success(FileStorage.storeImage(file, saveImagePath, requestImagePath)
                    .requestUrl());
        } catch (RuntimeException exception) {
            return EditorUploadResponse.error("上传图片格式非法");
        }
    }

    /**
     * 分页读取博客首页数据。
     *
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 博客首页分页响应
     */
    @GetMapping("/home")
    public ApiResponse<PageResponse<BlogResponse>> home(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.home(new PageQuery(page, size))));
    }

    /**
     * 委托服务提交当前作者博客，成功使用 HTTP 201。
     *
     * @param request 已校验的博客标题、正文和语言列表
     * @return 新提交博客的响应
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<BlogResponse>> submit(@Valid @RequestBody BlogSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.submit(request)));
    }

    /**
     * 委托服务按当前作者权限删除指定博客。
     *
     * @param blogId 正数博客 ID
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    public ApiResponse<Boolean> delete(@RequestParam @Positive long blogId) {
        return ApiResponse.success(service.delete(blogId));
    }

    /**
     * 分页查询当前登录作者的博客。
     *
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 当前作者的博客分页响应
     */
    @GetMapping("/myBlogs")
    public ApiResponse<PageResponse<BlogResponse>> myBlogs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.byCurrentWriter(new PageQuery(page, size))));
    }

    /**
     * 按博客 ID 读取详情，具体可见性由服务校验。
     *
     * @param id 正数博客 ID
     * @return 博客详情响应
     */
    @GetMapping("/{id}")
    public ApiResponse<BlogResponse> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(service.detail(id));
    }

    /**
     * 按语言 ID 分页查询可展示博客。
     *
     * @param languageId 正数语言 ID
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 语言关联博客的分页响应
     */
    @GetMapping("/list/{languageId}")
    public ApiResponse<PageResponse<BlogResponse>> byLanguage(
            @PathVariable @Positive int languageId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.byLanguage(languageId, new PageQuery(page, size))));
    }

    /**
     * 通过博客服务分页读取列表。
     *
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 博客列表分页响应
     */
    @GetMapping("/all")
    public ApiResponse<PageResponse<BlogResponse>> all(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.all(new PageQuery(page, size))));
    }

    /**
     * 委托服务保存当前用户的博客草稿正文。
     *
     * @param request 已校验的当前用户草稿正文
     * @return 草稿保存结果
     */
    @PutMapping("/draft")
    public ApiResponse<Boolean> saveDraft(@Valid @RequestBody BlogDraftRequest request) {
        return ApiResponse.success(service.saveDraft(request));
    }

    /**
     * 读取当前用户已保存的草稿正文。
     *
     * @return 草稿正文响应
     */
    @GetMapping("/draft")
    public ApiResponse<String> draft() {
        return ApiResponse.success(service.draft());
    }

    /**
     * 删除当前用户的博客草稿。
     *
     * @return 草稿删除结果
     */
    @DeleteMapping("/draft")
    public ApiResponse<Boolean> deleteDraft() {
        return ApiResponse.success(service.deleteDraft());
    }

    /**
     * 按标题检索词分页查询博客。
     *
     * @param info 博客标题检索词，长度 1 至 75
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 匹配博客的分页响应
     */
    @GetMapping("/search/{info}")
    public ApiResponse<PageResponse<BlogResponse>> search(
            @PathVariable @Size(min = 1, max = 75) String info,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.search(info, new PageQuery(page, size))));
    }

    /**
     * 委托博客服务增加指定博客的点击计数。
     *
     * @param id 正数博客 ID
     * @return 点击计数更新结果
     */
    @PutMapping("/click/{id}")
    public ApiResponse<Boolean> click(@PathVariable @Positive long id) {
        return ApiResponse.success(service.click(id));
    }
}
