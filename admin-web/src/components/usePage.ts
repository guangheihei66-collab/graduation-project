import { ref, shallowRef, reactive } from "vue";
import type { Page, PageParams } from "../types";
import { errorMessage } from "../api/http";
export function usePage<T>(fetcher: (page: PageParams) => Promise<Page<T>>) {
  const rows = shallowRef<T[]>([]),
    loading = ref(false),
    error = ref(""),
    page = reactive({ page: 1, size: 20, total: 0 });
  let sequence = 0;
  async function load(reset = false): Promise<void> {
    if (reset) page.page = 1;
    const ticket = ++sequence;
    loading.value = true;
    error.value = "";
    try {
      const data = await fetcher({ page: page.page, size: page.size });
      if (ticket === sequence) {
        rows.value = data.records;
        page.total = data.total;
        if (page.page > 1 && !data.records.length && data.total > 0) {
          page.page--;
          await load();
        }
      }
    } catch (e) {
      if (ticket === sequence) {
        error.value = errorMessage(e);
        rows.value = [];
        page.total = 0;
      }
    } finally {
      if (ticket === sequence) loading.value = false;
    }
  }
  return { rows, loading, error, page, load };
}
