<template>
  <div class="course" style="background-color:white">
    <el-row justify="center" style="padding-top:20px; ">
      <el-col :span="4"
        style="height:1000px; background-color: white; padding-left: 30px; border-right: solid 1px rgb(220,223,230); box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);">
        <h3>课程目录</h3>
        <el-menu default-active="2"  @open="handleOpen" @close="handleClose" style="">
          <el-sub-menu index="1">
            <template #title>
              <span>科目一</span>
            </template>
            <el-menu-item index="1-1">课程一</el-menu-item>
            <el-menu-item index="1-2">课程二</el-menu-item>
            <el-menu-item index="1-3">课程三</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="2">
            <template #title>
              <span>科目二</span>
            </template>
            <el-menu-item index="2-1">课程一</el-menu-item>
            <el-menu-item index="2-2">课程二</el-menu-item>
            <el-menu-item index="2-3">课程三</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="3">
            <template #title>
              <span>科目三</span>
            </template>
            <el-menu-item index="3-1">课程一</el-menu-item>
            <el-menu-item index="3-2">课程二</el-menu-item>
            <el-menu-item index="3-3">课程三</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="4">
            <template #title>
              <span>科目四</span>
            </template>
            <el-menu-item index="4-1">课程一</el-menu-item>
            <el-menu-item index="4-2">课程二</el-menu-item>
            <el-menu-item index="4-3">课程三</el-menu-item>
          </el-sub-menu>
        </el-menu>

      </el-col>

      <el-col :span="12"
        style="height:1000px; padding:10px 30px 10px 30px;  background-color: white; border-right: solid 1px rgb(220,223,230);">
        <h2>{{ course.name }}</h2>
        <h1>课程简介</h1>
        <p>● 先修要求：{{ course.preCourse }}</p>
        <p>● 编程语言：{{ course.lang }}</p>
        <p>● 课程难度：{{ course.difficulty }} 星</p>
        <p>● 预计学时：{{ course.studyTime }} h</p>
        <br>
        <p>{{ course.description }}</p>
        <br>
        <h1>课程资源</h1>
        <p>● 课程网站：{{ course.courseWeb }}</p>
        <p>● 课程视频：{{ course.courseVideo }}</p>
      </el-col>

      <el-col :span="4" style="height:1000px;  background-color: white; padding-left: 30px;">
        <h1>本页目录</h1>
        <p>课程简介</p>
        <p>课程资源</p>
        <p>课程讨论区</p>

      </el-col>
    </el-row>
  </div>




</template>

<script>
export default {
  // data() 返回的属性将会成为响应式的状态
  // 并且暴露在 `this` 上
  data() {
    return {
      course: {
        name: '软件工程',
        preCourse: 'JAVAEE',
        lang: 'JAVE',
        difficulty: '5',
        studyTime: '10',
        description: '为此，这门课的设计者们精心编写了一本书来阐释诸多软件构建的核心原则与前人总结下来的宝贵经验，内容细节到如何编写注释和函数 Specification，如何设计抽象数据结构以及诸多并行编程的内容，并且会让你在精心设计的 Java 编程项目里体验和练习这些编程模式。2016年春季学期这门课开源了其所有编程作业的代码框架，而最新的课程教材可以在其最新的教学网站上找到，具体链接参见下方。',
        courseWeb: 'www.website.com',
        courseVideo: 'www.video.com'
      }

    }
  },

  // methods 是一些用来更改状态与触发更新的函数
  // 它们可以在模板中作为事件监听器绑定
  methods: {
    increment() {
      this.count++
    }
  },

  // 生命周期钩子会在组件生命周期的各个不同阶段被调用
  // 例如这个函数就会在组件挂载完成后被调用
  mounted() {
    console.log(`The initial count is ${this.count}.`)
  }
}
</script>

<style>
a {
  text-decoration: none;
}

.course .el-menu-item {
  background-color: white;
}

</style>