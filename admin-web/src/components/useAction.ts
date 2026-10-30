import { ref } from "vue";
import { ElMessage } from "element-plus";
import { errorMessage } from "../api/http";
export function useAction() {
  const busy = ref(false);
  async function run<T>(
    task: () => Promise<T>,
  ): Promise<{ ok: true; value: T } | { ok: false }> {
    if (busy.value) return { ok: false };
    busy.value = true;
    try {
      return { ok: true, value: await task() };
    } catch (e) {
      ElMessage.error(errorMessage(e));
      return { ok: false };
    } finally {
      busy.value = false;
    }
  }
  return { busy, run };
}
