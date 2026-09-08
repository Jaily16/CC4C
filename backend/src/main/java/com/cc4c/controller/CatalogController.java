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

/**
 * CatalogController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Validated
@RestController
@RequestMapping("/courses")
public class CatalogController {
    private final CatalogService service;

    /**
     * 创建 CatalogController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param service 由容器注入的 CatalogService 协作组件
     */
    CatalogController(CatalogService service) {
        this.service = service;
    }

    /**
     * 执行 CatalogController 中的 home 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/home")
    public ApiResponse<PageResponse<CourseResponse>> home(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.home(new PageQuery(page, size))));
    }

    /**
     * 执行 CatalogController 中的 recommend 职责，并保持既有权限、事务与副作用边界。
     *
     * @param language 调用方提供的 {@code language} 值
     * @param major 调用方提供的 {@code major} 值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 执行 CatalogController 中的 byName 职责，并保持既有权限、事务与副作用边界。
     *
     * @param name 调用方提供的 {@code name} 值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/{name}")
    public ApiResponse<CourseResponse> byName(@PathVariable @Size(min = 1, max = 200) String name) {
        return ApiResponse.success(BusinessCode.COURSE_GET_ONE_SUCCESS.code(), service.byName(name), null);
    }

    /**
     * 执行 CatalogController 中的 search 职责，并保持既有权限、事务与副作用边界。
     *
     * @param info 调用方提供的 {@code info} 值
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 执行 CatalogController 中的 byLanguage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 变更 CatalogController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/module")
    public ResponseEntity<ApiResponse<CourseModuleResponse>> createModule(
            @Valid @RequestBody CourseModuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_MODULE_SUCCESS.code(), service.createModule(request), "课程模块添加成功"));
    }

    /**
     * 执行 CatalogController 中的 modules 职责，并保持既有权限、事务与副作用边界。
     *
     * @param id 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/module/{id}")
    public ApiResponse<List<CourseModuleResponse>> modules(@PathVariable @Positive int id) {
        return ApiResponse.success(BusinessCode.COURSE_GET_MODULES_SUCCESS.code(), service.modules(id), null);
    }

    /**
     * 变更 CatalogController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_SUCCESS.code(), service.createCourse(request), "课程添加成功"));
    }
}
