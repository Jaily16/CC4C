<template>
    <div class="allCourse" style="background-color:rgb(244,246,248); ">
        <el-row justify="center">
            <el-col :span="2">
                <div
                    style="height:80px;background-color: white;padding:5px 0px 0px 0px; margin: 10px 00px 5px 0px ;border: solid 1px rgb(220,223,230); border-top-left-radius: 5px; border-bottom-left-radius: 5px; text-align: center;">
                    <h3>选择语言</h3>
                </div>
            </el-col>
            <el-col :span="14">
                <div
                    style="height:80px;background-color: white;padding:5px 0px 0px 20px; margin: 10px  0px 5px 0px ;border: solid 1px rgb(220,223,230);">
                    <el-radio-group v-model="mainLang" size="small">
                        <el-radio-button v-for="lang in langs" :label="lang.name" @click="selectLang(lang.name)">
                            <div style="height: 60px; width: 60px; ">
                                <el-image :src="lang.icon" />
                            </div>
                        </el-radio-button>
                    </el-radio-group>
                </div>
            </el-col>
            <el-col :span="6">
                <div
                    style="height:80px;background-color: white;padding:5px 0px 0px 10px; margin: 10px 0px 0px 0px ;border: solid 1px rgb(220,223,230); border-top-right-radius: 5px; border-bottom-right-radius: 5px;">
                    <el-input v-model="searchInfo" :prefix-icon="Search" placeholder="搜索全部课程" :change="search()"
                        style="padding:20px 20px 0px 20px" />
                </div>
            </el-col>
        </el-row>

        <el-row justify="center" style="padding-top:10px;">

            <el-col :span="22"
                style="background-color: white; padding: 0px 0px 0px 20px;  height: 1000px; border: solid 1px lightgray;border-radius: 5px;">
                <h2>所有课程</h2>
                <el-scrollbar height="900px">

                    <el-row style="padding: 10px 20px 0px 20px;">

                        <el-col v-for="Course in allCourses" :span="4" style="padding: 0px 10px 10px 10px;"
                            @dblclick="flyTo(Course.courseName)">
                            <div
                                style="  border-radius: 5px;background-color: aliceblue;box-shadow: 2px 2px 2px 1px rgba(0, 0, 0, 0.2);">
                                <el-row style="padding: 20px 30px 10px 30px; ">
                                    <el-image v-if="Course.languageName == 'java'" style="width: 200px; height: 100px"
                                        src="src\assets\LangImg\JavaImg.png" />
                                    <el-image v-if="Course.languageName == 'c'" style="width: 200px; height: 100px"
                                        src="src\assets\LangImg\CImg.png" />
                                    <el-image v-if="Course.languageName == 'c++'" style="width: 200px; height: 100px"
                                        src="src\assets\LangImg\C++Img.png" />
                                    <el-image v-if="Course.languageName == 'python'" style="width: 200px; height: 100px"
                                        src="src\assets\LangImg\PythonImg.png" />
                                </el-row>
                                <el-row style="padding: 10px 10px; font-size: 10px;">
                                    {{ Course.courseName }}
                                </el-row>
                            </div>
                        </el-col>
                    </el-row>
                </el-scrollbar>
            </el-col>

        </el-row>
    </div>

</template>
  
<script setup lang="ts">
import { ref, reactive } from 'vue';
import axios from 'axios';
import "md-editor-v3/lib/style.css";
import { Search } from "@element-plus/icons-vue";
import { useRouter } from 'vue-router';

const router = useRouter();

const mainLang = ref('java');

const langs = [
    {
        name: 'java',
        icon: 'src/assets/LangImg/JAVA.png'
    },
    {
        name: 'c++',
        icon: 'src/assets/LangImg/C++.png'
    }, 
    {
        name: 'python',
        icon: 'src/assets/LangImg/PYTHON.png'
    },
    {
        name: 'c',
        icon: 'src/assets/LangImg/C.png'
    }, 
]

var allCourses = ref([]);
function flyTo(courseName) {
    router.push({ path: '/courseDetail', query: { courseName: courseName } })
}

// 初始化课程列表
axios.get("http://localhost:4080/courses/search/" + 'java').then((resp) => {
    allCourses.value = resp.data.data;
});

// 搜索对应语言
function selectLang(langName) {
    axios.get("http://localhost:4080/courses/language/" + langName).then((resp) => {
        allCourses.value = resp.data.data;
    });
}

// 搜索
var searchInfo = ref('');
function search() {
    if (searchInfo.value == '') {
        return;
    }
    axios.get("http://localhost:4080/courses/search/" + searchInfo.value).then((resp) => {
        allCourses.value = resp.data.data;
    });
}

</script>
  
<style scoped>
a {
    text-decoration: none;
}
</style>