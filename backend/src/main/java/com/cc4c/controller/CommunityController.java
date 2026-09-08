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

/**
 * CommunityController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Validated
@RestController
@RequestMapping("/blogs")
public class CommunityController {
    private final CommunityService service;
    private final String saveImagePath;
    private final String requestImagePath;

    /**
     * 创建 CommunityController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param service 由容器注入的 CommunityService 协作组件
     * @param saveImagePath 调用方提供的 {@code saveImagePath} 值
     * @param requestImagePath 调用方提供的 {@code requestImagePath} 值
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
     * 执行 CommunityController 中的 uploadImage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param file 调用方提供的 {@code file} 值
     * @return 按当前声明计算、查询或转换得到的结果
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
     * 执行 CommunityController 中的 home 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/home")
    public ApiResponse<PageResponse<BlogResponse>> home(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.home(new PageQuery(page, size))));
    }

    /**
     * 执行 CommunityController 中的 submit 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<BlogResponse>> submit(@Valid @RequestBody BlogSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.submit(request)));
    }

    /**
     * 删除 CommunityController 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @DeleteMapping("/delete")
    public ApiResponse<Boolean> delete(@RequestParam @Positive long blogId) {
        return ApiResponse.success(service.delete(blogId));
    }

    /**
     * 执行 CommunityController 中的 myBlogs 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/myBlogs")
    public ApiResponse<PageResponse<BlogResponse>> myBlogs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.byCurrentWriter(new PageQuery(page, size))));
    }

    /**
     * 执行 CommunityController 中的 detail 职责，并保持既有权限、事务与副作用边界。
     *
     * @param id 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/{id}")
    public ApiResponse<BlogResponse> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(service.detail(id));
    }

    /**
     * 执行 CommunityController 中的 byLanguage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageId 目标对象的稳定标识
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/list/{languageId}")
    public ApiResponse<PageResponse<BlogResponse>> byLanguage(
            @PathVariable @Positive int languageId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.byLanguage(languageId, new PageQuery(page, size))));
    }

    /**
     * 执行 CommunityController 中的 all 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/all")
    public ApiResponse<PageResponse<BlogResponse>> all(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.all(new PageQuery(page, size))));
    }

    /**
     * 执行 CommunityController 中的 saveDraft 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PutMapping("/draft")
    public ApiResponse<Boolean> saveDraft(@Valid @RequestBody BlogDraftRequest request) {
        return ApiResponse.success(service.saveDraft(request));
    }

    /**
     * 执行 CommunityController 中的 draft 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/draft")
    public ApiResponse<String> draft() {
        return ApiResponse.success(service.draft());
    }

    /**
     * 删除 CommunityController 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @DeleteMapping("/draft")
    public ApiResponse<Boolean> deleteDraft() {
        return ApiResponse.success(service.deleteDraft());
    }

    /**
     * 执行 CommunityController 中的 search 职责，并保持既有权限、事务与副作用边界。
     *
     * @param info 调用方提供的 {@code info} 值
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/search/{info}")
    public ApiResponse<PageResponse<BlogResponse>> search(
            @PathVariable @Size(min = 1, max = 75) String info,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.search(info, new PageQuery(page, size))));
    }

    /**
     * 执行 CommunityController 中的 click 职责，并保持既有权限、事务与副作用边界。
     *
     * @param id 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PutMapping("/click/{id}")
    public ApiResponse<Boolean> click(@PathVariable @Positive long id) {
        return ApiResponse.success(service.click(id));
    }
}
