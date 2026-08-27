package com.cc4c.catalog;

import com.cc4c.shared.IntValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record CourseCreateRequest(
            @NotBlank @Size(max = 200) String courseName,
            @NotBlank String description,
            @NotNull @IntValues({-2, -1, 0, 1, 2, 66}) Integer level,
            @NotNull @IntValues({0, 1}) Integer state,
            @NotNull @Positive Integer languageId,
            @NotNull @Positive Integer priority
    ) {
    }

    public record CourseModuleCreateRequest(
            @NotNull @Positive Integer languageId,
            @NotNull @Positive Integer priority,
            @NotBlank @Size(max = 50) String moduleName,
            @NotNull @IntValues({-1, 0, 1}) Integer level
    ) {
    }

    public record CourseResponse(
            Integer courseId,
            String courseName,
            String languageName,
            String description,
            Integer level,
            Integer state,
            Integer favorsNum
    ) {
    }

    public record CourseModuleResponse(
            Integer languageId,
            Integer priority,
            String moduleName,
            Integer level,
            List<String> courseList
    ) {
    }
}
