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

const url =
  // 'https://fuss10.elemecdn.com/e/5d/4a731a90594a4af544c0c25941171jpeg.jpeg'
  "src/assets/QQ20221202185121.jpg";
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
          <RouterLink to="/userinfo">
            <span> 个人信息</span>
          </RouterLink>
        </div>
      </el-col>
      <el-col
        :span="1"
        style="background-color: aliceblue; height: 60px; border-radius: 5px"
      >
        <div style="font-size: 20px; padding-top: 17px; padding-left: 5px">
          <!-- <RouterLink to="/favorite"> -->
          <span style="font-weight: 1000">收藏</span>
          <!-- </RouterLink> -->
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

    <div>
      <el-row justify="center">
        <el-col :span="12" style="height: 700px; padding: 0px 20px 0px 20px">
          <h1>收藏课程</h1>
          <el-row gutter="20" justify="center">
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
          </el-row>
          <br />
          <el-row gutter="20" justify="center">
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
          </el-row>
          <br />
          <el-row gutter="20" justify="center">
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px"> 课程1 </el-row>
              </div>
            </el-col>
          </el-row>
          <br />
          <el-row justify="center">
            <el-pagination
              small
              background
              layout="prev, pager, next"
              :total="50"
              class="mt-4"
            />
          </el-row>
        </el-col>
        <el-col :span="8" style="height: 700px; padding: 0px 20px 0px 20px">
          <h1>收藏博客</h1>
          <el-row class="goodBlog" style="height: 70px">
            <el-col>
              <el-row
                style="
                  height: 40px;
                  font-size: 20px;
                  padding-left: 10px;
                  padding-top: 7px;
                "
              >
                博客1
              </el-row>
              <el-row style="padding-left: 10px"> 博客内容简介 </el-row>
            </el-col>
          </el-row>
          <br />
          <el-row class="goodBlog" style="height: 70px">
            <el-col>
              <el-row
                style="
                  height: 40px;
                  font-size: 20px;
                  padding-left: 10px;
                  padding-top: 7px;
                "
              >
                博客1
              </el-row>
              <el-row style="padding-left: 10px"> 博客内容简介 </el-row>
            </el-col>
          </el-row>
          <br />
          <el-row class="goodBlog" style="height: 70px">
            <el-col>
              <el-row
                style="
                  height: 40px;
                  font-size: 20px;
                  padding-left: 10px;
                  padding-top: 7px;
                "
              >
                博客1
              </el-row>
              <el-row style="padding-left: 10px"> 博客内容简介 </el-row>
            </el-col>
          </el-row>
          <br />
          <el-row class="goodBlog" style="height: 70px">
            <el-col>
              <el-row
                style="
                  height: 40px;
                  font-size: 20px;
                  padding-left: 10px;
                  padding-top: 7px;
                "
              >
                博客1
              </el-row>
              <el-row style="padding-left: 10px"> 博客内容简介 </el-row>
            </el-col>
          </el-row>
          <br />
          <el-row class="goodBlog" style="height: 70px">
            <el-col>
              <el-row
                style="
                  height: 40px;
                  font-size: 20px;
                  padding-left: 10px;
                  padding-top: 7px;
                "
              >
                博客1
              </el-row>
              <el-row style="padding-left: 10px"> 博客内容简介 </el-row>
            </el-col>
          </el-row>
          <br />
          <el-row class="goodBlog" style="height: 70px">
            <el-col>
              <el-row
                style="
                  height: 40px;
                  font-size: 20px;
                  padding-left: 10px;
                  padding-top: 7px;
                "
              >
                博客1
              </el-row>
              <el-row style="padding-left: 10px"> 博客内容简介 </el-row>
            </el-col>
          </el-row>
          <br />
          <el-row justify="center">
            <el-pagination
              small
              background
              layout="prev, pager, next"
              :total="50"
              class="mt-4"
            />
          </el-row>
        </el-col>
      </el-row>
    </div>
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

.goodCourse {
  border-radius: 5px;
  background-color: aliceblue;
  box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);
}
</style>
