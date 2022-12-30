<template>
  <div class="write" style="background-color: RGB(244,246,248);">
    <el-row justify="center">
      <el-col :span="22"
        style="height:auto; background-color: white; padding: 0px 50px 0px 50px; margin: 20px 0px 0px 0px; border-radius: 5px; box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
        <div style="margin: 0px 0px 30px 0px">
          <h2>撰写博客</h2>
        </div>
        <el-row gutter="50">
          <el-col :span="18">
            <h3>文章标题</h3>
            <el-input v-model="title">
            </el-input>
          </el-col>
          <el-col :span="6">
            <h3>文章语言</h3>
            <el-checkbox-group v-model="langList">
              <el-checkbox label="1">java</el-checkbox>
              <el-checkbox label="4">c</el-checkbox>
              <el-checkbox label="2">c++</el-checkbox>
              <el-checkbox label="3">python</el-checkbox>
            </el-checkbox-group>
          </el-col>
        </el-row>

        <h3>文章内容</h3>
        <div>
          <md-editor :toolbarsExclude="['link', 'mermaid', 'katex', 'github']" v-model="text" @onSave="codeSave"
            @on-upload-img="onUploadImg" style="height: 650px" />
        </div>
        <div style="margin:10px 0px 0px 0px">
          <el-row justify="end" style="padding:0px 0px 30px 0px">
            <el-button type="success" size="large" @click="draft()">保存到草稿箱</el-button>
            <el-button type="primary" size="large" @click="publish()">发布</el-button>
          </el-row>
        </div>

      </el-col>
    </el-row>
  </div>
</template>

<script lang='ts' setup>
// 引入md-editor-v3
import { ref, } from "vue";
import MdEditor from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import axios from "axios";
import store from '@/store';

import { ElMessage, } from "element-plus";

// 验证用户的登陆状态
axios.defaults.withCredentials = true;//这样全局设置允许
axios.get("http://localhost:4080/users/verify").then((resp) => {
  if (resp.data.data == false) {
    alert(resp.data.msg);
    window.location.href = "http://localhost:5173/login";
    return;
  }
});

// 保存提示
function codeSave() {
  ElMessage({ type: 'success', message: "已保存" });
};
// 文章标题 文章语言 文章内容
var title = ref('');
var langList = ref([]);
var text = ref('');

// 发布文章
function publish() {
  if (title.value == '') {
    ElMessage({ type: 'warning', message: "文章标题不能为空" });
  }
  else if (langList.value.length == 0) {
    ElMessage({ type: 'warning', message: "文章语言不能为空" });
  }
  else if (text.value == '') {
    ElMessage({ type: 'warning', message: "文章内容不能为空" });
  }
  else {
    axios.post("http://localhost:4080/blogs/submit", {
      writerId: store.state.user.id,
      title: title.value,
      languageList: langList.value,
      content: text.value
    }).then((resp) => {
      // ElMessage({ type: 'success', message: "成功发布" });
      alert('成功发布');
      window.location.reload();

    });
  }
}

// 保存草稿
function draft() {
  if (text.value == '') {
    ElMessage({ type: 'warning', message: "文章内容不能为空" });
  }
  else {
    axios.post("http://localhost:4080/blogs/draft", {
      userId: store.state.user.id,
      content: text.value
    }).then((resp) => {
      ElMessage({ type: 'success', message: "成功保存至草稿箱" });

    });
  }
}

// 上传图片
const onUploadImg = async (files, callback) => {
  const res = await Promise.all(
    files.map((file) => {
      return new Promise((rev, rej) => {
        const form = new FormData();
        form.append('file', file);

        axios.post('http://localhost:4080/blogs/uploadImg', form, {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        })
          .then((res) => rev(res))
          .catch((error) => rej(error));
      });
    })
  );

  callback(res.map((item) => item.data.url));
};

axios.defaults.withCredentials = true;//这样全局设置允许

// 读取草稿
axios.get("http://localhost:4080/blogs/draft/" + store.state.user.id).then((resp) => {
  if (resp.data.data != null) {
    text.value = resp.data.data;
  }
});

</script>

<style scoped>

</style>

