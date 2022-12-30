
<template>
  <div style="background-color: rgb(244,246,248);">

    <el-row justify="center">
      <el-col :span="22" style="background-color: white; margin: 20px 0px 0px 0px; padding: 20px 0px 20px 0px;border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
        <UserInfo activeIndex="1"></UserInfo>
      </el-col>
    </el-row>



    <div>
      <el-row justify="center" style="margin-top: 20px">
        <el-col :span="22"
          style="height: 800px; background-color: white; margin: 0px 0px 0px 0px; padding: 00px 30px 20px 30px;border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
          <el-row>
            <h2>个人信息</h2>
          </el-row>
          <div style="padding: 0px 0px 0px 50px;">
            <el-row>
              <el-col :span="12">
                <h3>用户名</h3>
                <p
                  style="border: 1px solid rgb(220,223,230); border-radius: 5px; background-color: white; width: 70%; padding: 10px 10px 10px 10px;">
                  {{ user.name }}</p>


                <h3>邮箱</h3>
                <p
                  style="border: 1px solid rgb(220,223,230); border-radius: 5px; background-color: white; width: 70%; padding: 10px 10px 10px 10px;">
                  {{ user.email }}</p>
              </el-col>
              <el-col :span="12">
                <h3>专业</h3>
                <el-select v-model="user.major" size="large">
                  <el-option v-for="(major, key) in majors" :key="key" :label="major.label" :value="major.value"
                    disabled />
                </el-select>

                <h3>订阅语言</h3>
                <el-select v-model="user.language" size="large">
                  <el-option v-for="(lang, key) in langs" :key="key" :label="lang.label" :value="lang.value" disabled />
                </el-select>
              </el-col>
            </el-row>




          </div>
        </el-col>

      </el-row>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import UserInfo from '../components/UserInfo.vue';
import store from '@/store';
import axios from "axios";

// 验证用户的登陆状态
axios.defaults.withCredentials = true;//这样全局设置允许
axios.get("http://localhost:4080/users/verify").then((resp) => {
  if (resp.data.data == false) {
    alert(resp.data.msg);
    window.location.href = "http://localhost:5173/login";
    return;
  }
});

// 获取用户信息
var user = {
  id: store.state.user.id,
  name: store.state.user.name,
  email: store.state.user.email,
  major: Number(store.state.user.major),
  language: Number(store.state.user.language),
  avatar: store.state.user.avatar,
}

// 语言列表
const langs = [
  {
    value: 1,
    label: 'java',
  },
  {
    value: 2,
    label: 'c++',
  },
  {
    value: 3,
    label: 'python',
  },
  {
    value: 4,
    label: 'c',
  }
]
// 专业列表
const majors = [
  {
    value: 0,
    label: '不方便透露',
  },
  {
    value: 1,
    label: '计算机专业',
  },
  {
    value: -1,
    label: '非计算机专业',
  }
]



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
</style>
