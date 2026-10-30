<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  DataAnalysis,
  OfficeBuilding,
  Monitor,
  Tickets,
  User,
  Fold,
  SwitchButton,
  ArrowRight,
} from "@element-plus/icons-vue";
import { useAuthStore } from "../stores/auth";
import { visibleNavigation } from "../router";
import { roles } from "../utils/business";
import { shanghaiDate } from "../utils/date";
const auth = useAuthStore(),
  route = useRoute(),
  router = useRouter(),
  open = ref(false);
const menu = computed(() => visibleNavigation(auth.user?.role));
const icons = [DataAnalysis, OfficeBuilding, Monitor, Tickets, User];
function logout(): void {
  auth.clear();
  void router.replace("/login");
}
</script>
<template>
  <div class="admin-shell">
    <button
      v-if="open"
      class="sidebar-mask"
      aria-label="关闭导航"
      @click="open = false"
    ></button>
    <aside class="sidebar" :class="{ 'sidebar-open': open }">
      <router-link class="brand" to="/dashboard">
        <img src="/favicon.svg" alt="" />
        <div>实验室预约<span>高校设备管理平台</span></div>
      </router-link>
      <div class="nav-caption">管理工作台</div>
      <nav aria-label="管理导航">
        <router-link
          v-for="(item, index) in menu"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          @click="open = false"
        >
          <el-icon><component :is="icons[index]" /></el-icon>{{ item.title
          }}<el-icon class="nav-arrow"><ArrowRight /></el-icon>
        </router-link>
      </nav>
      <div class="sidebar-note">
        <span class="status-dot"></span> 设备有序共享<br /><small
          >预约有据 · 使用可追溯</small
        >
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="topbar-left">
          <el-button
            class="menu-toggle"
            text
            aria-label="展开导航"
            @click="open = !open"
          >
            <el-icon><Fold /></el-icon>
</el-button
          ><span class="breadcrumb"
            >管理工作台 <span>/</span>
            <strong>{{ route.meta.title }}</strong></span
          >
        </div>
        <div class="topbar-right">
          <span class="today">{{ shanghaiDate() }} · 北京时间</span>
          <div class="user-avatar">{{ auth.user?.realName.slice(0, 1) }}</div>
          <div class="user-meta">
            <strong>{{ auth.user?.realName }}</strong
            ><span>{{ auth.user ? roles[auth.user.role] : "" }}</span>
          </div>
          <el-button
            text
            aria-label="退出登录"
            title="退出登录"
            @click="logout"
          >
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </header>
      <main class="workspace"><router-view /></main>
      <footer class="app-footer">
        高校实验室设备预约系统 <span>规范预约 · 高效共享</span>
      </footer>
    </div>
  </div>
</template>
