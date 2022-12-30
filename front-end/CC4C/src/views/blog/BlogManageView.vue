
<template>
  <div class="blog">
    <div style="background-color: rgb(244,246,248);">

      <el-row justify="center">
        <el-col :span="22"
          style="background-color: white; margin: 20px 0px 0px 0px; padding: 20px 0px 20px 0px; border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
          <UserInfo activeIndex="4"></UserInfo>
        </el-col>
      </el-row>

      <el-row justify="center" style="">
        <el-col :span="22"
          style="background-color: white; margin: 20px 0px 0px 0px; padding: 0px 30px 30px 30px; border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19);">
          <div>
            <h2>我的博客</h2>
          </div>
          <!-- 
          <el-tabs v-model="activeName" type="card" class="demo-tabs" style="">
            <el-tab-pane label="综合" name="first"> -->
          <el-scrollbar height="700px">
            <el-row v-for="blog in blogList" @dblclick="flyTo(blog.blogId)"
              style="height: 80px;margin: 0px 0px 10px 0px;background-color: aliceblue;border-radius: 5px;box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);">
              <el-col :span="21">
                <el-row style="height: 40px; font-size: 20px; padding-left: 10px; padding-top: 7px; ">
                  <el-col :span="21">
                    <span style="font-weight: bolder;">
                      {{ blog.title }}
                    </span>
                  </el-col>
                  <el-col :span="3">
                    <p v-if="blog.state == -1" style="color:red">审核不通过</p>
                    <p v-if="blog.state == 0">待审核</p>
                    <p v-if="blog.state == 1" style="color:green">审核通过</p>
                  </el-col>
                </el-row>

                <!-- <el-row style=" padding-left: 10px;">
                博客内容简介
              </el-row> -->
              </el-col>

              <!-- <el-col :span="3" style="padding:25px 0px 0px 20px ">
              <el-popconfirm title="你确定要删除这篇博客吗？">
                <template #reference>
                  <el-button type="danger">删除</el-button>
                </template>
              </el-popconfirm>
              <el-button type="danger" @click="ifDelete = true">删除</el-button>

              <el-dialog v-model="ifDelete" width="30%" align-center>
                <span>你确定要删除这篇博客吗？</span>
                <template #footer>
                  <el-button @click="ifDelete = false">取消</el-button>
                  <el-button type="primary" @click="ifDelete = false">
                    确认
                  </el-button>
                </template>
              </el-dialog>

              <el-button type="primary">编辑</el-button>
            </el-col> -->
            </el-row>
          </el-scrollbar>

          <!-- <el-row justify="center">
            <el-pagination small background layout="prev, pager, next" :total="50" class="mt-4" />
          </el-row> -->
          <!-- </el-tab-pane>
            <el-tab-pane label="最新" name="second">Config</el-tab-pane>
            <el-tab-pane label="最热" name="third">Role</el-tab-pane>
          </el-tabs> -->
        </el-col>

      </el-row>
    </div>
  </div>
</template>



<script lang='ts' setup>
import UserInfo from '@/components/UserInfo.vue';
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router';
import store from '@/store';

import axios from 'axios';

// 验证用户的登陆状态
axios.defaults.withCredentials = true;//这样全局设置允许
axios.get("http://localhost:4080/users/verify").then((resp) => {
  if (resp.data.data == false) {
    alert(resp.data.msg);
    window.location.href = "http://localhost:5173/login";
    return;
  }
});

// 我的博客
const blogList = ref([]);
// 获取用户写的博客
axios.get("http://localhost:4080/blogs/myBlogs/" + store.state.user.id).then((resp) => {
  blogList.value = resp.data.data;
});

// 将参数传给具体博客页面
const router = useRouter();
function flyTo(blogId) {
  router.push({ path: '/blogDetail', query: { blogId: blogId } })
}

</script>
<style scoped>

</style>