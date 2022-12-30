<template>
  <div class="layout">
    <el-container>
      <el-header style="padding:0px 0px 0px 0px;">
        <Header></Header>
      </el-header>

      <el-container>
        <el-aside width="auto" style="padding:0px 0px 0px 0px;">
          <Sliber></Sliber>
        </el-aside>
        <el-container  style=" overflow: hidden;">
          <el-main style="padding:0px 0px 0px 0px; overflow: hidden;">
            <router-view></router-view>
          </el-main>

        </el-container>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
// import { NavLeft, NavTop } from "@/components";
import Sliber from './components/sliber.vue'
import Header from './components/header.vue'
</script>

<style scoped>

</style>
   

