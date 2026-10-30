<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
const auth = useAuthStore();
</script>
<template>
  <div class="standalone-state">
    <span>404</span>
    <h1>页面未找到</h1>
    <p>请检查地址，或返回管理工作台。</p>
    <router-link :to="auth.user ? '/dashboard' : '/login'">
      <el-button type="primary">返回首页</el-button>
    </router-link>
  </div>
</template>
