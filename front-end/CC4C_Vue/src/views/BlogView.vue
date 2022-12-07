<template>
  <!-- <div class="blog" style="background-color:white"> -->

  <div class="blog">

    <el-row justify="center" style="height:1000px; width:auto;">

      <el-col :span="20">
        <div style="margin:50px 0px 30px 0px">
          <h2>浏览博客</h2>
        </div>

        <el-tabs v-model="activeName" type="card" class="demo-tabs" style="">
          <el-tab-pane label="综合" name="first">
            <el-row class="goodBlog" style="height: 120px; background-color:white">
              <el-col :span="4" style="padding:5px 5px 5px 5px">
                <el-image style="width: 200px; height: 100px; margin: 5px;" :src="url" />
              </el-col>
              <el-col :span="20">
                <el-row
                  style="height: 40px; font-size: 20px; padding-left: 10px; padding-top: 7px;">
                  博客1
                </el-row>
                <el-row style=" padding-left: 10px;">
                  博客内容简介
                </el-row>
              </el-col>
            </el-row>

            <el-row class="goodBlog" style="height: 120px; background-color:white">
              <el-col :span="4" style="padding:5px 5px 5px 5px">
                <el-image style="width: 200px; height: 100px; margin: 5px;" :src="url" />
              </el-col>
              <el-col :span="20">
                <el-row
                  style="height: 40px; font-size: 20px; padding-left: 10px; padding-top: 7px;">
                  博客1
                </el-row>
                <el-row style=" padding-left: 10px;">
                  博客内容简介
                </el-row>
              </el-col>
            </el-row>

            <el-row class="goodBlog" style="height: 120px; background-color:white">
              <el-col :span="4" style="padding:5px 5px 5px 5px">
                <el-image style="width: 200px; height: 100px; margin: 5px;" :src="url" />
              </el-col>
              <el-col :span="20">
                <el-row
                  style="height: 40px; font-size: 20px; padding-left: 10px; padding-top: 7px;">
                  博客1
                </el-row>
                <el-row style=" padding-left: 10px;">
                  博客内容简介
                </el-row>
              </el-col>
            </el-row>

            <el-row class="goodBlog" style="height: 120px; background-color:white">
              <el-col :span="4" style="padding:5px 5px 5px 5px">
                <el-image style="width: 200px; height: 100px; margin: 5px;" :src="url" />
              </el-col>
              <el-col :span="20">
                <el-row
                  style="height: 40px; font-size: 20px; padding-left: 10px; padding-top: 7px;">
                  博客1
                </el-row>
                <el-row style=" padding-left: 10px;">
                  博客内容简介
                </el-row>
              </el-col>
            </el-row>

            <!-- <el-table :data="tableData" style="width:100%">
              <el-table-column prop="date" label="博客简介" width="auto" />
              <el-table-column prop="name" label="点赞数" width="100px" />
              <el-table-column prop="address" label="浏览数" width="100px" />
            </el-table> -->

          </el-tab-pane>
          <el-tab-pane label="最新" name="second">Config</el-tab-pane>
          <el-tab-pane label="最热" name="third">Role</el-tab-pane>
        </el-tabs>
      </el-col>

    </el-row>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import type { TabsPaneContext } from 'element-plus'

const activeName = ref('first')

const handleClick = (tab: TabsPaneContext, event: Event) => {
  console.log(tab, event)
}

const url =
  // 'https://fuss10.elemecdn.com/e/5d/4a731a90594a4af544c0c25941171jpeg.jpeg'
      'src/assets/QQ20221202185121.jpg'

// 数据
const item = {
  date: '博客1',
  name: '1000',
  address: '10k',
}
const tableData = ref(Array.from({ length: 20 }).fill(item))

</script>
<style>
.goodBlog {
    border-radius: 5px;
    margin-bottom: 5px;
    background-color: aliceblue;
    box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);
}
</style>