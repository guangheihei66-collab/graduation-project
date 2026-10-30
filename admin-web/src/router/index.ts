import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
import { tokenStorage } from "../utils/session";
import type { Role } from "../types";
export const navigation = [
  { path: "/dashboard", title: "统计看板" },
  { path: "/laboratories", title: "实验室管理" },
  { path: "/equipment", title: "设备管理" },
  { path: "/reservations", title: "预约管理" },
  { path: "/users", title: "用户管理", role: "SYS_ADMIN" },
];
export function visibleNavigation(role: Role | undefined) {
  return role === "LAB_ADMIN" || role === "SYS_ADMIN"
    ? navigation.filter((item) => !item.role || item.role === role)
    : [];
}
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      component: () => import("../views/login/LoginView.vue"),
      meta: { public: true, title: "登录" },
    },
    {
      path: "/",
      component: () => import("../layout/AdminLayout.vue"),
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          component: () => import("../views/dashboard/DashboardView.vue"),
          meta: { title: "统计看板" },
        },
        {
          path: "laboratories",
          component: () => import("../views/laboratories/LaboratoriesView.vue"),
          meta: { title: "实验室管理" },
        },
        {
          path: "equipment",
          component: () => import("../views/equipment/EquipmentView.vue"),
          meta: { title: "设备管理" },
        },
        {
          path: "reservations",
          component: () => import("../views/reservations/ReservationsView.vue"),
          meta: { title: "预约管理" },
        },
        {
          path: "users",
          component: () => import("../views/users/UsersView.vue"),
          meta: { title: "用户管理", role: "SYS_ADMIN" },
        },
        {
          path: "forbidden",
          component: () => import("../views/ForbiddenView.vue"),
          meta: { title: "权限不足" },
        },
      ],
    },
    {
      path: "/connection-error",
      component: () => import("../views/ConnectionErrorView.vue"),
      meta: { public: true, title: "服务连接异常" },
    },
    {
      path: "/:pathMatch(.*)*",
      component: () => import("../views/NotFoundView.vue"),
      meta: { public: true, title: "页面不存在" },
    },
  ],
});
export function safeRedirect(value: unknown): string {
  return typeof value === "string" &&
    value.startsWith("/") &&
    !value.startsWith("//") &&
    !value.startsWith("/login")
    ? value
    : "/dashboard";
}
router.beforeEach(async (to) => {
  document.title = `${String(to.meta.title || "管理后台")} · 高校实验室预约`;
  if (to.meta.public) return true;
  const auth = useAuthStore();
  if (!tokenStorage.get())
    return { path: "/login", query: { redirect: to.fullPath } };
  try {
    if (!(await auth.restore())) return "/login";
  } catch {
    // 只有失效会话才清理；网络或服务故障保留令牌，让用户显式重试。
    return tokenStorage.get()
      ? { path: "/connection-error", query: { redirect: to.fullPath } }
      : "/login";
  }
  if (to.meta.role && to.meta.role !== auth.user?.role) return "/forbidden";
  return true;
});
