<template>
    <div style="background-color: rgb(244,246,248);">
        <el-row justify="center">
            <el-col :span="6" style="margin: 80px 0px 500px 0px; ">
                <div style="text-align: center;">
                    <el-image style="height: 150px; width: 150px; " src="src/assets/logo/Logo_part1.png" />
                    <el-image style="height: 50px; width: 350px; " src="src/assets/logo/Logo_part3.png" />
                </div>
                <div
                    style="background-color: white; padding: 30px 50px 50px 50px; border-radius: 10px;box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
                    <el-input v-model="form.id" style="margin: 0px 0px 10px 0px;" placeholder="请输入管理员ID"></el-input>
                    <el-input v-model="form.password" style="margin: 0px 0px 20px 0px;" type="password"
                        placeholder="请输入密码"></el-input>
                    <el-row justify="center">
                        <el-button type="primary" style="width: 100%; font-weight: bolder;" @click="login()">
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

const router = useRouter();

var form = reactive({
    id: '',
    password: ''
})

axios.defaults.withCredentials = true;//这样全局设置允许

// 用于接收后台返回的json数据
var res = ref({});
// 登陆事件
function login() {
    axios.post("http://localhost:4080/admin/login",
        {
            adminId: form.id,
            adminPassword: form.password
        }).then((resp) => {
            alert(resp.data.msg)
            
            res.value = resp.data.data;
            // 登陆成功跳转
            router.push({ path: '/admin/CoursesAndBlogs' })
        }).catch(function (error) {
            console.log(error);
        });
}

</script>

