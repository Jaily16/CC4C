<template>
  <el-row class="nav-top" style="border-bottom: solid 1px lightgray;">

    <el-col :span="3" style="background-color: aliceblue">
      <el-image style="width: 50px; height: 50px; padding: 7px 0px 0px 10px" src="src\assets\logo\Logo_part1.png" />
      <el-image style="width: 100px; height: 50px; padding: 7px 0px 0px 10px" src="src\assets\logo\Logo_part2.png" />
    </el-col>


    <el-col :span="2" style="text-align: center;background-color: aliceblue;padding: 20px 0px 0px 0px;">
      <router-link to="/home"> 主页 </router-link>
    </el-col>
    <el-col :span="2" style="text-align: center;background-color: aliceblue;padding: 20px 0px 0px 0px;">
      <router-link to="/allCourses"> 所有课程</router-link>
    </el-col>
    <el-col :span="2" style="text-align: center;background-color: aliceblue;padding: 20px 0px 0px 0px;">
      <router-link to="/allBlogs">所有博客</router-link>
    </el-col>
    <el-col :span="1" style="text-align: center;background-color: aliceblue;padding: 20px 0px 0px 0px;">
    </el-col>


    <!-- <el-col :span="8" class="logo">
        <el-menu mode="horizontal" style="background-color: aliceblue">
          <router-link to="/"><el-menu-item index="1"> 主页 </el-menu-item></router-link>
          <router-link to="/allCourses"> <el-menu-item index="2"> 所有课程 </el-menu-item></router-link>
          <router-link to="/allBlogs"> <el-menu-item index="3"> 所有博客 </el-menu-item></router-link>
        </el-menu>
      </el-col> -->

    <el-col :span="12" style="background-color: aliceblue">
      <el-image style="width: 400px; height: 50px; padding: 7px 0px 0px 10px" src="src\assets\logo\Logo_part3.png" />
    </el-col>

    <el-col :span="2" style="background-color: aliceblue; padding:20px 0px 0px 0px; text-align: center;">
      <el-button v-if="this.$store.state.user.id != ''" size="small" @click="logout()" :icon="SwitchButton">
        退出登录
      </el-button>
      <el-button v-else size="small" type="primary" @click="flyToLogin()" :icon="Position">登录</el-button>
    </el-col>

    <!-- <el-col :span="4" class="searching" style="background-color: aliceblue">
        <el-input :prefix-icon="Search" placeholder="搜索" style="padding:15px 20px 0px 0px" />
      </el-col> -->

  </el-row>
</template>
  
<script setup>
import axios from 'axios';
import store from '@/store'
import {
  SwitchButton,
  Position
} from "@element-plus/icons-vue"

// 登出
function logout() {
  axios.get('http://localhost:4080/users/logout').then((resp) => {
    if (resp.data.data == true) {
      alert('退出登录！')
      store.commit('RESET_STATE');
      window.location.href = "http://localhost:5173/home";
    }
    else {
      alert('退出登录失败！')
    }
  })
}

// 跳转至登陆
function flyToLogin() {
  window.location.href = 'http://localhost:5173/login'
}

</script>
  
<style scoped>
a {
  text-decoration: none;
}

.router-link-active {
  text-decoration: none;
}
</style>