<template>
    <div class="layout">
        <el-container>
            <el-header style="padding:0px 0px 0px 10px; background-color: aliceblue;">
                <p style="font-size: 20px;font-weight: bolder;">CC4C后台管理系统</p>
            </el-header>

            <el-container>
                <el-aside width="auto" style="padding:0px 0px 0px 0px; background-color: aliceblue;">
                    <el-menu style="background-color: aliceblue;">
                        <el-menu-item>
                            <router-link to="CoursesAndBlogs">
                                查看课程和博客
                            </router-link>
                        </el-menu-item>
                        <el-menu-item>
                            <router-link to="addCourse">
                                添加课程
                            </router-link>
                        </el-menu-item>
                        <el-menu-item>
                            <router-link to="checkBlog">
                                审核博客
                            </router-link>
                        </el-menu-item>
                    </el-menu>

                </el-aside>
                <el-container>
                    <el-main style="padding:0px 0px 0px 0px;">
                        <router-view></router-view>
                    </el-main>
                    <!-- <el-footer style="background-color: rgb(244, 246, 248);">

                    </el-footer> -->
                </el-container>
            </el-container>
        </el-container>
    </div>
</template>
  
<script setup>
import axios from 'axios';

axios.defaults.withCredentials = true;//这样全局设置允许

axios.get("http://localhost:4080/admin/verify").then((resp) => {
    if (resp.data.data == false) {
        alert(resp.data.msg);
        window.location.href = "http://localhost:5173/adminlogin";
        return;
    }
});
</script>
  
<style scoped>
a {
    text-decoration: none;
}

.router-link-active {
    text-decoration: none;
}
</style>
     
  
  