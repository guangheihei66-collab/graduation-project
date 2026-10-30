import { it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
const mocks = vi.hoisted(() => ({
  setOption: vi.fn(),
  resize: vi.fn(),
  clear: vi.fn(),
  dispose: vi.fn(),
  init: vi.fn(),
}));
vi.mock("echarts/core", () => ({
  use: vi.fn(),
  init: mocks.init,
  graphic: { LinearGradient: class {} },
}));
vi.mock("echarts/charts", () => ({ LineChart: {}, BarChart: {} }));
vi.mock("echarts/components", () => ({
  GridComponent: {},
  TooltipComponent: {},
}));
vi.mock("echarts/renderers", () => ({ CanvasRenderer: {} }));
import StatisticsChart from "../src/components/StatisticsChart.vue";
it("图表空数据明确显示空状态，不生成随机数据", async () => {
  mocks.init.mockReturnValue(mocks);
  const wrapper = mount(StatisticsChart, {
    props: { kind: "line", data: [] },
    global: {
      stubs: {
        ElEmpty: {
          props: ["description"],
          template: "<div>{{ description }}</div>",
        },
      },
    },
  });
  await flushPromises();
  expect(wrapper.text()).toContain("暂无预约数据");
  expect(mocks.init).not.toHaveBeenCalled();
  wrapper.unmount();
});
it("有数据时绘制，窗口变化重绘，销毁时释放实例", async () => {
  mocks.init.mockReturnValue(mocks);
  const wrapper = mount(StatisticsChart, {
    props: { kind: "line", data: [{ label: "11-02", value: 3 }] },
    global: { stubs: { ElEmpty: true } },
  });
  await flushPromises();
  expect(mocks.setOption).toHaveBeenCalledOnce();
  window.dispatchEvent(new Event("resize"));
  expect(mocks.resize).toHaveBeenCalledOnce();
  wrapper.unmount();
  expect(mocks.dispose).toHaveBeenCalledOnce();
});
