<template>
  <div class="write" style="background-color: RGB(244,246,248);">
    <el-row justify="center">
      <el-col :span="22"
        style="height:auto; background-color: white; padding: 0px 50px 0px 50px; margin: 20px 0px 0px 0px;">
        <div style="margin: 0px 0px 30px 0px">
          <h2>添加课程</h2>
        </div>
        <el-row :gutter="50">
          <el-col :span="10">
            <h3>课程标题</h3>
            <el-input v-model="addCourseForm.courseName">
            </el-input>
          </el-col>
          <el-col :span="6">
            <h3>课程难度</h3>
            <el-select v-model="addCourseForm.level" placeholder="请选择">
              <el-option v-for="level in courseLevel" :label="level.label" :value="level.value" />
            </el-select>
          </el-col>
          <el-col :span="8">
            <h3>课程语言模块</h3>
            <el-cascader v-model="selectedModule" :options="modules" />
            <el-button type="primary" style="margin: 0px 0px 0px 20px"
              @click="addModuleDialog = true">添加语言模块</el-button>
          </el-col>
        </el-row>

        <h3>课程内容</h3>
        <div>
          <md-editor :toolbarsExclude="['link', 'mermaid', 'katex', 'github']" v-model="addCourseForm.description"
            @onSave="codeSave" @on-upload-img="onUploadImg" style="height: 650px" />
        </div>

        <div style="margin:10px 0px 0px 0px">
          <el-row justify="end" style="padding:0px 0px 30px 0px">
            <!-- <el-button type="success" size="large" @click="draft()">保存到草稿箱</el-button> -->
            <el-button type="primary" size="large" @click="addCourse()">发布新课程</el-button>
          </el-row>
        </div>

        <el-dialog v-model="addModuleDialog" title="添加课程模块" width="30%">
          <el-form :model="addModuleForm">

            <el-form-item label="所属语言">
              <el-select v-model="addModuleForm.languageId" placeholder="请选择">
                <el-option v-for="lang in langs" :label="lang.label" :value="lang.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="模块名称">
              <el-input v-model="addModuleForm.moduleName" />
            </el-form-item>

            <el-form-item label="模块难度">
              <el-select v-model="addModuleForm.level" placeholder="请选择">
                <el-option v-for="level in moduleLevel" :label="level.label" :value="level.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="模块优先级">
              <el-select v-model="addModuleForm.priority" placeholder="请选择">
                <el-option v-for="i in 11" :label="i - 1" :value="i - 1" />
              </el-select>
            </el-form-item>

          </el-form>
          <template #footer>
            <span class="dialog-footer">
              <el-button @click="addModuleDialog = false">取消</el-button>
              <el-button type="primary" @click="addModule()">
                提交
              </el-button>
            </span>
          </template>
        </el-dialog>

      </el-col>
    </el-row>
  </div>
</template>
  
<script lang='ts' setup>
// 引入md-editor-v3
import { ref, reactive } from "vue";
import MdEditor from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import axios from "axios";

import { ElMessage } from "element-plus";

// 语言模块
const selectedModule = ref([]);
const modules = reactive([
  {
    label: 'java',
    value: 'java',
    children: []
  },
  {
    label: 'c++',
    value: 'c++',
    children: []

  },
  {
    label: 'python',
    value: 'python',
    children: []
  },
  {
    label: 'c',
    value: 'c',
    children: []
  }
]);

// 语言列表
const langs = reactive([
  {
    value: 1,
    label: 'java'
  },
  {
    value: 2,
    label: 'c++'
  },
  {
    value: 3,
    label: 'python'
  },
  {
    value: 4,
    label: 'c'
  },
])


var addModuleDialog = ref(false);
// 添加模块表单
var addModuleForm = reactive({
  languageId: 1,
  moduleName: '',
  level: 0,
  priority: 1
});

// 课程难度列表
var courseLevel = reactive([
  {
    value: -2,
    label: '简单'
  },
  {
    value: -1,
    label: '简单且默认'
  },
  {
    value: 0,
    label: '默认'
  },
  {
    value: 1,
    label: '困难且默认'
  },
  {
    value: 2,
    label: '困难'
  },
  {
    value: 66,
    label: '必须展示'
  },
])
// 模块难度列表
var moduleLevel = reactive([
  {
    value: -1,
    label: '简单'
  },
  {
    value: 0,
    label: '默认'
  },
  {
    value: 1,
    label: '困难'
  },
])



async function loadModules() {
  try {
    const responses = await Promise.all(
      modules.map((_, index) => axios.get("http://localhost:4080/courses/module/" + (index + 1)))
    );
    responses.forEach((resp, index) => {
      modules[index].children = (resp.data.data || []).map((courseModule) => ({
        label: courseModule.moduleName,
        value: courseModule.priority,
        languageId: courseModule.languageId,
      }));
    });
  } catch (error) {
    ElMessage.error('课程模块加载失败，请稍后重试');
    console.error(error);
  }
}

loadModules();

// 增加语言模块
async function addModule() {
  if (!addModuleForm.moduleName.trim()) {
    ElMessage.warning('模块名称不能为空');
    return;
  }
  try {
    const resp = await axios.post("http://localhost:4080/courses/module", addModuleForm);
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '课程模块添加失败');
      return;
    }
    await loadModules();
    addModuleDialog.value = false;
    addModuleForm.moduleName = '';
    ElMessage.success('课程模块添加成功');
  } catch (error) {
    ElMessage.error('课程模块添加失败，请稍后重试');
    console.error(error);
  }
}


// 保存提示
function codeSave() {
  ElMessage({ type: 'success', message: "已保存" });
};
// 添加课程表单
var addCourseForm = reactive({
  languageName: '',
  languageId: '',
  courseName: '',
  description: '',
  level: 0,
  priority: 0
})

function langMap(langName) {
  if (langName == 'java') {
    return 1;
  }
  else if (langName == 'c++') {
    return 2;
  }
  else if (langName == 'python') {
    return 3;
  }
  else if (langName == 'c') {
    return 4;
  }
}

// 添加课程
async function addCourse() {
  if (!addCourseForm.courseName.trim()) {
    ElMessage.warning('课程标题不能为空');
    return;
  }
  if (selectedModule.value.length !== 2) {
    ElMessage.warning('请选择课程语言模块');
    return;
  }
  if (!addCourseForm.description.trim()) {
    ElMessage.warning('课程内容不能为空');
    return;
  }

  addCourseForm.languageName = selectedModule.value[0];
  addCourseForm.languageId = langMap(selectedModule.value[0]);
  addCourseForm.priority = selectedModule.value[1];

  try {
    const resp = await axios.post("http://localhost:4080/courses/add", addCourseForm);
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '课程发布失败');
      return;
    }
    ElMessage.success(resp.data.msg || '课程发布成功');
    addCourseForm.courseName = '';
    addCourseForm.description = '';
    addCourseForm.level = 0;
    selectedModule.value = [];
  } catch (error) {
    ElMessage.error('课程发布失败，请稍后重试');
    console.error(error);
  }
}



// // 保存草稿
// function draft() {
//   if (text.value == '') {
//     ElMessage({ type: 'warning', message: "文章内容不能为空" });
//   }
//   else {
//     axios.post("http://localhost:4080/blogs/draft", {
//       userId: user.value.id,
//       content: text.value
//     }).then((resp) => {
//       ElMessage({ type: 'success', message: "成功保存至草稿箱" });

//     });
//   }
// }

// 上传图片
const onUploadImg = async (files, callback) => {
  const res = await Promise.all(
    files.map((file) => {
      return new Promise((rev, rej) => {
        const form = new FormData();
        form.append('file', file);

        axios
          .post('http://localhost:4080/blogs/uploadImg', form, {
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

  // var user = ref({
  //   id: '',
  //   name: '',
  //   email: '',
  //   major: ''
  // });

</script>

<style scoped>

</style>
