package com.cc4c.catalog;

import com.cc4c.catalog.CatalogDtos.CourseCreateRequest;
import com.cc4c.catalog.CatalogDtos.CourseModuleCreateRequest;
import com.cc4c.catalog.CatalogDtos.CourseModuleResponse;
import com.cc4c.catalog.CatalogDtos.CourseResponse;
import com.cc4c.catalog.internal.CatalogService;
import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.IntValues;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/courses")
public class CatalogController {
    private final CatalogService service;

    CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/home")
    public ApiResponse<PageResponse<CourseResponse>> home(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.home(new PageQuery(page, size))));
    }

    @GetMapping("/recommend/{language}/{major}")
    public ApiResponse<List<CourseModuleResponse>> recommend(
            @PathVariable @Positive int language,
            @PathVariable @IntValues({-1, 0, 1}) int major) {
        return ApiResponse.success(
                BusinessCode.COURSE_GET_RECOMMENDATION_SUCCESS.code(),
                service.recommend(language, major),
                "recommend finished");
    }

    @GetMapping("/{name}")
    public ApiResponse<CourseResponse> byName(@PathVariable @Size(min = 1, max = 200) String name) {
        return ApiResponse.success(
                BusinessCode.COURSE_GET_ONE_SUCCESS.code(), service.byName(name), null);
    }

    @GetMapping("/search/{info}")
    public ApiResponse<PageResponse<CourseResponse>> search(
            @PathVariable @Size(min = 1, max = 200) String info,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                BusinessCode.COURSE_SEARCH_SUCCESS.code(),
                PageResponse.from(service.search(info, new PageQuery(page, size))),
                null);
    }

    @GetMapping("/language/{name}")
    public ApiResponse<PageResponse<CourseResponse>> byLanguage(
            @PathVariable @Size(min = 1, max = 15) String name,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                BusinessCode.COURSE_SEARCH_SUCCESS.code(),
                PageResponse.from(service.byLanguage(name, new PageQuery(page, size))),
                null);
    }

    @PostMapping("/module")
    public ResponseEntity<ApiResponse<CourseModuleResponse>> createModule(
            @Valid @RequestBody CourseModuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_MODULE_SUCCESS.code(),
                        service.createModule(request),
                        "课程模块添加成功"));
    }

    @GetMapping("/module/{id}")
    public ApiResponse<List<CourseModuleResponse>> modules(@PathVariable @Positive int id) {
        return ApiResponse.success(
                BusinessCode.COURSE_GET_MODULES_SUCCESS.code(), service.modules(id), null);
    }

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_SUCCESS.code(),
                        service.createCourse(request),
                        "课程添加成功"));
    }
}
