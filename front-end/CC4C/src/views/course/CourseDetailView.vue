<template>
    <div class="courseDetail">
        <div class="course" style="background-color:rgb(244,246,248)">
            <el-row justify="center" style="padding-top:20px;">
                <el-col :span="18"
                    style="height:1000px;  padding:0px 30px 0px 30px; background-color: white;border: solid 1px rgb(220,223,230); margin: 0px 10px 0px 20px;">
                    <md-editor v-model="text" :editorId="state.id" :previewOnly="true" style="height: auto" />
                </el-col>

                <el-col :span="4">
                    <el-row>
                        <el-col
                            style="height:auto; padding: 0px 30px 30px 30px; margin: 0px 0px 30px 0px;  background-color: white;border: solid 1px rgb(220,223,230);">
                            <h2>文章目录</h2>
                            <md-catalog :editorId="state.id" :scroll-element="scrollElement" />
                        </el-col>
                    </el-row>

                    <!-- 按钮 -->
                    <el-row >
                        <el-col
                            style="height:100px; padding: 20px 0px 30px 0px; background-color: white;border: solid 1px rgb(220,223,230);text-align: center;">

                            <el-button style="height: 50px; width:50px" @click="starCourse()" v-show="this.$store.state.user.id != ''">
                                <el-image v-show='isFavor != true' src="src\assets\button\Star.png"
                                    style="height: 30px; width:30px"></el-image>
                                <el-image v-show='isFavor == true' src="src\assets\button\StarFilled.png"
                                    style="height: 30px; width:30px"></el-image>
                            </el-button>

                            <el-button style="height: 50px; width:50px" @click="isCommentOpen = !isCommentOpen">
                                <el-image v-show='isCommentOpen != true' src="src\assets\button\Comment.png"
                                    style="height: 30px; width:30px"></el-image>
                                <el-image v-show='isCommentOpen == true' src="src\assets\button\CommentFilled.png"
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
                    <el-row v-show="this.$store.state.user.id != ''"
                        style="margin:0px 0px 0px 0px; padding:15px 10px 10px 10px;border-radius: 10px; background-color:antiquewhite;">
                        <el-col :span="2">
                            <el-image :src="this.$store.state.user.avatar"
                                style="height: 10px width: 10px; border-radius: 50%">
                            </el-image>
                        </el-col>
                        <el-col :span="22">
                            <el-row style=" padding: 0px 30px 0px 30px;">
                                <el-input :rows="10" type="textarea" v-model="commentText" maxlength="1000"
                                    show-word-limit resize="none">
                                </el-input>
                                <el-button size="small"
                                    style="background-color: rgb(252,85,49); color:white; margin: 5px 0px 0px 0px;"
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
                            <el-image src="src\assets\kun.png" style="height: 10px width: 10px; border-radius:50%">
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
                                <el-input :rows="4" type="textarea" v-model="replyText" maxlength="1000" show-word-limit
                                    resize="none">
                                </el-input>
                                <el-button size="small"
                                    style="background-color: rgb(252,85,49); color:white; margin: 5px 0px 0px 0px;"
                                    @click="reply(comment.commentId)">发布</el-button>
                            </el-row>

                            <!-- 子评论 -->
                            <el-row v-for="subcomment in comment.subCommentList"
                                style="margin:0px 0px 0px 0px; padding:10px 0px 0px 0px;border-radius: 10px; background-color:antiquewhite; ">
                                <el-col :span="2">
                                    <el-image src="src\assets\kun.png"
                                        style="height: 10px width: 10px; border-radius:50%">
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

                        <el-col :span="4" style="text-align: center;" v-show="this.$store.state.user.id != ''">
                            <el-button size="small" style="background-color: rgb(252,85,49); color: white"
                                @click="replyOpen(key)">
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
import { useRoute } from 'vue-router';
import store from '@/store'

const route = useRoute();

// md-editor
var text = ref("");
const MdCatalog = MdEditor.MdCatalog;
const scrollElement = document.documentElement;
const state = reactive({
    text: '标题',
    id: 'my-editor',
    catalogList: []
});

// 收藏课程
function starCourse() {
    if (this.isFavor != true) {
        axios.get("http://localhost:4080/courses/star/" + store.state.user.id + '/' + this.courseData.courseId).then((resp) => {
        });
        ElMessage({
            message: '收藏成功',
            type: 'success',
        })
        this.isFavor = true;
    }
    else {
        axios.delete("http://localhost:4080/courses/deleteFavor/" + store.state.user.id + '/' + this.courseData.courseId).then((resp) => {
        });
        ElMessage({
            message: '取消收藏',
            type: 'success',
        })
        this.isFavor = false;
    }
}

// 评论
const commentText = ref('发表评论');
const isCommentOpen = ref(false);
function comment() {
    axios.post('http://localhost:4080/comments/course', {
        userId: store.state.user.id,
        content: commentText.value,
        courseId: this.courseData.courseId,
    }).then((resp) => {
        alert('评论成功')
        window.location.reload();
    })
}
// 回复
const replyText = ref('发表回复');
const isReplyOpen = ref(-1);
function replyOpen(key) {
    if (isReplyOpen.value == -1) {

        isReplyOpen.value = key;
    }
    else {
        isReplyOpen.value = -1;
    }
}
function reply(fatherId) {
    axios.post('http://localhost:4080/comments/indirect', {
        userId: store.state.user.id,
        content: replyText.value,
        fatherId: fatherId
    }).then((resp) => {
        alert('回复成功')
    })
}

axios.defaults.withCredentials = true;//这样全局设置允许


// 用于接收课程信息
var courseData = reactive({});
// 用户课程收藏状态
var isFavor = ref(false);
// 用于保存评论列表
var commentList = reactive([]);
// 获取 课程数据 课程收藏状态
axios.get("http://localhost:4080/courses/" + route.query.courseName).then((resp) => {
    courseData = resp.data.data;
    text.value = courseData.description;

    axios.get("http://localhost:4080/courses/ifFavor/" + store.state.user.id + '/' + courseData.courseId).then((resp) => {
        isFavor.value = resp.data.data;
    });

    axios.get("http://localhost:4080/comments/course/" + courseData.courseId).then((resp) => {
        commentList = resp.data.data;
    });
})


</script>
  
<style scoped>
a {
    text-decoration: none;
}

/* 输入框样式 */


/* .el-input__inner {
      background-color:rgb(244, 246, 248);
      height: 200px;
  } */
</style>