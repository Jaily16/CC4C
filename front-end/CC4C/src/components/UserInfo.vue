<template>
    <div class="userinfo">
        <el-row justify="center">
            <el-col :span="4" style="padding: 0px 0px 0px 30px;" >
                <el-avatar shape="circle" :size="150" :src="circleUrl" />
            </el-col>
            <el-col :span="16" style="padding: 40px 0px 0px 0px;">
                <p style="font-size: 40px; margin: 0px">{{ this.$store.state.user.name }}</p>
            </el-col>
            <el-col :span="4" style="text-align: center;">
                <el-button :icon="EditPen" @click="editInfoFormDialog = true">
                    <div>编辑个人资料</div>
                </el-button>
                <br />
                <br />
                <el-button :icon="Lock" @click="changePwdFormDialog = true">
                    <div>修改密码</div>
                </el-button>


                <el-dialog v-model="editInfoFormDialog" title="编辑个人资料" width="30%">
                    <el-form :model="infoForm">

                        <el-form-item label="头像">
                            <el-upload class="avatar-uploader" action="#" list-type="picture-card"
                                :on-change="handleChange" :show-file-list="false" :http-request="uploadAvatar">
                                <img v-if="imageUrl" :src="imageUrl" class="avatar">
                                <el-icon v-else class="avatar-uploader-icon">
                                    <Plus />
                                </el-icon>
                            </el-upload>

                        </el-form-item>

                        <el-form-item label="用户名">
                            <el-input v-model="infoForm.name" autocomplete="off" />
                        </el-form-item>

                        <el-form-item label="所学专业">
                            <el-select v-model="infoForm.major" placeholder="请选择"
                                style="margin: 0px 0px 10px 0px; width: 200px;">
                                <el-option v-for="(t, i) of majorList" :key="i" :label="t.label" :value="t.value">
                                </el-option>
                            </el-select>
                        </el-form-item>

                        <el-form-item label="订阅语言">
                            <el-select v-model="infoForm.language" placeholder="请选择"
                                style="margin: 0px 0px 10px 0px; width: 200px">
                                <el-option v-for="(t, i) of langList" :key="i" :label="t.label" :value="t.value">
                                </el-option>
                            </el-select>
                        </el-form-item>

                    </el-form>
                    <template #footer>
                        <span class="dialog-footer">
                            <el-button type="danger" @click="editInfoFormDialog = false; imageUrl = ''">
                                取消
                            </el-button>
                            <el-button type="success" @click="editInfo()">
                                提交
                            </el-button>
                        </span>
                    </template>
                </el-dialog>


                <el-dialog v-model="changePwdFormDialog" title="修改密码" width="30%">
                    <el-form :model="infoForm">
                        <el-form-item label="原密码">
                            <el-input v-model="pwdForm.password" autocomplete="off" type="password"/>
                        </el-form-item>

                        <el-form-item label="新密码">
                            <el-input v-model="pwdForm.newPassword" autocomplete="off" type="password"/>
                        </el-form-item>
                    </el-form>
                    <template #footer>
                        <span class="dialog-footer">
                            <el-button type="danger" @click="changePwdFormDialog = false;">
                                取消
                            </el-button>
                            <el-button type="success" @click="changePwd()">
                                提交
                            </el-button>
                        </span>
                    </template>
                </el-dialog>
            </el-col>
        </el-row>

        <el-row justify="center" style="margin-top: 30px">
            <el-col :span="3" style="background-color: aliceblue; height: 60px; border-radius: 5px">
                <div style="font-size: 20px; padding-top: 17px; padding-left: 17px">
                    <span v-if="props.activeIndex == 1" style="font-weight: 1000"> 个人信息</span>
                    <span v-else>
                        <RouterLink to="/userinfo"> 个人信息 </RouterLink>
                    </span>
                </div>
            </el-col>
            <el-col :span="2" style="background-color: aliceblue; height: 60px; border-radius: 5px">
                <div style="font-size: 20px; padding-top: 17px; padding-left: 5px">
                    <span v-if="props.activeIndex == 2" style="font-weight: 1000"> 收藏</span>
                    <span v-else>
                        <RouterLink to="/favorite"> 收藏 </RouterLink>
                    </span>
                </div>
            </el-col>

            <el-col :span="2" style="background-color: aliceblue; height: 60px; border-radius: 5px">
                <div style="font-size: 20px; padding-top: 17px; padding-left: 17px">
                    <span v-if="props.activeIndex == 4" style="font-weight: 1000"> 文章</span>
                    <span v-else>
                        <RouterLink to="/blogmanage"> 文章 </RouterLink>
                    </span>
                </div>
            </el-col>
            <el-col :span="16" style="background-color: aliceblue; height: 60px; border-radius: 5px">
            </el-col>
        </el-row>
    </div>
</template>

<script lang="ts" setup>
import { ref, reactive } from 'vue';
import axios from "axios";
import store from '@/store'
import {
    Plus,
    EditPen,
    Lock
} from "@element-plus/icons-vue";

// 用户头像
const circleUrl = ref(store.state.user.avatar)

axios.defaults.withCredentials = true;//这样全局设置允许

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

// 编辑信息对话框
var editInfoFormDialog = ref(false);
// 编辑信息表单
var infoForm = reactive({
    id: store.state.user.id,
    name: store.state.user.name,
    major: store.state.user.name.major,
    language: store.state.user.name.language,
    avatar: store.state.user.name.avatar
})
// 编辑信息
function editInfo() {
    if (imageUrl.value != '') {
        infoForm.avatar = imageUrl.value;
    }
    axios.put('http://localhost:4080/users/update', infoForm).then((resp) => {
        console.log(resp.data.data)

        // 修改失败
        if (resp.data.data == false) {
            alert(resp.data.msg)
            window.location.reload();
            return;
        }
        else {
            alert('用户信息修改成功')
        }

        // 重新获取用户信息并更新vuex
        axios.get("http://localhost:4080/users/info").then((resp) => {
            store.commit("SET_ID", resp.data.data.id);
            store.commit("SET_NAME", resp.data.data.name);
            store.commit("SET_EMAIL", resp.data.data.email);
            store.commit("SET_MAJOR", resp.data.data.major);
            store.commit("SET_LANGUAGE", resp.data.data.language);
            store.commit("SET_AVATAR", resp.data.data.avatar);

            window.location.reload();
        })

        // editInfoFormDialog.value = false;
        // imageUrl.value = ''

    })
}

// 修改密码对话框
var changePwdFormDialog = ref(false)
// 修改密码表单
var pwdForm = reactive({
    id: store.state.user.id,
    password: '',
    newPassword: ''
})
// 修改密码
function changePwd() {
    axios.put('http://localhost:4080/users/password/change', pwdForm).then((resp) => {
        // 修改失败
        if (resp.data.data == false) {
            alert(resp.data.msg)
            // window.location.reload();
            return;
        }
        // 修改成功
        else {
            alert('密码修改成功！')
        }
        changePwdFormDialog.value = false
        pwdForm.password = '';
        pwdForm.newPassword = '';
    })
}


// 头像回显路径
var imageUrl = ref('');
//将上传图片的原路径赋值给临时路径
function handleChange(file, fileList) {
    // tempUrl.value = URL.createObjectURL(file.raw);
}
//实现图片上传功能
function uploadAvatar(item) {
    //验证图片格式大小信息
    const isJPG = item.file.type == 'image/jpeg' || item.file.type == 'image/png';
    const isLt2M = item.file.size / 1024 / 1024 < 2;
    if (!isJPG) {
        this.$message.error('上传图片只能是 JPG 或 PNG 格式!');
    }
    if (!isLt2M) {
        this.$message.error('上传图片大小不能超过 2MB!');
    }

    //图片格式大小信息没问题 执行上传图片的方法
    if (isJPG && isLt2M == true) {
        // // post上传图片
        // let App = this;
        //定义FormData对象 存储文件
        let mf = new FormData();
        //将图片文件放入mf
        mf.append('file', item.file);

        axios.post('http://localhost:4080/users/uploadAvatar', mf).then((resp) => {
            console.log(resp.data.data)
            imageUrl.value = resp.data.data.requestPath;
        })
    }
}


const props = defineProps(['activeIndex'])

</script>

<style scoped>
a {
    text-decoration: none;
}

.router-link-active {
    text-decoration: none;
}


/* .avatar-uploader {
    margin-top: 20px;
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    width: 178px;
    height: 178px;
} */

.avatar-uploader:hover {
    border-color: #409EFF;
}

.avatar-uploader-icon {
    font-size: 28px;
    color: #8c939d;
    width: 178px;
    height: 178px;
    line-height: 178px;
    text-align: center;
}

.avatar {
    width: 148px;
    height: 148px;
    display: flex;
}
</style>