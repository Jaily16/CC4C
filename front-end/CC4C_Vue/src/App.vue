<script lang="ts" setup>
import { RouterLink, RouterView } from "vue-router";
// 导入图标
import {
  Setting,
  Notebook,
  HomeFilled,
  StarFilled,
  UserFilled,
  Histogram,
  Reading,
  Search,
} from "@element-plus/icons-vue";

import { ref } from "vue";
// 导入头像
import { reactive, toRefs } from "vue";

const state = reactive({
  circleUrl:
    "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png",
  squareUrl:
    "https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png",
  sizeList: ["small", "", "large"] as const,
});
const { circleUrl, squareUrl, sizeList } = toRefs(state);

var isCollapse = ref(false);
</script>

<template>
  <div class="app">
    <el-container style="background-color: aliceblue">
      <el-header class="header" style="padding-left: 0">
        <el-row class="nav-top">
          <el-col :span="3">
            <el-image
              style="width: 50px; height: 50px; padding: 7px 0px 0px 10px"
              src="src\assets\Logo_part1.png"
            />
            <el-image
              style="width: 100px; height: 50px; padding: 7px 0px 0px 10px"
              src="src\assets\Logo_part2.png"
            ></el-image>
          </el-col>

          <el-col :span="8" class="logo">
            <el-menu mode="horizontal" style="background-color: aliceblue">
              <el-menu-item index="1"> 主页 </el-menu-item>
              <el-menu-item index="2"> 所有课程 </el-menu-item>
              <el-menu-item index="3"> 博客广场 </el-menu-item>
            </el-menu>
          </el-col>

          <el-col :span="9">
            <el-image
              style="width: 400px; height: 50px; padding: 7px 0px 0px 10px"
              src="src\assets\Logo_part3.png"
            />
          </el-col>

          <el-col :span="4" class="searching">
            <el-input
              :prefix-icon="Search"
              placeholder="搜索"
              style="margin-top: 10px"
            >
            </el-input>
          </el-col>
        </el-row>
      </el-header>

      <el-container style="height: 877px">
        <!-- <el-aside :width="isCollapse ? '64px' : '250px'"> -->
        <el-aside width="auto">
          <el-menu
            default-active="1"
            :default-openeds="['5', '6']"
            :collapse="isCollapse"
            :collapse-transition="false"
            style="background-color: aliceblue"
          >
            <!-- <el-button @click="toggleCollapse">
              |||
            </el-button> -->

            <el-switch v-model="isCollapse" style="padding-left: 14px" />

            <!-- <template #title> -->
            <div class="userinfo" style="width: 200px">
              <el-avatar
                v-if="!isCollapse"
                shape="square"
                :size="70"
                :src="squareUrl"
              />
              <p v-if="!isCollapse">USERNAME</p>
            </div>
            <!-- </template> -->

            <el-menu-item-group title="">
              <RouterLink to="/">
                <el-menu-item index="1">
                  <el-icon>
                    <HomeFilled />
                  </el-icon>
                  <template #title> 主页 </template>
                </el-menu-item>
              </RouterLink>

              <RouterLink to="/userinfo">
                <el-menu-item index="2">
                  <el-icon>
                    <UserFilled />
                  </el-icon>
                  <template #title> 个人信息 </template>
                </el-menu-item>
              </RouterLink>

              <router-link to="/favorite">
                <el-menu-item index="3">
                  <el-icon>
                    <StarFilled />
                  </el-icon>
                  <template #title> 收藏 </template>
                </el-menu-item>
              </router-link>

              <el-menu-item index="4">
                <el-icon>
                  <Histogram />
                </el-icon>
                <template #title>足迹</template>
              </el-menu-item>
            </el-menu-item-group>

            <el-sub-menu id="nav-study" index="5">
              <template #title>
                <el-icon>
                  <Reading />
                </el-icon>
                <span>学习资源</span>
              </template>
              <RouterLink to="/Course">
                <el-menu-item index="5-1"> 浏览课程 </el-menu-item>
              </RouterLink>
              <el-menu-item index="5-2"> 官方文档 </el-menu-item>
            </el-sub-menu>
            <el-sub-menu id="nav-blog" index="6">
              <template #title>
                <el-icon>
                  <Notebook />
                </el-icon>
                <span>博客广场</span>
              </template>
              <RouterLink to="/Blog">
                <el-menu-item index="6-1"> 浏览博客 </el-menu-item>
              </RouterLink>
              <RouterLink to="/write">
                <el-menu-item index="6-2"> 撰写博客 </el-menu-item>
              </RouterLink>
              <RouterLink to="/blogmanage">
                <el-menu-item index="6-3"> 管理博客 </el-menu-item>
              </RouterLink>
            </el-sub-menu>
          </el-menu>
        </el-aside>

        <el-main style="width: auto; padding: 0">
          <router-view></router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
/* 去除routerlink下划线 */
a {
  text-decoration: none;
}

.router-link-active {
  text-decoration: none;
}

/* .nav-top {
  border-bottom-width: 1px;
} */

#nav-study {
  border-top-width: 1px;
  border-top-style: solid;
  border-top-color: lightgrey;
}

#nav-blog {
  border-top-width: 1px;
  border-top-style: solid;
  border-top-color: lightgrey;
}

.el-aside .el-menu-item {
  height: 40px;
}

.userinfo {
  padding-left: 20px;
  padding-top: 20px;
}

.el-header {
  position: sticky;
}

.el-aside {
  position: sticky;
}

.el-menu-item {
  background-color: aliceblue;
}

.el-sub-menu {
  background-color: aliceblue;
}
</style>
