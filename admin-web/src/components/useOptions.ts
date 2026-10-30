import { ref } from "vue";
import { allPages, labsApi } from "../api";
import type { Lab } from "../types";
import { ElMessage } from "element-plus";
import { errorMessage } from "../api/http";
export function useLabOptions() {
  const labs = ref<Lab[]>([]);
  async function loadLabs(): Promise<void> {
    try {
      labs.value = await allPages((page) => labsApi.list({ page, size: 100 }));
    } catch (e) {
      ElMessage.error(errorMessage(e));
    }
  }
  return { labs, loadLabs };
}
