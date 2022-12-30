<template>
    <div style="background-color: rgb(244,246,248);">
        <el-row justify="center">
            <el-col :span="11"
                style="padding: 0px 30px 0px 30px; margin:30px 20px 0px 20px; background-color: white; height:900px">
                <h2>所有课程</h2>
                <el-table :data="courseList" border height="800px" style="width: 100%">
                    <el-table-column prop="courseId" label="ID" width="60" />
                    <el-table-column prop="languageName" label="语言" width="80" />
                    <el-table-column prop="courseName" label="名称" />
                    <el-table-column prop="favorsNum" label="收藏数" width="80" />
                    <!-- <el-table-column fixed="right" label="操作"  width="120">
                        <template #default>
                            <el-button link type="primary" size="small" @click="deleteCourse()">删除</el-button>
                            <el-button link type="primary" size="small" @click="editCourse()">编辑</el-button>
                        </template>
                    </el-table-column> -->
                </el-table>

            </el-col>
            <el-col :span="11"
                style="padding: 0px 30px 0px 30px; margin:30px 20px 0px 20px; background-color: white; height:700px">
                <h2>所有博客</h2>
                <el-table :data="blogList" border height="600px" style="width: 100%">
                    <el-table-column prop="blogId" label="ID" width="200" />
                    <!-- <el-table-column prop="courseLanguage" label="语言" width="60" /> -->
                    <el-table-column prop="title" label="名称" />
                </el-table>
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import axios from 'axios';



// var courseList = reactive([
//     {
//         courseName: '黑马程序员',
//         courseId: '123',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '尚硅谷尚硅谷尚硅谷',
//         courseId: '124',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '黑马程序员',
//         courseId: '123',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '尚硅谷尚硅谷尚硅谷',
//         courseId: '124',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '黑马程序员',
//         courseId: '123',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '尚硅谷尚硅谷尚硅谷',
//         courseId: '124',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '黑马程序员',
//         courseId: '123',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '尚硅谷尚硅谷尚硅谷',
//         courseId: '124',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '黑马程序员',
//         courseId: '123',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '尚硅谷尚硅谷尚硅谷',
//         courseId: '124',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '黑马程序员',
//         courseId: '123',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '尚硅谷尚硅谷尚硅谷',
//         courseId: '124',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '黑马程序员',
//         courseId: '123',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },    {
//         courseName: '尚硅谷尚硅谷尚硅谷',
//         courseId: '124',
//         courseLanguage: 'java',
//         courseModule: 'java基础',
//     },
// ]);
var blogList = ref([]);
var courseList = ref([]);
// 获得所有课程
axios.get("http://localhost:4080/courses/home").then((resp) => {
    courseList.value = resp.data.data;
});

// 获得所有博客
axios.get("http://localhost:4080/blogs/all").then((resp) => {
    blogList.value = resp.data.data;
});

</script>

<style scoped>

</style>