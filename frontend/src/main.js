import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import store from './store';

import './styles/main.css';

// 引入element-plus
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';

const app = createApp(App);

app.use(router);
app.use(store);
app.use(ElementPlus);

/** 监听业务客户端的 401 事件，清空 Vuex 身份并按当前路由进入用户或管理员登录页。 */
window.addEventListener('cc4c:unauthorized', async () => {
  const wasAdminRoute = router.currentRoute.value.path.startsWith('/admin');
  store.commit('RESET_STATE');
  const target = wasAdminRoute ? '/adminLogin' : '/login';
  if (router.currentRoute.value.path !== target) {
    await router.replace(target);
  }
});

app.mount('#app');
