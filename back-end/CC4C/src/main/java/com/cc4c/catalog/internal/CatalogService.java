package com.cc4c.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.catalog.CatalogDtos.CourseCreateRequest;
import com.cc4c.catalog.CatalogDtos.CourseModuleCreateRequest;
import com.cc4c.catalog.CatalogDtos.CourseModuleResponse;
import com.cc4c.catalog.CatalogDtos.CourseResponse;
import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService implements CatalogLookup {
    private final CatalogMapper mapper;

    CatalogService(CatalogMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<CourseResponse> home(PageQuery query) {
        return toPage(mapper.selectHome(new Page<>(query.page(), query.size())));
    }

    public PageResult<CourseResponse> search(String searchText, PageQuery query) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
                .and(condition -> condition.like(CourseEntity::getCourseName, searchText)
                        .or()
                        .eq(CourseEntity::getLanguageName, searchText))
                .orderByAsc(CourseEntity::getCourseId);
        return toPage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper));
    }

    public PageResult<CourseResponse> byLanguage(String languageName, PageQuery query) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
                .eq(CourseEntity::getLanguageName, languageName)
                .orderByAsc(CourseEntity::getCourseId);
        return toPage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper));
    }

    public CourseResponse byName(String courseName) {
        CourseEntity course = mapper.selectOne(
                new LambdaQueryWrapper<CourseEntity>().eq(CourseEntity::getCourseName, courseName));
        if (course == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.COURSE_GET_ONE_FAILED,
                    "Course does not exist");
        }
        return toResponse(course);
    }

    public List<CourseModuleResponse> modules(int languageId) {
        return mapper.selectModules(languageId).stream()
                .map(module -> new CourseModuleResponse(
                        module.getLanguageId(),
                        module.getPriority(),
                        module.getModuleName(),
                        module.getLevel(),
                        mapper.selectCourseNames(module.getLanguageId(), module.getPriority())))
                .toList();
    }

    public List<CourseModuleResponse> recommend(int languageId, int major) {
        int moduleMinimum = major == -1 ? -1 : 0;
        int moduleMaximum = major == 1 ? 1 : 0;
        int courseMinimum = major == -1 ? -2 : major == 1 ? 1 : -1;
        int courseMaximum = major == -1 ? -1 : major == 1 ? 2 : 1;
        return mapper.selectModulesForRecommendation(languageId, moduleMinimum, moduleMaximum).stream()
                .map(module -> new CourseModuleResponse(
                        module.getLanguageId(),
                        module.getPriority(),
                        module.getModuleName(),
                        module.getLevel(),
                        mapper.selectRecommendedCourseNames(
                                module.getLanguageId(), module.getPriority(), courseMinimum, courseMaximum)))
                .toList();
    }

    @Transactional
    public CourseModuleResponse createModule(CourseModuleCreateRequest request) {
        if (!languageExists(request.languageId())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY,
                    "Programming language does not exist");
        }
        if (mapper.moduleExists(request.languageId(), request.priority())) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.MODULE_PRIORITY_REPEATED,
                    "模块优先级重复");
        }
        mapper.insertModule(request.languageId(), request.priority(), request.moduleName(), request.level());
        return new CourseModuleResponse(
                request.languageId(), request.priority(), request.moduleName(), request.level(), List.of());
    }

    @Transactional
    public CourseResponse createCourse(CourseCreateRequest request) {
        if (mapper.exists(new LambdaQueryWrapper<CourseEntity>()
                .eq(CourseEntity::getCourseName, request.courseName()))) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.COURSE_NAME_REPEATED, "课程名重复");
        }
        String languageName = mapper.findLanguageName(request.languageId());
        if (languageName == null || !mapper.moduleExists(request.languageId(), request.priority())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.COURSE_ADD_MODULE_COURSE_FAILED,
                    "Course module does not exist");
        }

        CourseEntity course = new CourseEntity();
        course.setCourseName(request.courseName());
        course.setLanguageName(languageName);
        course.setDescription(request.description());
        course.setLevel(request.level());
        course.setState(request.state());
        mapper.insert(course);
        mapper.insertModuleCourse(request.languageId(), request.priority(), course.getCourseId());
        return toResponse(course);
    }

    @Override
    public boolean languageExists(int languageId) {
        return mapper.findLanguageName(languageId) != null;
    }

    @Override
    public boolean courseExists(int courseId) {
        return mapper.selectById(courseId) != null;
    }

    private PageResult<CourseResponse> toPage(IPage<CourseEntity> page) {
        return new PageResult<>(
                page.getRecords().stream().map(this::toResponse).toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    private CourseResponse toResponse(CourseEntity course) {
        return new CourseResponse(
                course.getCourseId(),
                course.getCourseName(),
                course.getLanguageName(),
                course.getDescription(),
                course.getLevel(),
                course.getState(),
                course.getFavorsNum());
    }
}
