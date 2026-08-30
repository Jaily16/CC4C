package com.cc4c.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.catalog.CatalogDtos.CourseCreateRequest;
import com.cc4c.catalog.CatalogDtos.CourseModuleCreateRequest;
import com.cc4c.catalog.CatalogDtos.CourseModuleResponse;
import com.cc4c.catalog.CatalogDtos.CourseResponse;
import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.shared.BusinessCache;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** CatalogService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class CatalogService implements CatalogLookup {
    private static final String HOME_REGION = "catalog:home";
    private static final String LANGUAGE_REGION = "catalog:language";
    private static final String DETAIL_REGION = "catalog:detail";
    private static final String MODULES_REGION = "catalog:modules";
    private static final String RECOMMEND_REGION = "catalog:recommend";
    private static final Duration HOME_TTL = Duration.ofSeconds(60);
    private static final Duration DETAIL_TTL = Duration.ofMinutes(5);
    private static final Duration STRUCTURE_TTL = Duration.ofMinutes(10);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);
    private static final TypeReference<PageResult<CourseResponse>> COURSE_PAGE_TYPE = new TypeReference<>() {};
    private static final TypeReference<CourseResponse> COURSE_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<CourseModuleResponse>> MODULE_LIST_TYPE = new TypeReference<>() {};

    private final CatalogMapper mapper;
    private final BusinessCache cache;

    CatalogService(CatalogMapper mapper, BusinessCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    public PageResult<CourseResponse> home(PageQuery query) {
        return cachedPage(
                HOME_REGION,
                pageKey(query),
                HOME_TTL,
                () -> toPage(mapper.selectHome(new Page<>(query.page(), query.size()))));
    }

    public PageResult<CourseResponse> search(String searchText, PageQuery query) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
                .and(condition -> condition
                        .like(CourseEntity::getCourseName, searchText)
                        .or()
                        .eq(CourseEntity::getLanguageName, searchText))
                .orderByAsc(CourseEntity::getCourseId);
        return toPage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper));
    }

    public PageResult<CourseResponse> byLanguage(String languageName, PageQuery query) {
        return cachedPage(LANGUAGE_REGION, languageName + ":" + pageKey(query), DETAIL_TTL, () -> {
            LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
                    .eq(CourseEntity::getLanguageName, languageName)
                    .orderByAsc(CourseEntity::getCourseId);
            return toPage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper));
        });
    }

    public CourseResponse byName(String courseName) {
        return cache.getOrLoad(
                        DETAIL_REGION, courseName, COURSE_TYPE, DETAIL_TTL, NEGATIVE_TTL, () -> Optional.ofNullable(
                                        mapper.selectOne(new LambdaQueryWrapper<CourseEntity>()
                                                .eq(CourseEntity::getCourseName, courseName)))
                                .map(this::toResponse))
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, BusinessCode.COURSE_GET_ONE_FAILED, "Course does not exist"));
    }

    public List<CourseModuleResponse> modules(int languageId) {
        return cache.getOrLoad(
                        MODULES_REGION,
                        Integer.toString(languageId),
                        MODULE_LIST_TYPE,
                        STRUCTURE_TTL,
                        NEGATIVE_TTL,
                        () -> Optional.of(loadModules(languageId)))
                .orElseThrow();
    }

    public List<CourseModuleResponse> recommend(int languageId, int major) {
        int moduleMinimum = major == -1 ? -1 : 0;
        int moduleMaximum = major == 1 ? 1 : 0;
        int courseMinimum = major == -1 ? -2 : major == 1 ? 1 : -1;
        int courseMaximum = major == -1 ? -1 : major == 1 ? 2 : 1;
        String key = languageId + ":" + major;
        return cache.getOrLoad(
                        RECOMMEND_REGION,
                        key,
                        MODULE_LIST_TYPE,
                        STRUCTURE_TTL,
                        NEGATIVE_TTL,
                        () -> Optional.of(loadRecommendation(
                                languageId, moduleMinimum, moduleMaximum, courseMinimum, courseMaximum)))
                .orElseThrow();
    }

    @Transactional
    public CourseModuleResponse createModule(CourseModuleCreateRequest request) {
        if (!languageExists(request.languageId())) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.UNPROCESSABLE_ENTITY,
                    "Programming language does not exist");
        }
        if (mapper.moduleExists(request.languageId(), request.priority())) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.MODULE_PRIORITY_REPEATED, "模块优先级重复");
        }
        mapper.insertModule(request.languageId(), request.priority(), request.moduleName(), request.level());
        cache.invalidateAfterCommit(MODULES_REGION, RECOMMEND_REGION);
        return new CourseModuleResponse(
                request.languageId(), request.priority(), request.moduleName(), request.level(), List.of());
    }

    @Transactional
    public CourseResponse createCourse(CourseCreateRequest request) {
        if (mapper.exists(
                new LambdaQueryWrapper<CourseEntity>().eq(CourseEntity::getCourseName, request.courseName()))) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.COURSE_NAME_REPEATED, "课程名重复");
        }
        String languageName = mapper.findLanguageName(request.languageId());
        if (languageName == null || !mapper.moduleExists(request.languageId(), request.priority())) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
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
        cache.invalidateAfterCommit(HOME_REGION, LANGUAGE_REGION, DETAIL_REGION, MODULES_REGION, RECOMMEND_REGION);
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

    @Override
    public void invalidateCoursePopularity() {
        cache.invalidateAfterCommit(HOME_REGION);
    }

    private List<CourseModuleResponse> loadModules(int languageId) {
        Map<Integer, List<String>> courseNames = groupCourseNames(mapper.selectCourseNamesByLanguage(languageId));
        return mapper.selectModules(languageId).stream()
                .map(module -> moduleResponse(module, courseNames))
                .toList();
    }

    private List<CourseModuleResponse> loadRecommendation(
            int languageId, int moduleMinimum, int moduleMaximum, int courseMinimum, int courseMaximum) {
        Map<Integer, List<String>> courseNames = groupCourseNames(
                mapper.selectRecommendedCourseNamesByLanguage(languageId, courseMinimum, courseMaximum));
        return mapper.selectModulesForRecommendation(languageId, moduleMinimum, moduleMaximum).stream()
                .map(module -> moduleResponse(module, courseNames))
                .toList();
    }

    private Map<Integer, List<String>> groupCourseNames(List<ModuleCourseNameRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        ModuleCourseNameRow::getPriority,
                        LinkedHashMap::new,
                        Collectors.mapping(ModuleCourseNameRow::getCourseName, Collectors.toList())));
    }

    private CourseModuleResponse moduleResponse(CourseModuleRow module, Map<Integer, List<String>> courseNames) {
        return new CourseModuleResponse(
                module.getLanguageId(),
                module.getPriority(),
                module.getModuleName(),
                module.getLevel(),
                courseNames.getOrDefault(module.getPriority(), List.of()));
    }

    private PageResult<CourseResponse> cachedPage(
            String region, String key, Duration ttl, Supplier<PageResult<CourseResponse>> loader) {
        return cache.getOrLoad(region, key, COURSE_PAGE_TYPE, ttl, NEGATIVE_TTL, () -> Optional.of(loader.get()))
                .orElseThrow();
    }

    private String pageKey(PageQuery query) {
        return query.page() + ":" + query.size();
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
