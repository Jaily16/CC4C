<template>
  <el-menu :default-openeds="['5', '6']" :collapse="isCollapse" :collapse-transition="false"
    style="background-color: aliceblue; height: 100%">
    <el-row>
      <el-switch v-model="isCollapse" style="padding-left: 14px" />

    </el-row>

    <div v-if="!isCollapse" style="width: 200px;padding: 10px 0px 0px 0px;">
      <el-row>
        <el-col :span="24" style="text-align: center;">
          <el-avatar v-if="this.$store.state.user.id == ''" shape="square" :size="100" src="src\assets\kun.png" />
          <el-avatar v-else shape="square" :size="100" :src="this.$store.state.user.avatar" />
          <p v-if="this.$store.state.user.id == ''">游客</p>
          <p v-else>{{ this.$store.state.user.name }}</p>
        </el-col>

        <!-- <el-col :span="12">
          <el-button @click="select" style="height:70px; width: 70px;border-radius: 100%;">

            <el-image v-for="lang in langs" v-show="lang.name == mainLang" :src="lang.icon" fit="cover" style="height:60px; width: 60px;"></el-image>
          </el-button> -->


        <!-- <el-card v-show="isSelecting"
            style="position:fixed; height: 500px; width: 420px; top:100px; left:250px; z-index:100; ">
            <p>请选择当前语言: {{ mainLang }}</p>
            <el-radio-group v-model="mainLang" size="small">
              <el-radio-button v-for="lang in langs" :label="lang.name" @click="selectMainLang()" >
                <div style="height: 80px; width: 80px; margin: 10px 10px 10px 10px;">
                  <el-image :src="lang.icon"></el-image>
                </div>
              </el-radio-button>
            </el-radio-group>
          </el-card> -->
        <!-- </el-col> -->
      </el-row>

    </div>

    <el-menu-item-group title="">
      <RouterLink to="/home">
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
          <template #title> 个人空间 </template>
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

      <!-- <router-link to="/history">
        <el-menu-item index="4">
          <el-icon>
            <Histogram />
          </el-icon>
          <template #title>足迹</template>
        </el-menu-item>
      </router-link> -->
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
      <!-- <RouterLink to="/document">
        <el-menu-item index="5-2"> 官方文档 </el-menu-item>
      </RouterLink> -->
    </el-sub-menu>
    <el-sub-menu id="nav-blog" index="6">
      <template #title>
        <el-icon>
          <Notebook />
        </el-icon>
        <span>博客广场</span>
      </template>
      <RouterLink to="/blog">
        <el-menu-item index="6-1"> 浏览博客 </el-menu-item>
      </RouterLink>
      <RouterLink to="/blogwrite">
        <el-menu-item index="6-2"> 撰写博客 </el-menu-item>
      </RouterLink>
      <RouterLink to="/blogmanage">
        <el-menu-item index="6-3"> 管理博客 </el-menu-item>
      </RouterLink>
    </el-sub-menu>
  </el-menu>
</template>

<script setup>
import { ref, reactive } from "vue";
import {
  Notebook,
  HomeFilled,
  StarFilled,
  UserFilled,
  Histogram,
  Reading,
} from "@element-plus/icons-vue";

const squareUrl = ref('src/assets/kun.png');
// 折叠控制
const isCollapse = ref(false);

// const isSelecting = ref(false);

// const props = defineProps(['activeIndex'])

// function select() {
//   isSelecting.value = !isSelecting.value;
// };
// function selectMainLang(){
//   isSelecting.value = false;
// }

// const mainLang = ref('java');

// const langs = [
//   {
//     name: 'java',
//     icon: 'src/assets/JAVA.png'
//   },
//   {
//     name: 'c',
//     icon: 'src/assets/C.png'
//   }, {
//     name: 'c++',
//     icon: 'src/assets/C++.png'
//   }, {
//     name: 'python',
//     icon: 'src/assets/PYTHON.png'
//   },]

</script>

<style scoped>
a {
  text-decoration: none;
}

.router-link-active {
  text-decoration: none;
}

.el-aside .el-menu-item {
  height: 40px;
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



