<template>
    <div style="background-color: rgb(244,246,248);">
        <el-row justify="center">
            <el-col :span="8" style="margin: 80px 0px 500px 0px; ">
                <div
                    style="background-color: white; padding: 0px 20px 30px 20px; border-radius: 10px;box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
                    <el-row>
                        <el-col :span="19">
                            <h1>注册</h1>
                            <h3>快速又简便。</h3>
                        </el-col>
                        <el-col :span="2" style="text-align: right;padding-top: 25px;">
                            <el-image style="height: 80px; width: 80px;" src="src/assets/logo/Logo_part1.png" />
                        </el-col>
                    </el-row>
                    <hr>
                    <el-input v-model="user.userName" placeholder="用户名" style="margin: 0px 0px 10px 0px;"></el-input>
                    <el-input v-model="user.password" placeholder="密码" style="margin: 0px 0px 10px 0px;"></el-input>
                    <el-input v-model="user.email" placeholder="邮箱" style="margin: 0px 0px 10px 0px;"></el-input>
                    <el-row>
                        <el-input v-model="iCode" placeholder="验证码"
                            style="margin: 0px 15px 10px 0px;width: 50%;"></el-input>
                        <el-button type="primary" @click="getVCode()" style="padding: 0px 0px 0px 0px; width: 40%;">
                            获取邮箱验证码
                        </el-button>
                    </el-row>
                    <el-row>
                        <span>您的专业：</span>
                        <el-select v-model="user.major" placeholder="请选择" style="margin: 0px 0px 10px 0px; width: 30%;">
                            <el-option v-for="(t, i) of majorList" :key="i" :label="t.label" :value="t.value">
                            </el-option>
                        </el-select>
                    </el-row>
                    <el-row>
                        <span style="font-size:medium">您订阅的语言：</span>
                        <el-select v-model="user.lang" placeholder="请选择" style="margin: 0px 0px 10px 0px; width: 30%;">
                            <el-option v-for="(t, i) of langList" :key="i" :label="t.label" :value="t.value">
                            </el-option>
                        </el-select>
                    </el-row>


                    <div style="text-align: center;margin-top: 10px;">
                        <el-button type="success" @click="register()" style="width: 60%;">
                            注册
                        </el-button>
                    </div>
                </div>
            </el-col>
        </el-row>
    </div>



</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from "element-plus";
import axios from 'axios'

const router = useRouter();

// 专业列表
const majorList = [
    { label: '非计算机专业', value: -1 },
    { label: '不愿透露', value: 0 },
    { label: '计算机专业', value: 1 }
]
// 订阅语言列表
const langList = [
    { label: 'java', value: 1 },
    { label: 'c++', value: 2 },
    { label: 'python', value: 3 },
    { label: 'c', value: 4 },
]

// 用于存储获取到的验证码
var vCode = '';
// 输入的邮箱验证码
var iCode = ref('');
// 接收resp
var resVCode = reactive({
    data: "",
    code: "",
    msg: ""
});
//用于存储发送验证码的邮箱
var rEmail = '';
// 获取邮箱验证码
function getVCode() {
    if (user.email == '') {
        ElMessage({ type: 'warning', message: "请先输入邮箱" });
        return;
    }
    rEmail = user.email;
    axios.get('http://localhost:4080/users/email/' + user.email).then((resp) => {
        resVCode = resp.data;
        vCode = resVCode.data;
        if (resVCode.data == false) {
            ElMessage({ type: 'warning', message: "未能成功获取邮箱验证码" });
        }
    }).catch(function (error) {
        console.log(error);
    });
}

// 用户信息
var user = reactive({
    userName: '',
    password: '',
    email: '',
    major: 0,
    lang: 1,
})
//用于接收后台返回的json数据
var resInfo = reactive({
    data: "",
    code: "",
    msg: ""
});
// 注册
function register() {
    if (user.userName === '' || user.userName === null || user.password === '' || user.password === null || user.email === '' || user.email === null) {
        alert("注册内容不能留空");
        return;
    }
    if (user.password.length < 4) {
        alert("密码长度不符合要求");
        return;
    }
    if (iCode.value !== vCode || vCode === null || vCode === '') {
        alert("邮箱验证失败，请重新验证");
        return;
    }
    if (user.email !== rEmail) {
        alert("您更改了邮箱，请重新验证");
        return;
    }
    axios({
        method: 'post',
        url: 'http://localhost:4080/users/register',
        data: {
            name: user.userName,
            email: user.email,
            password: user.password,
            major: user.major,
            language: user.lang
        }
    })
        .then((response) => {
            // resInfo.data = response.data.data;
            // resInfo.code = response.data.code;
            // resInfo.msg = response.data.msg;
            resInfo = response.data;
            alert(resInfo.msg);
            if (resInfo.data === true) {
                router.push({ path: '/login' });
                // window.location.href = "../html/login.html"
            }
        }).catch(function (error) {
            console.log(error);
        });
}
</script>

