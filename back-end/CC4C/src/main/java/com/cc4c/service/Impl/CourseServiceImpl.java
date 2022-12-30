package com.cc4c.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.dao.CourseDao;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ModuleCourse;
import com.cc4c.service.CourseService;
import com.cc4c.utility.CourseLevel;
import com.cc4c.utility.ModuleLevel;
import com.cc4c.utility.UserMajor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl implements CourseService {
    @Autowired
    private CourseDao courseDao;

    //纯course部分相关服务
    @Override
    public Result addCourse(Course course) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Course::getCourseName, course.getCourseName());
        if(courseDao.exists(lambdaQueryWrapper)){
            return new Result(Code.COURSE_NAME_REPEATED.getCode(), false,"课程名重复");
        }
        if(courseDao.insert(course) <= 0){
            return new Result(Code.COURSE_ADD_FAILED.getCode(), false, "课程添加失败");
        }
        ModuleCourse moduleCourse = new ModuleCourse();
        moduleCourse.setCourseId(course.getCourseId());
        moduleCourse.setPriority(course.getPriority());
        moduleCourse.setLanguageId(course.getLanguageId());
        if(!addCourseIntoModule(moduleCourse)){
            return new Result(Code.COURSE_ADD_MODULE_COURSE_FAILED.getCode(), false, "将课程添加进入模块失败");
        }
        return new Result(Code.COURSE_ADD_SUCCESS.getCode(), true, "课程添加成功");
    }

    @Override
    public Code addCourseModule(CourseModule courseModule) {
        if(courseDao.countCourseModule(courseModule)){
            return Code.MODULE_PRIORITY_REPEATED;
        }
        return (courseDao.addCourseModule(courseModule) > 0) ? Code.COURSE_ADD_MODULE_SUCCESS : Code.COURSE_ADD_MODULE_FAILED;
    }

    @Override
    public Boolean addCourseIntoModule(ModuleCourse moduleCourse) {
        return courseDao.addModuleCourse(moduleCourse) > 0;
    }

    @Override
    public Boolean deleteCourseByName(String courseName) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Course::getCourseName, courseName);
        return courseDao.delete(lambdaQueryWrapper) > 0;
    }

    @Override
    public Boolean deleteCourseModule(CourseModule courseModule) {
        courseDao.deleteModuleCourseByModule(courseModule.getLanguageId(), courseModule.getPriority());
        return courseDao.deleteCourseModule(courseModule) > 0 ;
    }

    @Override
    public Boolean deleteCourseFormModule(ModuleCourse moduleCourse) {
        return courseDao.deleteModuleCourse(moduleCourse) > 0;
    }

    @Override
    public Code updateCourse(Course course) {
        String oldName = courseDao.getCourseNameById(course.getCourseId());
        if(!course.getCourseName().equals(oldName)){
            //若修改了课程名则检测课程名是否重复
            LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(Course::getCourseName, course.getCourseName());
            if(courseDao.exists(lambdaQueryWrapper)){
                return Code.COURSE_NAME_REPEATED;
            }
        }
        //修改课程信息
        if(courseDao.updateById(course) <= 0){
            return Code.COURSE_UPDATE_FAILED;
        }
        return Code.COURSE_UPDATE_SUCCESS;
    }

    @Override
    public Result getCourseByName(String courseName) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Course::getCourseName, courseName);
        Course course = courseDao.selectOne(lambdaQueryWrapper);
        if(course == null){
            return new Result(Code.COURSE_GET_ONE_FAILED.getCode(), null, "course not exist");
        }
        return new Result(Code.COURSE_GET_ONE_SUCCESS.getCode(), course);
    }

    @Override
    public Result getCourseModule(Integer languageId) {
        List<CourseModule> modules = courseDao.getCourseModulesByLId(languageId);
        if(modules == null || modules.isEmpty()){
            return new Result(Code.COURSE_GET_MODULES_FAILED.getCode(), null, "get modules failed");
        }
        for(CourseModule module : modules){
            List<String> courses = courseDao.getCoursesByModule(module);
            module.setCourseList(courses);
        }
        return new Result(Code.COURSE_GET_MODULES_SUCCESS.getCode(), modules);
    }

    @Override
    public Result getCourseModuleByCourseName(String courseName) {
        CourseModule module = courseDao.getCourseModuleByCName(courseName);
        if(module == null){
            return new Result(Code.COURSE_GET_MODULE_BY_CNAME_FAILED.getCode(), null, "course not belonging to a module");
        }
        return new Result(Code.COURSE_GET_MODULE_BY_CNAME_SUCCESS.getCode(), module);
    }


    @Override
    public Result searchCourse(String searchInfo) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(Course::getCourseName, searchInfo).or().eq(Course::getLanguageName, searchInfo)
                          .select(Course.class,info->!info.getColumn().equals("description"));
        List<Course> courses = courseDao.selectList(lambdaQueryWrapper);
        if(courses == null || courses.isEmpty()){
            return new Result(Code.COURSE_SEARCH_NO_RESULT.getCode(), null, "no such course");
        }
        return new Result(Code.COURSE_SEARCH_SUCCESS.getCode(), courses);
    }

    //与user关联部分相关服务
    @Override
    public Result recommendCourseToUser(Integer languageId, Integer major) {
        Integer smallLimit1 = ModuleLevel.DEFAULT.getLevel();
        Integer smallLimit2 = CourseLevel.EASY_AND_DEFAULT.getLevel();
        Integer bigLimit1 = ModuleLevel.DEFAULT.getLevel();
        Integer bigLimit2 = CourseLevel.DIFFICULT_AND_DEFAULT.getLevel();
        if(Objects.equals(major, UserMajor.MAJOR_NOT_IN_CS.getMajor())){
            smallLimit1 = ModuleLevel.EASY.getLevel();
            smallLimit2 = CourseLevel.EASY.getLevel();
            bigLimit2 = CourseLevel.EASY_AND_DEFAULT.getLevel();
        }
        if(Objects.equals(major, UserMajor.MAJOR_IN_CS.getMajor())) {
            bigLimit1 = ModuleLevel.DIFFICULT.getLevel();
            smallLimit2 = CourseLevel.DIFFICULT_AND_DEFAULT.getLevel();
            bigLimit2 = CourseLevel.DIFFICULT.getLevel();
        }
        List<CourseModule> modules = courseDao.getCourseModuleByLIdAndRange(languageId, smallLimit1, bigLimit1);
        for(CourseModule module : modules){
            List<String> courses = courseDao.getCoursesByModuleAndRange(module, smallLimit2, bigLimit2, CourseLevel.MUST_SHOW.getLevel());
            module.setCourseList(courses);
        }
        return new Result(Code.COURSE_GET_RECOMMENDATION_SUCCESS.getCode(), modules, "recommend finished");
    }

    @Override
    public Boolean favorCourse(Long userId, Integer courseId) {
        Date date = new Date();
        return courseDao.addFavorInfo(userId, courseId, date) > 0;
    }

    @Override
    public Boolean cancelFavor(Long userId, Integer courseId) {
        return courseDao.deleteFavorInfo(userId,courseId) > 0;
    }

    @Override
    public Boolean ifFavor(Long userId, Integer courseId) {
        return courseDao.ifFavor(userId, courseId);
    }

    @Override
    public List<Course> getFavorCourseList(Long userId) {
        return courseDao.getFavorCourses(userId);
    }

    @Override
    public Result getCoursesByLanguage(String languageName) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Course::getLanguageName, languageName)
                .select(Course.class,info->!info.getColumn().equals("description"));
        List<Course> courses = courseDao.selectList(lambdaQueryWrapper);
        if(courses == null || courses.isEmpty()){
            return new Result(Code.COURSE_SEARCH_NO_RESULT.getCode(), null, "no such course");
        }
        return new Result(Code.COURSE_SEARCH_SUCCESS.getCode(), courses);
    }

    @Override
    public Result getHomeCourses() {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(Course.class,info->!info.getColumn().equals("description"));
        List<Course> courses = courseDao.selectList(lambdaQueryWrapper);
        for(Course course : courses){
            Integer favors = courseDao.countFavor(course.getCourseId());
            course.setFavorsNum(favors);
        }
        //按照课程收藏量排序
        courses = courses.stream().sorted(Comparator.comparing(Course::getFavorsNum).reversed())
                .collect(Collectors.toList());
        return new Result(Code.SUCCESS.getCode(), courses);
    }

}
