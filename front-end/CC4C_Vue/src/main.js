import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import './assets/main.css'

// 引入element-plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 引入axios
// import axios from '@/plugins/axiosInstance.js'
// import axios from 'axios'

const app = createApp(App)



app.use(router)
app.use(ElementPlus)
// app.use(axios)

app.mount('#app')
// app.config.globalProperties.$axios = axios;  //配置axios的全局引用
