import { describe, it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { nextTick } from "vue";
import Pager from "../src/components/Pager.vue";
import StatusTag from "../src/components/StatusTag.vue";
import { useAction } from "../src/components/useAction";
import { usePage } from "../src/components/usePage";
vi.mock("element-plus", () => ({ ElMessage: { error: vi.fn() } }));
describe("列表及操作基础组件", () => {
  it("分页展示记录数并传递数字页码", () => {
    const wrapper = mount(Pager, {
      props: { page: { page: 1, size: 20, total: 31 } },
      global: { stubs: { ElPagination: true } },
    });
    expect(wrapper.text()).toContain("31");
    wrapper
      .findComponent({ name: "ElPagination" })
      .vm.$emit("current-change", 2);
    expect(wrapper.emitted("change")).toEqual([[2]]);
  });
  it("状态标签显示中文", () => {
    const wrapper = mount(StatusTag, {
      props: { value: "PENDING" },
      global: { stubs: { ElTag: { template: "<span><slot /></span>" } } },
    });
    expect(wrapper.text()).toBe("待审批");
  });
  it("请求未完成时第二次写入被拦截", async () => {
    const action = useAction();
    let finish: (() => void) | undefined;
    const task = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    const first = action.run(task);
    expect(action.busy.value).toBe(true);
    expect(await action.run(task)).toEqual({ ok: false });
    expect(task).toHaveBeenCalledOnce();
    finish?.();
    await first;
    expect(action.busy.value).toBe(false);
  });
  it("失败操作释放锁并不返回成功", async () => {
    const action = useAction();
    expect(
      await action.run(async () => {
        throw new Error("失败");
      }),
    ).toEqual({ ok: false });
    expect(action.busy.value).toBe(false);
  });
  it("列表使用真实分页响应，不把ID转成数字", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      records: [{ id: "9007199254740993" }],
      total: 1,
      page: 1,
      size: 20,
    });
    const list = usePage<{ id: string }>(fetcher);
    await list.load();
    expect(list.rows.value[0]?.id).toBe("9007199254740993");
    expect(fetcher).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(list.page.total).toBe(1);
  });
  it("过时列表响应不能覆盖较新的查询", async () => {
    let old:
      | ((v: {
          records: { id: string }[];
          total: number;
          page: number;
          size: number;
        }) => void)
      | undefined;
    const fetch = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            old = resolve;
          }),
      )
      .mockResolvedValueOnce({
        records: [{ id: "new" }],
        total: 1,
        page: 1,
        size: 20,
      });
    const list = usePage<{ id: string }>(fetch);
    const first = list.load();
    await list.load();
    old?.({ records: [{ id: "old" }], total: 1, page: 1, size: 20 });
    await first;
    await nextTick();
    await flushPromises();
    expect(list.rows.value[0]?.id).toBe("new");
  });
});
