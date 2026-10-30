<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { safeRedirect } from "../router";
import { useAuthStore } from "../stores/auth";
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
function login(): void {
  auth.clear();
  void router.replace("/login");
}
</script>
<template>
  <el-result
    class="standalone-state"
    icon="warning"
    title="暂时无法验证会话"
    sub-title="后端服务或网络暂时不可用，已保留当前会话。请检查连接后重试。"
  >
    <template #extra>
      <el-button
        type="primary"
        @click="router.replace(safeRedirect(route.query.redirect))"
      >
        重新连接
</el-button
      ><el-button @click="login">返回登录</el-button>
    </template>
  </el-result>
</template>
