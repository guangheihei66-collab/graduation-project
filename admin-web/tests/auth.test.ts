import { beforeEach, describe, it, expect, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { authApi } from "../src/api";
import { useAuthStore } from "../src/stores/auth";
import { ApiError } from "../src/api/http";
import { tokenStorage } from "../src/utils/session";
import { visibleNavigation, safeRedirect, router } from "../src/router";
vi.mock("../src/api", () => ({
  authApi: { login: vi.fn(), me: vi.fn() },
  statisticsApi: { overview: vi.fn() },
}));
const sys = {
  id: "9007199254740993",
  username: "admin",
  realName: "测试管理员",
  role: "SYS_ADMIN" as const,
};
beforeEach(async () => {
  setActivePinia(createPinia());
  sessionStorage.clear();
  vi.resetAllMocks();
  await router.push("/login");
});
describe("会话和路由", () => {
  it("登录成功保存令牌并调用me验证身份", async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: "test-token",
      expiresIn: 7200,
      user: sys,
    });
    vi.mocked(authApi.me).mockResolvedValue(sys);
    const store = useAuthStore();
    expect(await store.login("admin", "test-password")).toBe(true);
    expect(tokenStorage.get()).toBe("test-token");
    expect(store.user?.id).toBe("9007199254740993");
    expect(authApi.me).toHaveBeenCalledOnce();
  });
  it("失败登录不保留令牌和身份", async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error("凭据错误"));
    const store = useAuthStore();
    await expect(store.login("bad", "bad")).rejects.toThrow();
    expect(store.user).toBeNull();
    expect(tokenStorage.get()).toBeNull();
    expect(store.busy).toBe(false);
  });
  it("退出清理前端会话", async () => {
    tokenStorage.set("test-token");
    vi.mocked(authApi.me).mockResolvedValue(sys);
    const store = useAuthStore();
    await store.restore();
    store.clear();
    expect(tokenStorage.get()).toBeNull();
    expect(store.user).toBeNull();
  });
  it("学生即使有令牌也不能恢复后台会话", async () => {
    tokenStorage.set("test-token");
    vi.mocked(authApi.me).mockResolvedValue({ ...sys, role: "STUDENT" });
    await expect(useAuthStore().restore()).rejects.toMatchObject({
      status: 403,
    });
    expect(tokenStorage.get()).toBeNull();
  });
  it("两类管理员导航不同，学生没有管理菜单", () => {
    expect(
      visibleNavigation("SYS_ADMIN").some((i) => i.path === "/users"),
    ).toBe(true);
    expect(
      visibleNavigation("LAB_ADMIN").some((i) => i.path === "/users"),
    ).toBe(false);
    expect(visibleNavigation("STUDENT")).toEqual([]);
  });
  it("未认证访问受保护路由跳转登录", async () => {
    await router.push("/equipment");
    expect(router.currentRoute.value.path).toBe("/login");
    expect(router.currentRoute.value.query.redirect).toBe("/equipment");
  });
  it("实验室管理员直接输入用户路由也被拒绝", async () => {
    tokenStorage.set("test-token");
    vi.mocked(authApi.me).mockResolvedValue({ ...sys, role: "LAB_ADMIN" });
    await router.push("/users");
    expect(router.currentRoute.value.path).toBe("/forbidden");
  });
  it("只接受站内重定向路径", () => {
    expect(safeRedirect("//evil.test")).toBe("/dashboard");
    expect(safeRedirect("https://evil.test")).toBe("/dashboard");
    expect(safeRedirect("/equipment")).toBe("/equipment");
  });
  it("暂时性服务错误保留会话并显示重试页面", async () => {
    tokenStorage.set("test-token");
    vi.mocked(authApi.me).mockRejectedValue(new ApiError("服务暂不可用", 503));
    await router.push("/equipment");
    expect(router.currentRoute.value.path).toBe("/connection-error");
    expect(tokenStorage.get()).toBe("test-token");
  });
});
