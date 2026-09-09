package com.cc4c.dto;

import com.cc4c.common.IntValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 集中声明课程目录接口请求与响应的数据结构，不承载业务流程。
 */
public final class CatalogDtos {
    /** 仅作为嵌套 DTO 的命名容器，禁止外部实例化。 */
    private CatalogDtos() {}

    /**
     * 新增课程的请求字段；语言和模块序号必须为正数，级别及状态受枚举值校验。
     *
     * @param courseName 课程名称
     * @param description 课程介绍
     * @param level 课程级别（-2、-1、0、1、2、66）
     * @param state 课程状态（0、1）
     * @param languageId 所属语言 ID
     * @param priority 课程模块序号
     */
    public record CourseCreateRequest(
            @NotBlank @Size(max = 200) String courseName,
            @NotBlank String description,
            @NotNull @IntValues({-2, -1, 0, 1, 2, 66}) Integer level,
            @NotNull @IntValues({0, 1}) Integer state,
            @NotNull @Positive Integer languageId,
            @NotNull @Positive Integer priority) {}

    /**
     * 新增课程模块的请求字段；指定所属语言、模块序号、名称及级别。
     *
     * @param languageId 所属语言 ID
     * @param priority 课程模块序号
     * @param moduleName 模块名称
     * @param level 模块级别编码
     */
    public record CourseModuleCreateRequest(
            @NotNull @Positive Integer languageId,
            @NotNull @Positive Integer priority,
            @NotBlank @Size(max = 50) String moduleName,
            @NotNull @IntValues({-1, 0, 1}) Integer level) {}

    /**
     * 课程展示数据，包含关联语言名称和收藏数量。
     *
     * @param courseId 课程 ID
     * @param courseName 课程名称
     * @param languageName 语言名称
     * @param description 课程介绍
     * @param level 课程级别编码
     * @param state 课程状态编码
     * @param favorsNum 课程收藏数量
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
     * 按语言和模块序号返回课程模块及其中的课程名称。
     *
     * @param languageId 所属语言 ID
     * @param priority 课程模块序号
     * @param moduleName 模块名称
     * @param level 模块级别编码
     * @param courseList 该模块的课程名称列表
     */
    public record CourseModuleResponse(
            Integer languageId, Integer priority, String moduleName, Integer level, List<String> courseList) {}
}
