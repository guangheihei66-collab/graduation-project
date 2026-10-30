import { defineStore } from "pinia";
import { ref } from "vue";
import type { Statistics, StatsParams } from "../types";
import { statisticsApi } from "../api";
import { shanghaiDate, addDays } from "../utils/date";
export const useStatisticsStore = defineStore("statistics", () => {
  const initial = (): StatsParams => {
    const today = shanghaiDate();
    return { fromDate: addDays(today, -6), toDate: today };
  };
  const params = ref<StatsParams>(initial()),
    data = ref<Statistics | null>(null),
    loading = ref(false),
    error = ref("");
  let version = 0;
  async function refresh(): Promise<void> {
    const ticket = ++version;
    loading.value = true;
    error.value = "";
    try {
      const result = await statisticsApi.overview({ ...params.value });
      if (ticket === version) data.value = result;
    } catch (e) {
      if (ticket === version) {
        data.value = null;
        error.value = e instanceof Error ? e.message : "统计加载失败";
      }
    } finally {
      if (ticket === version) loading.value = false;
    }
  }
  function reset(): void {
    version++;
    params.value = initial();
    data.value = null;
    error.value = "";
    loading.value = false;
  }
  return { params, data, loading, error, refresh, reset };
});
