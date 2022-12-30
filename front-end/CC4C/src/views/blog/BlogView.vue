
<template>

  <div class="blog" style="background-color: rgb(244,246,248);">
    <el-row justify="center" style=" width:auto;">
      <el-col :span="22"
        style="height:1000px; background-color: white; margin:30px 0px 0px 0px; padding: 0px 50px 30px 50px; border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
        <div style="margin:0px 0px 30px 0px">
          <h2>浏览博客</h2>
        </div>
        <el-scrollbar height="900px">
          <el-row v-for="blog in blogList" @dblclick="flyTo(blog.blogId)"
            style="border-radius: 5px;background-color: aliceblue;box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);height: 80px; margin:0px 0px 10px 0px;">

            <el-col :span="22">
              <el-row>
                <span style="margin:5px; font-size: 20px; padding-left: 10px; padding-top: 7px; font-weight: bolder;">
                  {{ blog.title }}
                </span>
                <span style="margin:5px; padding: 10px;">
                  发布时间：{{ blog.publishTime }}
                </span>
              </el-row>
              <el-row>
              </el-row>
              <el-row>
                <el-icon style="padding-left: 20px;">
                  <View></View>
                </el-icon>
                <span style="padding-left: 5px; font-size: smaller;">
                  {{ blog.click }}
                </span>
              </el-row>
            </el-col>
          </el-row>
        </el-scrollbar>
      </el-col>
    </el-row>
  </div>

</template>
  
  
  
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router';
import axios from 'axios';
import { View } from "@element-plus/icons-vue";


const router = useRouter();

// 验证用户的登陆状态
axios.defaults.withCredentials = true;//这样全局设置允许
axios.get("http://localhost:4080/users/verify").then((resp) => {
  if (resp.data.data == false) {
    alert(resp.data.msg);
    window.location.href = "http://localhost:5173/login";
    return;
  }
});

// 博客列表
var blogList = ref([]);

// 将参数传给具体博客页面
function flyTo(blogId) {
  axios.put('http://localhost:4080/blogs/click/' + blogId).then((resp) => { })
  router.push({ path: '/blogDetail', query: { blogId: blogId } })
}

axios.get("http://localhost:4080/blogs/list/1").then((resp) => {
  blogList.value = resp.data.data;
});


</script>
<style scoped>
.goodBlog {
  border-radius: 5px;
  margin-bottom: 5px;
  background-color: aliceblue;
  box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);
}

a {
  text-decoration: none;
}

.router-link-active {
  text-decoration: none;
}
</style>