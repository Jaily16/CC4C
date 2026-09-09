package com.cc4c.controller;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.IntValues;
import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.CatalogDtos.CourseCreateRequest;
import com.cc4c.dto.CatalogDtos.CourseModuleCreateRequest;
import com.cc4c.dto.CatalogDtos.CourseModuleResponse;
import com.cc4c.dto.CatalogDtos.CourseResponse;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResponse;
import com.cc4c.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
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

/** 提供公开课程读取和管理员课程创建入口，将分页及字段校验后的请求交给目录服务。 */
@Validated
@RestController
@RequestMapping("/courses")
public class CatalogController {
    private final CatalogService service;

    /**
     * 接入课程查询与写入服务。
     *
     * @param service 课程目录服务
     */
    CatalogController(CatalogService service) {
        this.service = service;
    }

    /**
     * 分页查询首页课程并封装分页元数据。
     *
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 首页课程分页结果
     */
    @GetMapping("/home")
    public ApiResponse<PageResponse<CourseResponse>> home(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.home(new PageQuery(page, size))));
    }

    /**
     * 按语言 ID 与专业分类查询推荐课程模块。
     *
     * @param language 正数语言 ID
     * @param major 专业分类（-1、0、1）
     * @return 带推荐业务码的模块列表
     */
    @GetMapping("/recommend/{language}/{major}")
    public ApiResponse<List<CourseModuleResponse>> recommend(
            @PathVariable @Positive int language, @PathVariable @IntValues({-1, 0, 1}) int major) {
        return ApiResponse.success(
                BusinessCode.COURSE_GET_RECOMMENDATION_SUCCESS.code(),
                service.recommend(language, major),
                "recommend finished");
    }

    /**
     * 按课程名称查询单门课程详情。
     *
     * @param name 课程名称，长度 1 至 200
     * @return 课程详情响应
     */
    @GetMapping("/{name}")
    public ApiResponse<CourseResponse> byName(@PathVariable @Size(min = 1, max = 200) String name) {
        return ApiResponse.success(BusinessCode.COURSE_GET_ONE_SUCCESS.code(), service.byName(name), null);
    }

    /**
     * 按检索词查询课程并返回分页结果。
     *
     * @param info 课程检索词，长度 1 至 200
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 匹配课程的分页响应
     */
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

    /**
     * 按语言名称分页查询课程。
     *
     * @param name 语言名称，长度 1 至 15
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 该语言的课程分页响应
     */
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

    /**
     * 将已校验的模块创建请求交给目录服务，成功使用 HTTP 201。
     *
     * @param request 已校验的新课程模块字段
     * @return 包含新模块的创建响应
     */
    @PostMapping("/module")
    public ResponseEntity<ApiResponse<CourseModuleResponse>> createModule(
            @Valid @RequestBody CourseModuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_MODULE_SUCCESS.code(), service.createModule(request), "课程模块添加成功"));
    }

    /**
     * 按语言 ID 查询课程模块列表。
     *
     * @param id 正数语言 ID
     * @return 该语言的模块列表
     */
    @GetMapping("/module/{id}")
    public ApiResponse<List<CourseModuleResponse>> modules(@PathVariable @Positive int id) {
        return ApiResponse.success(BusinessCode.COURSE_GET_MODULES_SUCCESS.code(), service.modules(id), null);
    }

    /**
     * 将已校验的课程创建请求交给目录服务，成功使用 HTTP 201。
     *
     * @param request 已校验的新课程字段
     * @return 包含新课程的创建响应
     */
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_SUCCESS.code(), service.createCourse(request), "课程添加成功"));
    }
}
