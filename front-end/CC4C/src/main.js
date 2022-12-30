import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'

import './assets/main.css'

// 引入element-plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App)


app.use(router)
app.use(store)
app.use(ElementPlus)
// app.use(axios)

app.mount('#app')

