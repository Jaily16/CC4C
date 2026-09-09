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

/** 维护课程、模块和推荐查询，使用分区缓存并在课程写入提交后失效相关分区。 */
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
     * 接入课程 Mapper 和可旁路的业务缓存。
     *
     * @param mapper 课程、模块和语言数据访问 Mapper
     * @param cache 支持分区及提交后失效的业务缓存
     */
    CatalogService(CatalogMapper mapper, BusinessCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    /**
     * 缓存按收藏热度排列的课程分页，基础有效期为 60 秒。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 带收藏数的首页课程页
     */
    public PageResult<CourseResponse> home(PageQuery query) {
        return cachedPage(
                HOME_REGION,
                pageKey(query),
                HOME_TTL,
                () -> toPage(mapper.selectHome(new Page<>(query.page(), query.size()))));
    }

    /**
     * 直接查询课程名模糊匹配或语言名精确匹配的课程，不缓存任意检索词。
     *
     * @param searchText 课程名检索词或完整语言名
     * @param query 从 1 起算的页码及页大小
     * @return 按课程 ID 升序排列的课程页
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
     * 按语言名和分页条件缓存课程查询，基础有效期为五分钟。
     *
     * @param languageName 语言名称
     * @param query 从 1 起算的页码及页大小
     * @return 该语言的课程页
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
     * 缓存课程名详情查询，空结果使用短期负缓存；不存在时抛出 404。
     *
     * @param courseName 课程名称
     * @return 课程详情
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
     * 缓存指定语言的模块及课程列表，基础有效期为十分钟。
     *
     * @param languageId 语言 ID
     * @return 按模块序号排列的模块列表
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
     * 依据专业分类计算模块和课程级别范围，并按语言与专业组合缓存推荐列表。
     *
     * @param languageId 语言 ID
     * @param major 专业分类（-1、0、1）
     * @return 符合级别规则的课程模块列表
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
     * 事务内校验语言存在和模块序号唯一后创建模块，提交后失效模块与推荐缓存。
     *
     * @param request 语言、序号、名称和级别的新模块请求
     * @return 尚无课程的新模块
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
     * 事务内校验课程名、语言及模块，插入课程和模块关联后安排五个课程缓存分区失效。
     *
     * @param request 课程字段及所属语言模块请求
     * @return 新建课程响应
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
     * 以未删除语言名称查询判断语言存在性。
     *
     * @param languageId 语言 ID
     * @return 存在语言名称时为 true
     */
    @Override
    public boolean languageExists(int languageId) {
        return mapper.findLanguageName(languageId) != null;
    }

    /**
     * 按主键读取未删除课程以判断存在性。
     *
     * @param courseId 课程 ID
     * @return 课程存在时为 true
     */
    @Override
    public boolean courseExists(int courseId) {
        return mapper.selectById(courseId) != null;
    }

    /** 安排事务提交后失效课程首页分区，使收藏热度重新聚合。 */
    @Override
    public void invalidateCoursePopularity() {
        cache.invalidateAfterCommit(HOME_REGION);
    }

    /**
     * 批量查询语言下的课程名称并按模块序号组装模块列表。
     *
     * @param languageId 语言 ID
     * @return 带课程名称列表的模块响应
     */
    private List<CourseModuleResponse> loadModules(int languageId) {
        Map<Integer, List<String>> courseNames = groupCourseNames(mapper.selectCourseNamesByLanguage(languageId));
        return mapper.selectModules(languageId).stream()
                .map(module -> moduleResponse(module, courseNames))
                .toList();
    }

    /**
     * 分别按模块级别与课程级别批量查询，再按模块序号组合推荐结果。
     *
     * @param languageId 语言 ID
     * @param moduleMinimum 模块级别下限，包含该值
     * @param moduleMaximum 模块级别上限，包含该值
     * @param courseMinimum 课程级别下限，包含该值
     * @param courseMaximum 课程级别上限，包含该值
     * @return 符合两套级别范围的模块响应
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
     * 按模块序号归组课程名称，保留查询中模块首次出现的顺序。
     *
     * @param rows 按模块序号排列的课程名称投影
     * @return 模块序号到课程名称列表的映射
     */
    private Map<Integer, List<String>> groupCourseNames(List<ModuleCourseNameRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        ModuleCourseNameRow::getPriority,
                        LinkedHashMap::new,
                        Collectors.mapping(ModuleCourseNameRow::getCourseName, Collectors.toList())));
    }

    /**
     * 合并模块字段与对应课程名称；无关联课程时使用空列表。
     *
     * @param module 模块字段投影
     * @param courseNames 按模块序号归组的课程名称
     * @return 单个模块响应
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
     * 使用显式分页类型从缓存读取，未命中时调用加载器并缓存分页结果。
     *
     * @param region 缓存分区名称
     * @param key 分区内的逻辑键
     * @param ttl 正常结果的基础缓存有效期
     * @param loader 缓存未命中时读取数据库的加载器
     * @return 缓存或加载器返回的课程页
     */
    private PageResult<CourseResponse> cachedPage(
            String region, String key, Duration ttl, Supplier<PageResult<CourseResponse>> loader) {
        return cache.getOrLoad(region, key, COURSE_PAGE_TYPE, ttl, NEGATIVE_TTL, () -> Optional.of(loader.get()))
                .orElseThrow();
    }

    /**
     * 组合页码和页大小作为缓存逻辑键。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 以冒号分隔的分页键
     */
    private String pageKey(PageQuery query) {
        return query.page() + ":" + query.size();
    }

    /**
     * 将 MyBatis 分页记录转为课程响应并保留页码、页大小和总数。
     *
     * @param page MyBatis 分页结果，携带记录及分页元数据
     * @return 服务层课程分页结果
     */
    private PageResult<CourseResponse> toPage(IPage<CourseEntity> page) {
        return new PageResult<>(
                page.getRecords().stream().map(this::toResponse).toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    /**
     * 投影课程字段及查询附带的收藏数。
     *
     * @param course 课程实体及查询附带字段
     * @return 课程展示响应
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
