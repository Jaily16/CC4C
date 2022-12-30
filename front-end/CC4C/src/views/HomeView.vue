<template>
  <div style="background-color: rgb(244,246,248);">
    <el-row justify="center">
      <el-col :span="22">
        <div
          style="background-color: white; padding: 10px 30px 0px 30px; margin: 20px 0px 0px 0px;border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
          <h2>合作伙伴</h2>
          <!-- <h2>语言介绍</h2> -->
          <el-carousel type="card" height="350px">
            <el-carousel-item v-for="item in langDisplay">
              <!-- <h3 class="small justify-center" text="2xl">广告位招租</h3> -->

              <el-image :src="item" :fit="fit"></el-image>
            </el-carousel-item>
          </el-carousel>
        </div>

        <div
          style="background-color: white; padding: 0px 30px 30px 30px; margin: 20px 0px 0px 0px;border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
          <el-row>
            <el-col :span="23">
              <h2>课程推荐</h2>
            </el-col>
            <el-col :span="1">
              <router-link to="/allCourses">
                <p>更多</p>
              </router-link>
            </el-col>
          </el-row>

          <el-row justify="center" :gutter="30">
            <el-col v-for="course in courses.slice(0, 6)" :span="4" @click="flyToCourse(course.courseName)">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image v-if="course.languageName == 'java'" style="width: 200px; height: 100px"
                    src="src\assets\LangImg\JavaImg.png" />
                  <el-image v-if="course.languageName == 'c'" style="width: 200px; height: 100px"
                    src="src\assets\LangImg\CImg.png" />
                  <el-image v-if="course.languageName == 'c++'" style="width: 200px; height: 100px"
                    src="src\assets\LangImg\C++Img.png" />
                  <el-image v-if="course.languageName == 'python'" style="width: 200px; height: 100px"
                    src="src\assets\LangImg\PythonImg.png" />
                </el-row>
                <el-row style="padding: 10px 10px; font-size: 10px;height:50px">
                  {{ course.courseName }}
                  <!-- <br /> -->
                  <!-- {{ course.favorsNum }} -->
                </el-row>
              </div>
            </el-col>
          </el-row>
        </div>

        <div
          style="background-color: white; padding: 0px 30px 30px 30px; margin: 20px 0px 0px 0px;border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
          <el-row>
            <el-col :span="23">
              <h2>博客推荐</h2>
            </el-col>
            <el-col :span="1">
              <router-link to="/allBlogs">
                <p>更多</p>
              </router-link>
            </el-col>
          </el-row>

          <el-row class="goodBlog" v-for="blog in blogs.slice(0, 4)" @dblclick="flyToBlog(blog.blogId)"
            style="height: 70px; margin: 0px 0px 10px 0px;">
            <el-col :span="20">
              <el-row style="font-size: 15px; padding: 5px 0px 0px 10px">
                {{ blog.poster }}
              </el-row>
              <el-row style="
                  font-size: 20px;
                  font-weight: 1000;
                  padding: 5px 0px 5px 10px;
                ">
                {{ blog.title }}
              </el-row>
              <el-row style="font-size: 15px; padding: 5px 0px 0px 10px">
                {{ blog.publishTime }}
              </el-row>
            </el-col>
          </el-row>

        </div>
      </el-col>
    </el-row>

  </div>
</template>
<script setup>
import { ref, reactive } from "vue";
import { useRouter } from 'vue-router';
import axios from "axios";

const router = useRouter();

const langDisplay = [
  'src/assets/LangImg/Bilibili.jpg',
  'src/assets/LangImg/Java_Display.jpg',
  'src/assets/LangImg/C++_Display.jpg',
  'src/assets/LangImg/Python_Display.jpg',
  'src/assets/LangImg/C_Display.jpg',
]

axios.defaults.withCredentials = true;//这样全局设置允许

// 获得所有课程
const courses = ref([]);
axios.get("http://localhost:4080/courses/home").then((resp) => {
  courses.value = resp.data.data;
});
// 获得所有博客
const blogs = ref([]);
axios.get("http://localhost:4080/blogs/home").then((resp) => {
  blogs.value = resp.data.data;
});


function flyToCourse(courseName) {
  router.push({ path: '/courseDetail', query: { courseName: courseName } })
}
function flyToBlog(blogId) {
  axios.put('http://localhost:4080/blogs/click/' + blogId).then((resp) => { })
  router.push({ path: '/blogDetail', query: { blogId: blogId } })
}
</script>

<style scoped>
a {
  text-decoration: none;
}

.router-link-active {
  text-decoration: none;
}

/* 走马灯 */
.el-carousel__item h3 {
  color: #475669;
  opacity: 0.75;
  line-height: 150px;
  margin: 0;
  text-align: center;
}

.el-carousel__item:nth-child(2n) {
  background-color: #99a9bf;
}

.el-carousel__item:nth-child(2n + 1) {
  background-color: #d3dce6;
}

.goodCourse {
  border-radius: 5px;
  background-color: aliceblue;
  box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);
}

.goodBlog {
  border-radius: 5px;
  margin-bottom: 5px;
  background-color: aliceblue;
  box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);
}
</style>