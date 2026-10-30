<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  DataAnalysis,
  Clock,
  CircleCheck,
  Refresh,
} from "@element-plus/icons-vue";
import { useStatisticsStore } from "../../stores/statistics";
import { useAuthStore } from "../../stores/auth";
import { useLabOptions } from "../../components/useOptions";
import StatisticsChart from "../../components/StatisticsChart.vue";
import { validRange } from "../../utils/date";
import { ElMessage } from "element-plus";
const store = useStatisticsStore(),
  auth = useAuthStore(),
  { labs, loadLabs } = useLabOptions();
const range = ref<string[]>([store.params.fromDate, store.params.toDate]),
  labId = ref<string | undefined>(store.params.labId);
const line = computed(
  () =>
    store.data?.dailyTrend.map((item) => ({
      label: item.date.slice(5),
      value: item.count,
    })) ?? [],
);
const bars = computed(
  () =>
    store.data?.topEquipment.map((item) => ({
      label: item.equipmentName,
      value: item.count,
    })) ?? [],
);
async function refresh(): Promise<void> {
  if (!validRange(range.value, 90)) {
    ElMessage.warning("请选择有效日期范围，最多包含 90 个自然日");
    return;
  }
  store.params = {
    fromDate: range.value[0]!,
    toDate: range.value[1]!,
    labId: labId.value,
  };
  await store.refresh();
}
onMounted(() => {
  void loadLabs();
  void refresh();
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">数据概览</div>
      <h1>统计看板</h1>
      <p>
        {{
          auth.isSystemAdmin
            ? "全局预约动态与设备使用需求，一目了然。"
            : "掌握授权实验室的预约动态，及时处理待办。"
        }}
      </p>
    </div>
    <el-button :icon="Refresh" :loading="store.loading" @click="refresh">
      刷新数据
    </el-button>
  </div>
  <section class="panel filter-panel">
    <span class="filter-label">统计范围</span
    ><el-date-picker
      v-model="range"
      type="daterange"
      value-format="YYYY-MM-DD"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      range-separator="至"
      :clearable="false"
    /><el-select
      v-model="labId"
      clearable
      placeholder="全部授权实验室"
      aria-label="统计实验室"
    >
      <el-option
        v-for="lab in labs"
        :key="lab.id"
        :label="lab.name"
        :value="lab.id"
      />
</el-select
    ><el-button type="primary" :disabled="store.loading" @click="refresh">
      查询统计
</el-button
    ><small>按创建日期统计 · 北京时间</small>
  </section>
  <el-alert
    v-if="store.error"
    :title="store.error"
    type="error"
    :closable="false"
    show-icon
    class="page-alert"
  />
  <div class="stat-cards" v-loading="store.loading">
    <article class="stat-card">
      <div>
        <span>预约总数</span><strong>{{ store.data?.totalCount ?? "—" }}</strong
        ><small>所选范围内创建的全部预约</small>
      </div>
      <el-icon class="stat-icon blue"><DataAnalysis /></el-icon>
    </article>
    <article class="stat-card">
      <div>
        <span>待审批预约</span
        ><strong>{{ store.data?.pendingCount ?? "—" }}</strong
        ><small>等待实验室管理员处理</small>
      </div>
      <el-icon class="stat-icon amber"><Clock /></el-icon>
    </article>
    <article class="stat-card">
      <div>
        <span>当前已通过</span
        ><strong>{{ store.data?.approvedCount ?? "—" }}</strong
        ><small>当前状态为已通过的预约</small>
      </div>
      <el-icon class="stat-icon green"><CircleCheck /></el-icon>
    </article>
  </div>
  <div class="chart-grid">
    <section class="panel" v-loading="store.loading">
      <div class="panel-heading">
        <h2>每日预约趋势</h2>
        <span>按创建自然日统计</span>
      </div>
      <StatisticsChart kind="line" :data="line" />
    </section>
    <section class="panel" v-loading="store.loading">
      <div class="panel-heading">
        <h2>热门设备</h2>
        <span>已通过及已完成 · 最多十台</span>
      </div>
      <StatisticsChart kind="bar" :data="bars" />
    </section>
  </div>
  <p class="page-note">
    统计数量反映预约需求，不代表设备真实使用率。数据以服务端查询结果为准。
  </p>
</template>
