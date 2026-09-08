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

/** 登记页面可见性监听，以便隐藏时暂停轮询并在恢复后安全刷新。 */
window.addEventListener('cc4c:unauthorized', async () => {
  const wasAdminRoute = router.currentRoute.value.path.startsWith('/admin');
  store.commit('RESET_STATE');
  const target = wasAdminRoute ? '/adminLogin' : '/login';
  if (router.currentRoute.value.path !== target) {
    await router.replace(target);
  }
});

app.mount('#app');
