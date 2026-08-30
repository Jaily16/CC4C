package com.cc4c.community;

import com.cc4c.community.CommunityDtos.BlogDraftRequest;
import com.cc4c.community.CommunityDtos.BlogResponse;
import com.cc4c.community.CommunityDtos.BlogSubmitRequest;
import com.cc4c.community.internal.CommunityService;
import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.EditorUploadResponse;
import com.cc4c.shared.FileStorage;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResponse;
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

@Validated
@RestController
@RequestMapping("/blogs")
/** CommunityController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class CommunityController {
    private final CommunityService service;
    private final String saveImagePath;
    private final String requestImagePath;

    CommunityController(
            CommunityService service,
            @Value("${cc4c.save-img-path}") String saveImagePath,
            @Value("${cc4c.request-img-path}") String requestImagePath) {
        this.service = service;
        this.saveImagePath = saveImagePath;
        this.requestImagePath = requestImagePath;
    }

    @PostMapping("/uploadImg")
    public EditorUploadResponse uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            return EditorUploadResponse.success(FileStorage.storeImage(file, saveImagePath, requestImagePath)
                    .requestUrl());
        } catch (RuntimeException exception) {
            return EditorUploadResponse.error("上传图片格式非法");
        }
    }

    @GetMapping("/home")
    public ApiResponse<PageResponse<BlogResponse>> home(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.home(new PageQuery(page, size))));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<BlogResponse>> submit(@Valid @RequestBody BlogSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.submit(request)));
    }

    @DeleteMapping("/delete")
    public ApiResponse<Boolean> delete(@RequestParam @Positive long blogId) {
        return ApiResponse.success(service.delete(blogId));
    }

    @GetMapping("/myBlogs")
    public ApiResponse<PageResponse<BlogResponse>> myBlogs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.byCurrentWriter(new PageQuery(page, size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<BlogResponse> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(service.detail(id));
    }

    @GetMapping("/list/{languageId}")
    public ApiResponse<PageResponse<BlogResponse>> byLanguage(
            @PathVariable @Positive int languageId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.byLanguage(languageId, new PageQuery(page, size))));
    }

    @GetMapping("/all")
    public ApiResponse<PageResponse<BlogResponse>> all(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.all(new PageQuery(page, size))));
    }

    @PutMapping("/draft")
    public ApiResponse<Boolean> saveDraft(@Valid @RequestBody BlogDraftRequest request) {
        return ApiResponse.success(service.saveDraft(request));
    }

    @GetMapping("/draft")
    public ApiResponse<String> draft() {
        return ApiResponse.success(service.draft());
    }

    @DeleteMapping("/draft")
    public ApiResponse<Boolean> deleteDraft() {
        return ApiResponse.success(service.deleteDraft());
    }

    @GetMapping("/search/{info}")
    public ApiResponse<PageResponse<BlogResponse>> search(
            @PathVariable @Size(min = 1, max = 75) String info,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.search(info, new PageQuery(page, size))));
    }

    @PutMapping("/click/{id}")
    public ApiResponse<Boolean> click(@PathVariable @Positive long id) {
        return ApiResponse.success(service.click(id));
    }
}
