import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus, { ElMessage } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/dist/index.css";
import "./styles.css";
import App from "./App.vue";
import { router } from "./router";
import { useAuthStore } from "./stores/auth";
import { onUnauthorized } from "./api/http";
const app = createApp(App),
  pinia = createPinia();
app.use(pinia);
onUnauthorized(() => {
  useAuthStore(pinia).clear();
  if (router.currentRoute.value.path !== "/login") {
    ElMessage.warning("会话已失效，请重新登录");
    void router.replace({
      path: "/login",
      query: { redirect: router.currentRoute.value.fullPath },
    });
  }
});
app.use(router).use(ElementPlus, { locale: zhCn }).mount("#app");
