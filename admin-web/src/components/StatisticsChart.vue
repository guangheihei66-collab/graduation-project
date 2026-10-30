<script setup lang="ts">
import {
  computed,
  nextTick,
  onMounted,
  onBeforeUnmount,
  ref,
  watch,
} from "vue";
import * as echarts from "echarts/core";
import { LineChart, BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
echarts.use([
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  CanvasRenderer,
]);
const props = defineProps<{
  kind: "line" | "bar";
  data: { label: string; value: number }[];
}>();
const root = ref<HTMLDivElement>(),
  hasData = computed(() => props.data.some((item) => item.value > 0));
let chart: echarts.EChartsType | undefined,
  observer: ResizeObserver | undefined;
function resize(): void {
  chart?.resize();
}
async function render(): Promise<void> {
  await nextTick();
  if (!root.value || !hasData.value) {
    chart?.clear();
    return;
  }
  chart ??= echarts.init(root.value);
  chart.setOption(
    {
      tooltip: { trigger: "axis", renderMode: "richText" },
      grid: {
        top: 22,
        left: 42,
        right: 24,
        bottom: props.kind === "bar" ? 70 : 38,
      },
      xAxis: {
        type: "category",
        data: props.data.map((item) => item.label),
        axisLine: { lineStyle: { color: "#e5ebf3" } },
        axisTick: { show: false },
        axisLabel: {
          color: "#6b7b91",
          rotate: props.kind === "bar" ? 20 : 0,
          hideOverlap: true,
        },
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: "#6b7b91" },
        splitLine: { lineStyle: { color: "#edf1f7", type: "dashed" } },
      },
      series: [
        {
          type: props.kind,
          data: props.data.map((item) => item.value),
          smooth: true,
          symbolSize: 7,
          barMaxWidth: 32,
          itemStyle: {
            color: "#3478ed",
            borderRadius: props.kind === "bar" ? [6, 6, 0, 0] : 0,
          },
          lineStyle: { width: 3 },
          areaStyle:
            props.kind === "line"
              ? {
                  color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: "#3478ed30" },
                    { offset: 1, color: "#3478ed00" },
                  ]),
                }
              : undefined,
        },
      ],
    },
    true,
  );
}
onMounted(() => {
  void render();
  if (typeof ResizeObserver !== "undefined" && root.value) {
    observer = new ResizeObserver(resize);
    observer.observe(root.value);
  }
  window.addEventListener("resize", resize);
});
watch(
  () => props.data,
  () => {
    void render();
  },
  { deep: true },
);
onBeforeUnmount(() => {
  observer?.disconnect();
  window.removeEventListener("resize", resize);
  chart?.dispose();
  chart = undefined;
});
</script>
<template>
  <div class="chart-wrap">
    <div
      v-show="hasData"
      ref="root"
      class="chart"
      role="img"
      :aria-label="kind === 'line' ? '每日预约趋势' : '热门设备预约数量'"
    ></div>
    <el-empty
      v-if="!hasData"
      description="该范围暂无预约数据"
      :image-size="90"
    />
  </div>
</template>
