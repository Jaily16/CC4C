package com.cc4c.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.dto.CatalogDtos.CourseCreateRequest;
import com.cc4c.dto.CatalogDtos.CourseModuleCreateRequest;
import com.cc4c.dto.CatalogDtos.CourseModuleResponse;
import com.cc4c.dto.CatalogDtos.CourseResponse;
import com.cc4c.dto.CourseModuleRow;
import com.cc4c.dto.ModuleCourseNameRow;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResult;
import com.cc4c.entity.CourseEntity;
import com.cc4c.mapper.CatalogMapper;
import com.cc4c.support.cache.BusinessCache;
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

/**
 * CatalogService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Service
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

    /**
     * 创建 CatalogService 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param mapper 调用方提供的 {@code mapper} 值
     * @param cache 调用方提供的 {@code cache} 值
     */
    CatalogService(CatalogMapper mapper, BusinessCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    /**
     * 执行 CatalogService 中的 home 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<CourseResponse> home(PageQuery query) {
        return cachedPage(
                HOME_REGION,
                pageKey(query),
                HOME_TTL,
                () -> toPage(mapper.selectHome(new Page<>(query.page(), query.size()))));
    }

    /**
     * 执行 CatalogService 中的 search 职责，并保持既有权限、事务与副作用边界。
     *
     * @param searchText 调用方提供的 {@code searchText} 值
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<CourseResponse> search(String searchText, PageQuery query) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
                .and(condition -> condition
                        .like(CourseEntity::getCourseName, searchText)
                        .or()
                        .eq(CourseEntity::getLanguageName, searchText))
                .orderByAsc(CourseEntity::getCourseId);
        return toPage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper));
    }

    /**
     * 执行 CatalogService 中的 byLanguage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageName 调用方提供的 {@code languageName} 值
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<CourseResponse> byLanguage(String languageName, PageQuery query) {
        return cachedPage(LANGUAGE_REGION, languageName + ":" + pageKey(query), DETAIL_TTL, () -> {
            LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
                    .eq(CourseEntity::getLanguageName, languageName)
                    .orderByAsc(CourseEntity::getCourseId);
            return toPage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper));
        });
    }

    /**
     * 执行 CatalogService 中的 byName 职责，并保持既有权限、事务与副作用边界。
     *
     * @param courseName 调用方提供的 {@code courseName} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public CourseResponse byName(String courseName) {
        return cache.getOrLoad(
                        DETAIL_REGION, courseName, COURSE_TYPE, DETAIL_TTL, NEGATIVE_TTL, () -> Optional.ofNullable(
                                        mapper.selectOne(new LambdaQueryWrapper<CourseEntity>()
                                                .eq(CourseEntity::getCourseName, courseName)))
                                .map(this::toResponse))
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, BusinessCode.COURSE_GET_ONE_FAILED, "Course does not exist"));
    }

    /**
     * 执行 CatalogService 中的 modules 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageId 目标对象的稳定标识
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
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

    /**
     * 执行 CatalogService 中的 recommend 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageId 目标对象的稳定标识
     * @param major 调用方提供的 {@code major} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
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

    /**
     * 变更 CatalogService 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 按当前声明计算、查询或转换得到的结果
     */
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

    /**
     * 变更 CatalogService 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 按当前声明计算、查询或转换得到的结果
     */
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

    /**
     * 执行 CatalogService 中的 languageExists 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Override
    public boolean languageExists(int languageId) {
        return mapper.findLanguageName(languageId) != null;
    }

    /**
     * 执行 CatalogService 中的 courseExists 职责，并保持既有权限、事务与副作用边界。
     *
     * @param courseId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Override
    public boolean courseExists(int courseId) {
        return mapper.selectById(courseId) != null;
    }

    /**
     * 执行 CatalogService 中的 invalidateCoursePopularity 职责，并保持既有权限、事务与副作用边界。
     */
    @Override
    public void invalidateCoursePopularity() {
        cache.invalidateAfterCommit(HOME_REGION);
    }

    /**
     * 执行 CatalogService 中的 loadModules 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageId 目标对象的稳定标识
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    private List<CourseModuleResponse> loadModules(int languageId) {
        Map<Integer, List<String>> courseNames = groupCourseNames(mapper.selectCourseNamesByLanguage(languageId));
        return mapper.selectModules(languageId).stream()
                .map(module -> moduleResponse(module, courseNames))
                .toList();
    }

    /**
     * 执行 CatalogService 中的 loadRecommendation 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageId 目标对象的稳定标识
     * @param moduleMinimum 调用方提供的 {@code moduleMinimum} 值
     * @param moduleMaximum 调用方提供的 {@code moduleMaximum} 值
     * @param courseMinimum 调用方提供的 {@code courseMinimum} 值
     * @param courseMaximum 调用方提供的 {@code courseMaximum} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    private List<CourseModuleResponse> loadRecommendation(
            int languageId, int moduleMinimum, int moduleMaximum, int courseMinimum, int courseMaximum) {
        Map<Integer, List<String>> courseNames = groupCourseNames(
                mapper.selectRecommendedCourseNamesByLanguage(languageId, courseMinimum, courseMaximum));
        return mapper.selectModulesForRecommendation(languageId, moduleMinimum, moduleMaximum).stream()
                .map(module -> moduleResponse(module, courseNames))
                .toList();
    }

    /**
     * 执行 CatalogService 中的 groupCourseNames 职责，并保持既有权限、事务与副作用边界。
     *
     * @param rows 调用方提供的 {@code rows} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private Map<Integer, List<String>> groupCourseNames(List<ModuleCourseNameRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        ModuleCourseNameRow::getPriority,
                        LinkedHashMap::new,
                        Collectors.mapping(ModuleCourseNameRow::getCourseName, Collectors.toList())));
    }

    /**
     * 执行 CatalogService 中的 moduleResponse 职责，并保持既有权限、事务与副作用边界。
     *
     * @param module 调用方提供的 {@code module} 值
     * @param courseNames 调用方提供的 {@code courseNames} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private CourseModuleResponse moduleResponse(CourseModuleRow module, Map<Integer, List<String>> courseNames) {
        return new CourseModuleResponse(
                module.getLanguageId(),
                module.getPriority(),
                module.getModuleName(),
                module.getLevel(),
                courseNames.getOrDefault(module.getPriority(), List.of()));
    }

    /**
     * 执行 CatalogService 中的 cachedPage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param region 调用方提供的 {@code region} 值
     * @param key 调用方提供的 {@code key} 值
     * @param ttl 调用方提供的 {@code ttl} 值
     * @param loader 调用方提供的 {@code loader} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    private PageResult<CourseResponse> cachedPage(
            String region, String key, Duration ttl, Supplier<PageResult<CourseResponse>> loader) {
        return cache.getOrLoad(region, key, COURSE_PAGE_TYPE, ttl, NEGATIVE_TTL, () -> Optional.of(loader.get()))
                .orElseThrow();
    }

    /**
     * 执行 CatalogService 中的 pageKey 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String pageKey(PageQuery query) {
        return query.page() + ":" + query.size();
    }

    /**
     * 执行 CatalogService 中的 toPage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    private PageResult<CourseResponse> toPage(IPage<CourseEntity> page) {
        return new PageResult<>(
                page.getRecords().stream().map(this::toResponse).toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    /**
     * 执行 CatalogService 中的 toResponse 职责，并保持既有权限、事务与副作用边界。
     *
     * @param course 调用方提供的 {@code course} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
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
