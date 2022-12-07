<script lang="ts" setup>
// 导入头像
import { reactive, toRefs } from "vue";

import { EditPen } from "@element-plus/icons-vue";

const state = reactive({
  circleUrl:
    "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png",
  squareUrl:
    "https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png",
  sizeList: ["small", "", "large"] as const,
});

const { circleUrl, squareUrl, sizeList } = toRefs(state);
</script>

<template>
  <div class="userinfo" style="height: 1000px; background-color: white">
    <el-row justify="center">
      <!-- <el-col :span="20" style="text-align: center;  height:150px;">
        欢迎来到你的开发社区个人资料<br>
        首先添加一张吸引眼球的封面图片吧。大胆展示你的独特风格！<br>
        上传封面图片<br>
      </el-col> -->
    </el-row>
    <el-row justify="center">
      <el-col :span="3">
        <el-avatar shape="circle" :size="150" :src="circleUrl" />
      </el-col>
      <el-col :span="14">
        <p style="font-size: 50px; margin: 0px">username</p>
        <span>0文章 | 0回答 | 0教程 | 0片段</span>
        <br />
      </el-col>
      <el-col :span="3">
        <el-button>
          <el-icon>
            <EditPen />
          </el-icon>
          <div style="padding: 7px">编辑个人资料</div>
        </el-button>
      </el-col>
    </el-row>

    <el-row justify="center" style="margin-top: 30px">
      <el-col
        :span="2"
        style="background-color: aliceblue; height: 60px; border-radius: 5px"
      >
        <div style="font-size: 20px; padding-top: 17px; padding-left: 17px">
          <!-- <RouterLink to="/userinfo"> -->
          <span style="font-weight: 1000"> 个人信息</span>
          <!-- </RouterLink> -->
        </div>
      </el-col>
      <el-col
        :span="1"
        style="background-color: aliceblue; height: 60px; border-radius: 5px"
      >
        <div style="font-size: 20px; padding-top: 17px; padding-left: 5px">
          <RouterLink to="/favorite">
            <span>收藏</span>
          </RouterLink>
        </div>
      </el-col>
      <el-col
        :span="1"
        style="background-color: aliceblue; height: 60px; border-radius: 5px"
      >
        <div style="font-size: 20px; padding-top: 17px; padding-left: 17px">
          <RouterLink to="/userinfo">
            <span>足迹</span>
          </RouterLink>
        </div>
      </el-col>
      <el-col
        :span="16"
        style="background-color: aliceblue; height: 60px; border-radius: 5px"
      >
      </el-col>
    </el-row>

    <el-row justify="center" style="margin-top: 40px">
      <el-col :span="15" style="height: 60px">
        <el-row>
          <div style="font-size: 20px; text-align: left">个人介绍</div>
        </el-row>
        <div style="text-align: center">
          <p>
            介绍一下你自己 <br />
            使用这部分让我们了解你。<br />
            请随意添加你的社交账户、标签和链接。<br />
          </p>
        </div>
      </el-col>
      <el-col :span="5" class="language-manage">
        <h1>语言订阅</h1>
        <p>Java</p>
        <p>Python</p>
        <p>C++</p>
      </el-col>
    </el-row>
  </div>
</template>

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
