<template>
  <div class="home">
    <el-row justify="center" style="background-color: white">
      <el-col :span="20">
        <div>
          <h1>语言介绍</h1>
          <el-carousel type="card" height="250px">
            <el-carousel-item v-for="item in 4" :key="item">
              <h3 class="small justify-center" text="2xl">{{ item }}</h3>
            </el-carousel-item>
          </el-carousel>
        </div>

        <div>
          <h1>课程推荐</h1>
          <el-row justify="center" :gutter="30">
            <el-col :span="6">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px">
                  {{ courses[0].name }}
                  <br />
                  {{ courses[0].description }}
                </el-row>
              </div>
            </el-col>

            <el-col :span="6">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px">
                  {{ courses[1].name }}
                  <br />
                  {{ courses[1].description }}
                </el-row>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px">
                  {{ courses[2].name }}
                  <br />
                  {{ courses[2].description }}
                </el-row>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="goodCourse">
                <el-row style="padding: 20px 30px 10px 30px">
                  <el-image style="width: 200px; height: 100px" :src="url" />
                </el-row>
                <el-row style="padding: 10px 10px">
                  {{ courses[3].name }}
                  <br />
                  {{ courses[3].description }}
                </el-row>
              </div>
            </el-col>
          </el-row>
        </div>

        <div>
          <h1>博客推荐</h1>

          <el-row class="goodBlog" style="height: 120px">
            <el-col :span="4" style="padding: 5px 5px 5px 5px">
              <el-image
                style="width: 200px; height: 100px; margin: 5px"
                :src="url"
              />
            </el-col>
            <el-col :span="20">
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

          <el-row class="goodBlog" style="height: 120px">
            <el-col :span="4" style="padding: 5px 5px 5px 5px">
              <el-image
                style="width: 200px; height: 100px; margin: 5px"
                :src="url"
              />
            </el-col>
            <el-col :span="20">
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

          <el-row class="goodBlog" style="height: 120px">
            <el-col :span="4" style="padding: 5px 5px 5px 5px">
              <el-image
                style="width: 200px; height: 100px; margin: 5px"
                :src="url"
              />
            </el-col>
            <el-col :span="20">
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

          <el-row class="goodBlog" style="height: 120px">
            <el-col :span="4" style="padding: 5px 5px 5px 5px">
              <el-image
                style="width: 200px; height: 100px; margin: 5px"
                :src="url"
              />
            </el-col>
            <el-col :span="20">
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
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<!-- <script lang="ts" setup> -->
<script>
import axios from "axios";
export default {
  data() {
    return {
      url: "src/assets/QQ20221202185121.jpg",
      courses: [
        {
          name: "课程1",
          description: "课程描述1",
        },
        {
          name: "课程2",
          description: "课程描述2",
        },
        {
          name: "课程3",
          description: "课程描述3",
        },
        {
          name: "课程4",
          description: "课程描述4",
        },
      ],
      msg: [],
    };
  },
  created() {
    axios.get("http://localhost:8888/course/findAll").then((resp) => {
      console.log(resp.data);
      _this.courses = resp.data;
    });
    // axios.get("http://localhost:4080/test").then((resp) => {
    //   console.log(resp.data);
    //   this.msg = resp.data;
    // });
  },
};
// const url =
//     // 'https://fuss10.elemecdn.com/e/5d/4a731a90594a4af544c0c25941171jpeg.jpeg'
//     'src/assets/QQ20221202185121.jpg'
</script>
<style>
/* 走马灯 */
.el-carousel__item h3 {
  color: #475669;
  opacity: 0.75;
  line-height: 150px;
  margin: 0;
  text-align: center;
}

.el-carousel__item:nth-child(2n) {
  background-color: #99a9bf;
}

.el-carousel__item:nth-child(2n + 1) {
  background-color: #d3dce6;
}

.goodCourse {
  border-radius: 5px;
  background-color: aliceblue;
  box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);
}

.goodBlog {
  border-radius: 5px;
  margin-bottom: 5px;
  background-color: aliceblue;
  box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);
}
</style>