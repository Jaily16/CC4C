<template>
    <div class="course" style="background-color:rgb(244,246,248)">
        <el-row justify="center" style="padding-top:20px;">
            <el-col :span="10">
                <el-row>
                    <el-col
                        style="height:auto; padding: 0px 30px 30px 30px; margin: 0px 0px 30px 0px;  background-color: white;">
                        <h2>审核博客</h2>
                        <el-table :data="blogList" border height="300px" @row-click="flyTo" style="width: 100%">
                            <el-table-column prop="blogId" label="ID" width="200" />
                            <el-table-column prop="title" label="名称" />
                            <!-- <el-table-column label="操作" width="90">
                                <el-button @click="flyTo(blogId)">
                                    审核
                                </el-button>
                            </el-table-column>> -->
                        </el-table>
                    </el-col>
                </el-row>

                <!-- 三个按钮 -->
                <el-row v-show="canOperate == true">
                    <el-col
                        style="height:auto; padding: 20px 0px 30px 0px; background-color: white; text-align: center;">
                        <el-button type="danger" @click="deny()">不通过
                        </el-button>
                        <el-button type="primary" @click="approve()">通过
                        </el-button>
                    </el-col>
                </el-row>
            </el-col>

            <el-col :span="12"
                style="height:1000px; padding:10px 50px 10px 50px; background-color: white; margin: 0px 10px 0px 20px;">
                <el-scrollbar height="1000px">
                    <md-editor v-model="text" :editorId="state.id" :previewOnly="true" style="height: auto" />
                </el-scrollbar>
            </el-col>


        </el-row>
    </div>


</template>
  
<script setup>
// 引入md-editor-v3
import { ref, reactive } from "vue";
import MdEditor from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import axios from "axios";

const route = useRoute();

var text = ref("## 博客内容 \n 请在左侧选择您要审核的博客");

const MdCatalog = MdEditor.MdCatalog;

const state = reactive({
    text: '标题',
    id: 'my-editor',
    catalogList: []
});
const scrollElement = document.documentElement;


// 保存待审核博客列表
var blogList = ref([
]);

// 正在审核博客的ID
var checkedBlogId = ref('')

// 获得所有待审核的博客
axios.get("http://localhost:4080/blogs/examine").then((resp) => {
    blogList.value = resp.data.data;
});

// 展示正在审核的博客的内容
function flyTo(row) {
    canOperate.value = true;
    // console.log(row.blogId)
    checkedBlogId.value = row.blogId;
    axios.get("http://localhost:4080/blogs/" + checkedBlogId.value).then((resp) => {
        text.value = resp.data.data.content;
    });
}

// 审核通过
function approve() {
    axios.put("http://localhost:4080/blogs/approve/" + checkedBlogId.value).then((resp) => {
        alert("审核通过！")
        window.location.reload();
    });
}
// 审核不通过
function deny() {
    axios.put("http://localhost:4080/blogs/deny/" + checkedBlogId.value).then((resp) => {
        alert("审核不通过！")
        window.location.reload();

    });

}

// 是否能审核
var canOperate = ref(false)
</script>
  
<style>
a {
    text-decoration: none;
}
</style>