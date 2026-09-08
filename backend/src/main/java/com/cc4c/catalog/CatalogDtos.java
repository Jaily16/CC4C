package com.cc4c.catalog;

import com.cc4c.shared.IntValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 集中声明课程目录接口请求与响应的数据结构，不承载业务流程。
 */
public final class CatalogDtos {
    /**
     * 创建 CatalogDtos 实例，不触发外部 I/O。
     */
    private CatalogDtos() {}

    /**
     * 承载课程目录接口的输入字段与声明式校验约束。
     *
     * @param courseName 调用方提供的 {@code courseName} 值
     * @param description 面向用户展示的说明文本
     * @param level 调用方提供的 {@code level} 值
     * @param state 调用方提供的 {@code state} 值
     * @param languageId 目标对象的稳定标识
     * @param priority 调用方提供的 {@code priority} 值
     */
    public record CourseCreateRequest(
            @NotBlank @Size(max = 200) String courseName,
            @NotBlank String description,
            @NotNull @IntValues({-2, -1, 0, 1, 2, 66}) Integer level,
            @NotNull @IntValues({0, 1}) Integer state,
            @NotNull @Positive Integer languageId,
            @NotNull @Positive Integer priority) {}

    /**
     * 承载课程目录接口的输入字段与声明式校验约束。
     *
     * @param languageId 目标对象的稳定标识
     * @param priority 调用方提供的 {@code priority} 值
     * @param moduleName 调用方提供的 {@code moduleName} 值
     * @param level 调用方提供的 {@code level} 值
     */
    public record CourseModuleCreateRequest(
            @NotNull @Positive Integer languageId,
            @NotNull @Positive Integer priority,
            @NotBlank @Size(max = 50) String moduleName,
            @NotNull @IntValues({-1, 0, 1}) Integer level) {}

    /**
     * 承载课程目录接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param courseId 目标对象的稳定标识
     * @param courseName 调用方提供的 {@code courseName} 值
     * @param languageName 调用方提供的 {@code languageName} 值
     * @param description 面向用户展示的说明文本
     * @param level 调用方提供的 {@code level} 值
     * @param state 调用方提供的 {@code state} 值
     * @param favorsNum 调用方提供的 {@code favorsNum} 值
     */
    public record CourseResponse(
            Integer courseId,
            String courseName,
            String languageName,
            String description,
            Integer level,
            Integer state,
            Integer favorsNum) {}

    /**
     * 承载课程目录接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param languageId 目标对象的稳定标识
     * @param priority 调用方提供的 {@code priority} 值
     * @param moduleName 调用方提供的 {@code moduleName} 值
     * @param level 调用方提供的 {@code level} 值
     * @param courseList 调用方提供的 {@code courseList} 值
     */
    public record CourseModuleResponse(
            Integer languageId, Integer priority, String moduleName, Integer level, List<String> courseList) {}
}
