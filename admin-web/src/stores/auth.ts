import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { authApi } from "../api";
import { tokenStorage } from "../utils/session";
import { ApiError } from "../api/http";
import type { CurrentUser } from "../types";
import { useStatisticsStore } from "./statistics";
export const useAuthStore = defineStore("auth", () => {
  const user = ref<CurrentUser | null>(null),
    busy = ref(false);
  const isSystemAdmin = computed(() => user.value?.role === "SYS_ADMIN");
  function clear(): void {
    tokenStorage.clear();
    user.value = null;
    useStatisticsStore().reset();
  }
  async function restore(): Promise<boolean> {
    if (!tokenStorage.get()) {
      clear();
      return false;
    }
    const current = await authApi.me();
    if (current.role !== "SYS_ADMIN" && current.role !== "LAB_ADMIN") {
      clear();
      throw new ApiError("学生账号不能进入管理后台", 403, "FORBIDDEN");
    }
    user.value = current;
    return true;
  }
  async function login(username: string, password: string): Promise<boolean> {
    if (busy.value) return false;
    busy.value = true;
    clear();
    try {
      const result = await authApi.login(username, password);
      tokenStorage.set(result.accessToken);
      await restore();
      return true;
    } catch (e) {
      clear();
      throw e;
    } finally {
      busy.value = false;
    }
  }
  return { user, busy, isSystemAdmin, login, restore, clear };
});
