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
                                    show-word-limit resize="none" placeholder="发表评论">
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
                                    <el-image :src="assets.defaultAvatar"
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
import { assets } from '@/assets';

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
async function starCourse() {
    if (!courseData.value.courseId) {
        ElMessage.warning('课程尚未加载完成');
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
        ElMessage.success('回复成功');
    } catch (error) {
        ElMessage.error('回复失败，请稍后重试');
        console.error(error);
    }
}

axios.defaults.withCredentials = true;//这样全局设置允许


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

// 获取 课程数据 课程收藏状态
async function loadCourse() {
    try {
        const courseName = String(route.query.courseName || '');
        if (!courseName) {
            ElMessage.error('缺少课程名称');
            return;
        }
        const resp = await axios.get("http://localhost:4080/courses/" + encodeURIComponent(courseName));
        if (!resp.data.data || !resp.data.data.courseId) {
            ElMessage.error(resp.data.msg || '课程加载失败');
            return;
        }
        courseData.value = resp.data.data;
        text.value = courseData.value.description || '';

        const requests = [loadComments()];
        if (store.state.user.id) {
            requests.push(
                axios.get("http://localhost:4080/courses/ifFavor/" + store.state.user.id + '/' + courseData.value.courseId)
                    .then((favorResp) => {
                        isFavor.value = favorResp.data.data === true;
                    })
            );
        }
        await Promise.all(requests);
    } catch (error) {
        ElMessage.error('课程加载失败，请稍后重试');
        console.error(error);
    }
}

loadCourse();


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
