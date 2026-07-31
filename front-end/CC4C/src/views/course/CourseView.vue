<template>
  <div class="courseDetail">
    <div class="course" style="background-color:rgb(244,246,248)">
      <!-- 语言选择 -->
      <el-row justify="center">
        <el-col :span="2">
          <div
            style="height:80px;background-color: white;padding:5px 0px 0px 0px; margin: 10px 00px 10px 0px;border: solid 1px rgb(220,223,230);border-radius: 5px; ; text-align: center;">
            <h3>选择语言</h3>
          </div>
        </el-col>
        <el-col :span="20">
          <div
            style="height:80px;background-color: white;padding:5px 0px 0px 20px; margin: 10px  0px 10px 0px;border: solid 1px rgb(220,223,230);border-radius: 5px; ;">
            <el-radio-group v-model="mainLang" size="small">
              <el-radio-button v-for="lang in langs" :label="lang.name" @click="selectLang(lang)">
                <div style="height: 60px; width: 60px; ">
                  <el-image :src="lang.icon" />
                </div>
              </el-radio-button>
            </el-radio-group>
          </div>
        </el-col>
        <!-- <el-col :span="4">
          <div
            style="height:80px;background-color: white;padding:5px 0px 0px 10px; margin: 10px 0px 0px 0px ;border: solid 1px rgb(220,223,230);">
            <el-input v-model="searchInfo" :prefix-icon="Search" placeholder="搜索" style="padding:20px 20px 0px 20px" />
            <el-button @click="search()">
              搜索
            </el-button>
          </div>
        </el-col> -->
      </el-row>
      <!-- 课程展示 -->
      <el-row justify="center">
        <!-- 课程目录 -->
        <el-col :span="4"
          style="height:1000px;  background-color: white;border-radius: 5px; border: solid 1px rgb(220,223,230); margin: 0px 10px 0px 20px;">
          <h2 style="padding-left: 20px; ">课程目录</h2>
          <el-menu>
            <el-sub-menu v-for="(courseModule, key1) in courseModules" :index="String(key1)">
              <template #title>
                <span>{{ courseModule.moduleName }}</span>
              </template>
              <div>
                <el-menu-item v-for="(course, key2) in courseModule.courseList" :index="key1 + '-' + key2"
                  @click="flyTo(course)">
                  {{ course }}
                </el-menu-item>
              </div>
            </el-sub-menu>
          </el-menu>
        </el-col>
        <!-- 课程内容 -->
        <el-col :span="14"
          style="height:auto;  padding:0px 30px 0px 30px; background-color: white;border-radius: 5px;border: solid 1px rgb(220,223,230); margin: 0px 10px 0px 10px;">
          <md-editor v-model="text" :editorId="state.id" :previewOnly="true" style="height: auto" />
        </el-col>
        <!-- 课程目录 -->
        <el-col :span="4">
          <el-row>
            <el-col
              style="height:auto; padding: 0px 30px 30px 30px; margin: 0px 0px 30px 0px;  background-color: white;border: solid 1px rgb(220,223,230);border-radius: 5px;;">
              <h2>文章目录</h2>
              <md-catalog :editorId="state.id" :scroll-element="scrollElement" />
            </el-col>
          </el-row>
          <!-- 三个按钮 -->
          <el-row v-show="canStar == true">
            <el-col
              style="height:100px; padding: 20px 0px 30px 0px; background-color: white;border: solid 1px rgb(220,223,230); text-align: center;">
              <!-- <el-button style="height: 50px; width:50px" @click="thumbUpCourse()">
                <el-image v-show='isThumbUp != true' src="src\assets\ThumbUp.png"
                  style="height: 30px; width:30px"></el-image>
                <el-image v-show='isThumbUp == true' src="src\assets\ThumbUpFilled.png"
                  style="height: 30px; width:30px"></el-image>
              </el-button> -->


              <el-button style="height: 50px; width:50px" @click="starCourse()">
                <el-image v-show='isFavor != true' :src="assets.actions.star"
                  style="height: 30px; width:30px"></el-image>
                <el-image v-show='isFavor == true' :src="assets.actions.starFilled"
                  style="height: 30px; width:30px"></el-image>
              </el-button>

              <el-button style="height: 50px; width:50px" @click="isCommentOpen = !isCommentOpen">
                <el-image v-show='isCommentOpen != true' :src="assets.actions.comment"
                  style="height: 30px; width:30px"></el-image>
                <el-image v-show='isCommentOpen == true' :src="assets.actions.commentFilled"
                  style="height: 30px; width:30px"></el-image>
              </el-button>

            </el-col>
          </el-row>

        </el-col>
        <!-- 评论区 -->
        <el-drawer v-model="isCommentOpen">
          <template #header="{ titleId, titleClass }">
            <h4 :id="titleId" :class="titleClass" style="font-size:30px">评论</h4>
          </template>

          <!-- 发布评论框 -->
          <el-row
            style="margin:0px 0px 0px 0px; padding:15px 10px 10px 10px;border-radius: 10px; background-color:antiquewhite;">
            <el-col :span="2">
              <el-image :src="this.$store.state.user.avatar" style="height: 10px width: 10px; border-radius: 50%">
              </el-image>
            </el-col>
            <el-col :span="22">
              <el-row style=" padding: 0px 30px 0px 30px;">
                <el-input :rows="10" type="textarea" v-model="commentText" maxlength="1000" show-word-limit
                  resize="none" placeholder="发表评论">
                </el-input>
                <el-button size="small" style="background-color: rgb(252,85,49); color:white; margin: 5px 0px 0px 0px;"
                  @click="comment()">
                  发布
                </el-button>
              </el-row>
            </el-col>
          </el-row>

          <!-- 回复列表 -->
          <el-row v-for="(comment, key) in commentList"
            style="margin:10px 0px 0px 0px; padding:10px 10px 10px 10px;border-radius: 10px; background-color:antiquewhite;">
            <el-col :span="2">
              <el-image :src="assets.defaultAvatar" style="height: 10px width: 10px; border-radius:50%">
              </el-image>
            </el-col>
            <el-col :span="18" style="padding: 0px 0px 0px 20px;">
              <el-row style="margin: 0px 0px 5px 0px; font-size:medium; font-weight: bolder;">
                <span>{{ comment.userName }}</span>
              </el-row>
              <el-row>
                <span>{{ comment.content }}</span>
              </el-row>

              <!-- 子评论回复框 -->
              <el-row v-show="isReplyOpen == key" style="margin: 10px 0px 0px 0px; ">
                <el-input :rows="4" type="textarea" v-model="replyText" maxlength="1000" show-word-limit resize="none">
                </el-input>
                <el-button size="small" style="background-color: rgb(252,85,49); color:white; margin: 5px 0px 0px 0px;"
                  @click="reply(comment.commentId)">发布</el-button>
              </el-row>

              <!-- 子评论 -->
              <el-row v-for="subcomment in comment.subCommentList"
                style="margin:0px 0px 0px 0px; padding:10px 0px 0px 0px;border-radius: 10px; background-color:antiquewhite; ">
                <el-col :span="2">
                  <el-image :src="assets.defaultAvatar" style="height: 10px width: 10px; border-radius:50%">
                  </el-image>
                </el-col>
                <el-col :span="22" style="padding: 0px 0px 0px 20px;">
                  <el-row style="margin: 0px 0px 0px 0px; font-size:small; font-weight: bolder;">
                    <span>{{ subcomment.userName }}</span>
                  </el-row>
                  <el-row style=" font-size:small;">
                    <span>{{ subcomment.content }}</span>
                  </el-row>
                </el-col>
              </el-row>
            </el-col>

            <el-col :span="4" style="text-align: center;">
              <el-button size="small" style="background-color: rgb(252,85,49); color: white" @click="replyOpen(key)">
                回复
              </el-button>
            </el-col>
          </el-row>
        </el-drawer>

      </el-row>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import axios from 'axios';
import MdEditor from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import { ElMessage } from 'element-plus';
import store from '@/store';
import { assets } from '@/assets';


// 验证用户的登陆状态
axios.defaults.withCredentials = true;//这样全局设置允许
axios.get("http://localhost:4080/users/verify").then((resp) => {
  if (resp.data.data == false) {
    alert(resp.data.msg);
    window.location.href = "/login";
    return;
  }
});


// 当前选择的语言
const mainLang = ref('java');
// 语言选择
const langs = [
  {
    no: '1',
    name: 'java',
    icon: assets.languageIcons.java
  },
  {
    no: '2',
    name: 'c++',
    icon: assets.languageIcons['c++']
  },
  {
    no: '3',
    name: 'python',
    icon: assets.languageIcons.python
  },
  {
    no: '4',
    name: 'c',
    icon: assets.languageIcons.c
  }]
// 选择语言
function selectLang(lang) {
  axios.get("http://localhost:4080/courses/recommend/" + lang.no + '/' + store.state.user.major).then((resp) => {
    courseModules.value = resp.data.data;
  });
}

// md-editor
var text = ref("## 这是课程资源 \n 您可以在左侧的菜单栏选择您想要学习的课程");
const MdCatalog = MdEditor.MdCatalog;
const scrollElement = document.documentElement;
const state = reactive({
  text: '标题',
  id: 'my-editor',
  catalogList: []
});
// const isThumbUp = ref(false);

// 收藏课程
const canStar = ref(false);
async function starCourse() {
  if (!courseData.value.courseId) {
    ElMessage.warning('请先选择课程');
    return;
  }
  try {
    const resp = isFavor.value
      ? await axios.delete("http://localhost:4080/courses/deleteFavor/" + store.state.user.id + '/' + courseData.value.courseId)
      : await axios.get("http://localhost:4080/courses/star/" + store.state.user.id + '/' + courseData.value.courseId);
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '收藏操作失败');
      return;
    }
    isFavor.value = !isFavor.value;
    ElMessage.success(isFavor.value ? '收藏成功' : '取消收藏成功');
  } catch (error) {
    ElMessage.error('收藏操作失败，请稍后重试');
    console.error(error);
  }
}


// 评论
const commentText = ref('');
const isCommentOpen = ref(false);
async function comment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('评论内容不能为空');
    return;
  }
  try {
    const resp = await axios.post('http://localhost:4080/comments/course', {
      userId: store.state.user.id,
      content: commentText.value.trim(),
      courseId: courseData.value.courseId,
    });
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '评论失败');
      return;
    }
    commentText.value = '';
    await loadComments();
    isCommentOpen.value = true;
    ElMessage.success('评论成功');
  } catch (error) {
    ElMessage.error('评论失败，请稍后重试');
    console.error(error);
  }
}
// 回复
const replyText = ref('');
const isReplyOpen = ref(-1);
function replyOpen(key) {
  if (isReplyOpen.value == -1) {
    isReplyOpen.value = key;
  }
  else {
    isReplyOpen.value = -1;
  }
}
async function reply(fatherId) {
  if (!replyText.value.trim()) {
    ElMessage.warning('回复内容不能为空');
    return;
  }
  try {
    const resp = await axios.post('http://localhost:4080/comments/indirect', {
      userId: store.state.user.id,
      content: replyText.value.trim(),
      fatherId: fatherId
    });
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '回复失败');
      return;
    }
    replyText.value = '';
    isReplyOpen.value = -1;
    await loadComments();
    isCommentOpen.value = true;
    ElMessage.success('回复成功');
  } catch (error) {
    ElMessage.error('回复失败，请稍后重试');
    console.error(error);
  }
}

// 课程模块数据
const courseModules = ref([]);
// 获取 课程模块数据
axios.get("http://localhost:4080/courses/recommend/" + '1' + '/' + store.state.user.major).then((resp) => {
  courseModules.value = resp.data.data;
});

// 用于接收课程信息
const courseData = ref({});
// 用户课程收藏状态
const isFavor = ref(false);
// 用于保存评论列表
const commentList = ref([]);

async function loadComments() {
  if (!courseData.value.courseId) {
    commentList.value = [];
    return;
  }
  const resp = await axios.get("http://localhost:4080/comments/course/" + courseData.value.courseId);
  commentList.value = resp.data.data || [];
}

// 获取 课程数据 收藏状态 评论区
async function flyTo(courseName) {
  try {
    const courseResp = await axios.get("http://localhost:4080/courses/" + encodeURIComponent(courseName));
    if (!courseResp.data.data || !courseResp.data.data.courseId) {
      ElMessage.error(courseResp.data.msg || '课程加载失败');
      return;
    }
    courseData.value = courseResp.data.data;
    text.value = courseData.value.description || '';
    canStar.value = true;

    const [favorResp] = await Promise.all([
      axios.get("http://localhost:4080/courses/ifFavor/" + store.state.user.id + '/' + courseData.value.courseId),
      loadComments()
    ]);
    isFavor.value = favorResp.data.data === true;
  } catch (error) {
    ElMessage.error('课程加载失败，请稍后重试');
    console.error(error);
  }
}
</script>

<style scoped>
a {
  text-decoration: none;
}

.el-menu-item {

  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  width: 90%;
}
</style>
