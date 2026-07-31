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
                            <el-image style="height: 80px; width: 80px;" :src="assets.logoPart1" />
                        </el-col>
                    </el-row>
                    <hr>
                    <el-input v-model="user.userName" placeholder="用户名" style="margin: 0px 0px 10px 0px;"></el-input>
                    <el-input v-model="user.password" type="password" show-password placeholder="密码"
                        style="margin: 0px 0px 10px 0px;"></el-input>
                    <el-input v-model="user.email" placeholder="邮箱" style="margin: 0px 0px 10px 0px;"></el-input>
                    <el-row>
                        <el-input v-model="iCode" placeholder="验证码"
                            style="margin: 0px 15px 10px 0px;width: 50%;"></el-input>
                        <el-button type="primary" :loading="sendingCode" @click="getVCode()"
                            style="padding: 0px 0px 0px 0px; width: 40%;">
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
                        <el-button type="success" :loading="registering" @click="register()" style="width: 60%;">
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
import { assets } from '@/assets';

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
//用于存储发送验证码的邮箱
var rEmail = '';
const sendingCode = ref(false);
const registering = ref(false);
// 获取邮箱验证码
async function getVCode() {
    const email = user.email.trim();
    if (!email) {
        ElMessage.warning("请先输入邮箱");
        return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        ElMessage.warning("请输入正确的邮箱地址");
        return;
    }
    sendingCode.value = true;
    try {
        const resp = await axios.get('http://localhost:4080/users/email/' + encodeURIComponent(email));
        if (!resp.data.data) {
            ElMessage.error(resp.data.msg || "未能成功获取邮箱验证码");
            return;
        }
        rEmail = email;
        vCode = resp.data.data;
        ElMessage.success('验证码已发送');
    } catch (error) {
        ElMessage.error('验证码发送失败，请稍后重试');
        console.error(error);
    } finally {
        sendingCode.value = false;
    }
}

// 用户信息
var user = reactive({
    userName: '',
    password: '',
    email: '',
    major: 0,
    lang: 1,
})
// 注册
async function register() {
    if (!user.userName.trim() || !user.password || !user.email.trim()) {
        ElMessage.warning("注册内容不能留空");
        return;
    }
    if (user.password.length < 4) {
        ElMessage.warning("密码长度不符合要求");
        return;
    }
    if (iCode.value !== vCode || vCode === null || vCode === '') {
        ElMessage.warning("邮箱验证失败，请重新验证");
        return;
    }
    if (user.email.trim() !== rEmail) {
        ElMessage.warning("您更改了邮箱，请重新验证");
        return;
    }

    registering.value = true;
    try {
        const response = await axios.post('http://localhost:4080/users/register', {
            name: user.userName.trim(),
            email: user.email.trim(),
            password: user.password,
            major: user.major,
            language: user.lang
        });
        if (response.data.data !== true) {
            ElMessage.error(response.data.msg || '注册失败');
            return;
        }
        ElMessage.success(response.data.msg || '注册成功');
        await router.push({ path: '/login' });
    } catch (error) {
        ElMessage.error('注册失败，请稍后重试');
        console.error(error);
    } finally {
        registering.value = false;
    }
}
</script>

