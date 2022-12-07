<template>
  <div class="write">
    <el-row justify="center">
      <el-col :span="20">
        <div style="margin: 50px 0px 30px 0px">
          <h2>撰写博客</h2>
        </div>
        <div>
          <el-progress v-show="isDis" :percentage="load" />
          <md-editor
            :toolbarsExclude="['link', 'mermaid', 'katex', 'github']"
            v-model="text"
            @onUploadImg="onUploadImg"
            @onSave="codeSave"
            style="height: 650px"
          />
        </div>
        <div style="margin:10px 0px 0px 0px">
          <el-row justify="end">
            <el-col offset="19" :span="4" style="padding:0px 0px 0px 30px">
              <el-button type="success" size="large">保存到草稿箱</el-button>
              <el-button type="primary" size="large">发布</el-button>
            </el-col>
          </el-row>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script lang='ts'>
// 引入md-editor-v3
import { ref } from "vue";
import MdEditor from "md-editor-v3";
import "md-editor-v3/lib/style.css";

const text = ref("");

// 客制
import { defineComponent, reactive, toRefs, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

interface shareData {
  text: string;
  load: number;
  isDis: boolean;
}

export default defineComponent({
  name: "codeShare",
  components: { MdEditor },
  setup() {
    const data = <shareData>reactive({
      text: "",
      load: 0,
      isDis: false,
    });

    const codeSave = (v: string): void => {
      ElMessage.info("已保存");
      localStorage.setItem("codeSave", v);
    };

    const href = window.location.href;
    const url = href.substring(0, href.length - 10);

    onMounted(() => {
      if (localStorage.getItem("codeSave")) {
        data.text = localStorage.getItem("codeSave") || "";
      }
    });

    return {
      ...toRefs(data),
      codeSave,
    };
  },
  methods: {
    // 图片上传
    async onUploadImg(files: FileList, callback: (urls: string[]) => void) {
      this.load = 0;
      this.isDis = true;
      const res = await Promise.all(
        Array.from(files).map((file) => {
          return new Promise((rev, rej) => {
            const form = new FormData();
            form.append("img", file);
            // @ts-ignore
            onUploadImg(form, this.onUploadProgress).then((res) => {
              rev(res);
            });
          });
        })
      );

      callback(res.map((item: any) => item.data.data));
    },
    // 获取图片上传进度
    onUploadProgress(e: number) {
      this.load = e;
      if (e === 100) {
        setTimeout(() => {
          this.isDis = false;
        }, 1000);
      }
    },
  },
});
</script>

<style>
</style>