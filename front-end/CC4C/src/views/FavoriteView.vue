

<template>

  <div style="background-color: rgb(244,246,248);">

    <el-row justify="center">
      <el-col :span="22" style="background-color: white; margin: 20px 0px 0px 0px; padding: 20px 0px 20px 0px;border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
        <UserInfo activeIndex="2"></UserInfo>
      </el-col>
    </el-row>


    <div>
      <el-row justify="center">

        <el-col :span="13"
          style=" background-color: white; height: 650px; margin: 20px 20px 0px 0px; padding: 0px 30px 0px 30px; border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
          <h2>收藏课程</h2>

          <el-scrollbar height="550px">
            <el-row gutter="20">
              <el-col v-for="favoriteCourse in favoriteCourses" :span="8" style="padding: 0px 10px 10px 10px;"
                @dblclick="flyToCourse(favoriteCourse.courseName)">
                <div
                  style="  border-radius: 5px;background-color: aliceblue;box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);">
                  <el-row style="padding: 20px 30px 10px 30px">
                    <el-image v-if="favoriteCourse.languageName == 'java'" style="width: 200px; height: 100px"
                      src="src\assets\LangImg\JavaImg.png" />
                    <el-image v-if="favoriteCourse.languageName == 'c'" style="width: 200px; height: 100px"
                      src="src\assets\LangImg\CImg.png" />
                    <el-image v-if="favoriteCourse.languageName == 'c++'" style="width: 200px; height: 100px"
                      src="src\assets\LangImg\C++Img.png" />
                    <el-image v-if="favoriteCourse.languageName == 'python'" style="width: 200px; height: 100px"
                      src="src\assets\LangImg\PythonImg.png" />
                  </el-row>
                  <el-row style="padding: 10px 10px; font-size: 10px;">
                    {{ favoriteCourse.courseName }}
                  </el-row>
                </div>
              </el-col>
            </el-row>
          </el-scrollbar>
          <!-- <el-row justify="center">
            <el-pagination small background layout="prev, pager, next" :total="50" class="mt-4" />
          </el-row> -->
        </el-col>

        <el-col :span="8"
          style="height: 650px; margin: 20px 0px 0px 0px; padding: 0px 20px 0px 20px; background-color: white;border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
          <h2>收藏博客</h2>
          <el-scrollbar height="550px">
            <el-row v-for="favoriteBlog in favoriteBlogs" class="goodBlog" @dblclick="flyToBlog(favoriteBlog.blogId)"
              style="height: 70px; margin: 0px 0px 10px 0px;">
              <el-col>
                <el-row style="
                  height: 40px;
                  font-size: 20px;
                  padding-left: 10px;
                  padding-top: 7px;
                ">
                  {{ favoriteBlog.title }}
                </el-row>
                <el-row style="padding-left: 10px"> {{ favoriteBlog.publishTime }} </el-row>
              </el-col>
            </el-row>
          </el-scrollbar>

          <el-row justify="center">
            <el-pagination small background layout="prev, pager, next" :total="50" class="mt-4" />
          </el-row>

        </el-col>
      </el-row>
    </div>


  </div>

</template>

<script lang="ts" setup>
import { ref, reactive } from "vue";
import UserInfo from "../components/UserInfo.vue";
import axios from "axios";
import { useRouter } from 'vue-router';
import store from '@/store'

const router = useRouter();

var favoriteCourses = ref([]);
var favoriteBlogs = ref([]);


// 验证用户的登陆状态
axios.defaults.withCredentials = true;//这样全局设置允许
axios.get("http://localhost:4080/users/verify").then((resp) => {
  if (resp.data.data == false) {
    alert(resp.data.msg);
    window.location.href = "http://localhost:5173/login";
    return;
  }
});

// 获取用户收藏的课程和博客
axios.get("http://localhost:4080/courses/favorList/" + store.state.user.id).then((resp) => {
  favoriteCourses.value = resp.data.data;
});
axios.get("http://localhost:4080/blogs/collectList/" + store.state.user.id).then((resp) => {
  favoriteBlogs.value = resp.data.data;
});

// 跳转至课程
function flyToCourse(courseName) {
  router.push({ path: '/courseDetail', query: { courseName: courseName } })
}
// 跳转至博客
function flyToBlog(blogId) {
  axios.put('http://localhost:4080/blogs/click/' + blogId).then((resp) => { })
  router.push({ path: '/blogDetail', query: { blogId: blogId } })
}
</script>
<style scoped>
.language-manage {
  height: 250px;
  border: 1px;
  border-color: white;
  border-style: solid;
  border-radius: 5px;
  padding-left: 15px;
  padding-top: 15px;
  box-shadow: 2px 2px lightgray;
}

a {
  text-decoration: none;
}

.router-link-active {
  text-decoration: none;
}
</style>
