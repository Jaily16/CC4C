<template>
    <div style="background-color: rgb(244,246,248);">
        <el-row justify="center">
            <el-col :span="6" style="margin: 80px 0px 500px 0px; ">
                <!-- <h1>{{this.$store.state.user.name}}</h1> -->
                <div style="text-align: center;">
                    <el-image style="height: 150px; width: 150px; " src="src/assets/logo/Logo_part1.png" />
                    <el-image style="height: 50px; width: 350px; " src="src/assets/logo/Logo_part3.png" />
                </div>
                <div
                    style="background-color: white; padding: 30px 50px 50px 50px; border-radius: 10px;box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)">
                    <el-input v-model="form.email" style="margin: 0px 0px 10px 0px;" placeholder="请输入邮箱"></el-input>
                    <el-input v-model="form.password" style="margin: 0px 0px 20px 0px;" type="password"
                        placeholder="请输入密码"></el-input>

                    <el-row justify="center">
                        <el-button type="primary" style="width: 100%; font-weight: bolder;" @click="login()">
                            登录
                        </el-button>
                        <el-divider></el-divider>
                        <el-button type="success" style="width: 100%; font-weight: bolder;" @click="flyToRegister()">
                            注册
                        </el-button>
                        <el-button link style="margin: 10px 0px 0px 0px;" @click="findPwdDialog = !findPwdDialog">
                            找回密码
                        </el-button>

                        <el-dialog v-model="findPwdDialog" title="找回密码" width="30%">

                            <el-input v-model="findForm.email" placeholder="邮箱"
                                style="margin: 0px 0px 10px 0px;"></el-input>

                            <el-input v-model="findForm.password" placeholder="新密码" style="margin: 0px 0px 10px 0px;"
                                type="password"></el-input>

                            <el-row>
                                <el-input v-model="iCode" placeholder="验证码"
                                    style="margin: 0px 15px 10px 0px;width: 50%;"></el-input>
                                <el-button type="primary" @click="getVCode()"
                                    style="padding: 0px 0px 0px 0px; width: 30%;">
                                    获取邮箱验证码
                                </el-button>
                            </el-row>

                            <template #footer>
                                <span class="dialog-footer">
                                    <el-button @click="findPwdDialog = false">取消</el-button>
                                    <el-button type="success" @click="findPassword()">
                                        找回密码
                                    </el-button>
                                </span>
                            </template>
                        </el-dialog>

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
import store from '@/store'
import { ElMessage } from 'element-plus';

axios.defaults.withCredentials = true;//这样全局设置允许

const router = useRouter();

function flyToRegister() {
    router.push({ path: '/register' });
}

// 用于提交登陆信息
var form = reactive({
    email: '',
    password: ''
})
// // 用于接收后台返回的user信息
var user = reactive({

})
// 用于接收后台返回的json数据
var res = ref({
    code: '',
    data: '',
    msg: ''
});
// 登陆事件
function login() {
    axios.post("http://localhost:4080/users/login",
        {
            email: form.email,
            password: form.password
        }).then((resp) => {
            res = resp.data;
            // console.log(res);
            // 登陆成功
            if (res.data == true) {
                // 获取用户信息保存至vuex
                axios.get("http://localhost:4080/users/info").then((resp) => {
                    // console.log(resp.data.data);
                    store.commit("SET_ID", resp.data.data.id);
                    store.commit("SET_NAME", resp.data.data.name);
                    store.commit("SET_EMAIL", resp.data.data.email);
                    store.commit("SET_MAJOR", resp.data.data.major);
                    store.commit("SET_LANGUAGE", resp.data.data.language);
                    store.commit("SET_AVATAR", resp.data.data.avatar);
                    // console.log(store.state.user)
                })
                // 跳转到主页
                router.push({ path: '/home' })
            }
            // 登陆失败
            else {
                alert(res.msg);
            }
        }).catch(function (error) {
            console.log(error);
        });
}

// 找回密码
var findPwdDialog = ref(false)
// 找回密码表单
var findForm = reactive({
    email: '',
    password: ''
})
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
    if (findForm.email == '') {
        ElMessage({ type: 'warning', message: "请先输入邮箱" });
        return;
    }
    rEmail = findForm.email;
    axios.get('http://localhost:4080/users/email/' + findForm.email).then((resp) => {
        resVCode = resp.data;
        vCode = resVCode.data;
        if (resVCode.data == false) {
            ElMessage({ type: 'warning', message: "未能成功获取邮箱验证码" });
        }
    }).catch(function (error) {
        console.log(error);
    });
}
// 找回密码
function findPassword() {
    if (iCode.value != vCode || rEmail != findForm.email) {
        alert("验证码错误")
        return;
    }
    if (findForm.password == '') {
        alert("新密码不能为空")
        return;

    }
    else {
        axios.put('http://localhost:4080/users/password/forget', {
            email: findForm.email,
            newPassword: findForm.password
        }).then((resp) => {
            if (resp.data.data == true) {
                ElMessage.success("密码修改成功")
                // alert("密码修改成功")
                return;

            }

        })
        findPwdDialog.value = false
    }

}
</script>

