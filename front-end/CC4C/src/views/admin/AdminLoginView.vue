<template>
    <div style="background-color: rgb(244,246,248);">
        <el-row justify="center">
            <el-col :span="6" style="margin: 80px 0px 500px 0px; ">
                <div style="text-align: center;">
                    <el-image style="height: 150px; width: 150px; " :src="assets.logoPart1" />
                    <el-image style="height: 50px; width: 350px; " :src="assets.logoPart3" />
                </div>
                <div
                    style="background-color: white; padding: 30px 50px 50px 50px; border-radius: 10px;box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
                    <el-input v-model="form.id" style="margin: 0px 0px 10px 0px;" placeholder="请输入管理员ID"></el-input>
                    <el-input v-model="form.password" style="margin: 0px 0px 20px 0px;" type="password"
                        placeholder="请输入密码"></el-input>
                    <el-row justify="center">
                        <el-button type="primary" style="width: 100%; font-weight: bolder;" :loading="loggingIn"
                            @click="login()">
                            管理员登录
                        </el-button>
                    </el-row>
                </div>

            </el-col>
        </el-row>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import axios from 'axios'
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { assets } from '@/assets';

const router = useRouter();

var form = reactive({
    id: '',
    password: ''
})

axios.defaults.withCredentials = true;//这样全局设置允许

const loggingIn = ref(false);
// 登陆事件
async function login() {
    if (!form.id || !form.password) {
        ElMessage.warning('请输入管理员 ID 和密码');
        return;
    }

    loggingIn.value = true;
    try {
        const resp = await axios.post("http://localhost:4080/admin/login", {
            adminId: form.id,
            adminPassword: form.password
        });
        if (!resp.data.data) {
            ElMessage.error(resp.data.msg || '管理员登录失败');
            return;
        }
        ElMessage.success(resp.data.msg || '登录成功');
        await router.push({ path: '/admin/CoursesAndBlogs' });
    } catch (error) {
        ElMessage.error('管理员登录服务暂时不可用，请稍后重试');
        console.error(error);
    } finally {
        loggingIn.value = false;
    }
}

</script>

