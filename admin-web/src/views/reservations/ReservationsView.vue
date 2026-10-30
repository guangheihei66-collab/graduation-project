<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import { reservationsApi, equipmentApi, allPages } from "../../api";
import type {
  Reservation,
  ReservationStatus,
  ReservationAction,
  Equipment,
} from "../../types";
import { useAuthStore } from "../../stores/auth";
import { useStatisticsStore } from "../../stores/statistics";
import { usePage } from "../../components/usePage";
import { useAction } from "../../components/useAction";
import { useLabOptions } from "../../components/useOptions";
import { dateTime, validRange } from "../../utils/date";
import {
  reservationStatuses,
  actionLabels,
  allowedActions,
  validReason,
} from "../../utils/business";
import { confirmAction } from "../../utils/ui";
import { ApiError, errorMessage } from "../../api/http";
import Pager from "../../components/Pager.vue";
import StatusTag from "../../components/StatusTag.vue";
const auth = useAuthStore(),
  statistics = useStatisticsStore(),
  { labs, loadLabs } = useLabOptions();
const filters = reactive({
    status: undefined as ReservationStatus | undefined,
    labId: undefined as string | undefined,
    equipmentId: undefined as string | undefined,
  }),
  range = ref<string[] | null>(null),
  devices = ref<Equipment[]>([]);
const { rows, loading, error, page, load } = usePage<Reservation>((p) =>
  reservationsApi.list({
    ...p,
    ...filters,
    fromDate: range.value?.[0],
    toDate: range.value?.[1],
  }),
);
const { busy, run } = useAction(),
  detail = ref<Reservation | null>(null),
  detailOpen = ref(false),
  now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;
function actions(row: Reservation): ReservationAction[] {
  return allowedActions(auth.user?.role, row, now.value);
}
function labName(id: string): string {
  return labs.value.find((lab) => lab.id === id)?.name ?? `实验室 ${id}`;
}
async function search(): Promise<void> {
  if (range.value && !validRange(range.value)) {
    ElMessage.warning("请选择有效的预约日期范围");
    return;
  }
  await load(true);
}
async function loadDevices(): Promise<void> {
  try {
    devices.value = await allPages((p) =>
      equipmentApi.list({ page: p, size: 100, labId: filters.labId }),
    );
  } catch (e) {
    devices.value = [];
    ElMessage.error(errorMessage(e));
  }
}
function labChanged(): void {
  filters.equipmentId = undefined;
  void loadDevices();
}
function showDetail(row: Reservation): void {
  void run(async () => {
    detail.value = await reservationsApi.detail(row.id);
    detailOpen.value = true;
  });
}
const actionDialog = ref(false),
  chosen = ref<ReservationAction>("APPROVE"),
  target = ref<Reservation>(),
  reason = reactive({ text: "" }),
  reasonForm = ref<FormInstance>();
const needsReason = computed(
  () => chosen.value === "REJECT" || chosen.value === "REVOKE",
);
const reasonRules = computed<FormRules>(() => ({
  text: [
    {
      validator: (_rule, value: string, callback) =>
        !needsReason.value || validReason(value)
          ? callback()
          : callback(new Error("请填写 1—500 字的处理原因")),
      trigger: "blur",
    },
  ],
}));
function openAction(row: Reservation, action: ReservationAction): void {
  target.value = row;
  chosen.value = action;
  reason.text = "";
  actionDialog.value = true;
  reasonForm.value?.clearValidate();
}
async function perform(): Promise<void> {
  if (
    busy.value ||
    !target.value ||
    !(await reasonForm.value?.validate().catch(() => false))
  )
    return;
  void run(async () => {
    const row = target.value;
    if (!row) return;
    if (
      !(await confirmAction(
        `确认对预约 ${row.id} 执行“${actionLabels[chosen.value]}”？操作会由后端再次校验状态、时间和授权。`,
        "确认预约处理",
      ))
    )
      return;
    try {
      if (chosen.value === "APPROVE" || chosen.value === "REJECT")
        await reservationsApi.review(
          row.id,
          chosen.value,
          needsReason.value ? reason.text.trim() : undefined,
        );
      else if (chosen.value === "REVOKE")
        await reservationsApi.revoke(row.id, reason.text.trim());
      else await reservationsApi.complete(row.id);
      actionDialog.value = false;
      ElMessage.success("预约处理已提交");
    } catch (e) {
      if (e instanceof ApiError && [403, 404, 409].includes(e.status)) {
        await load();
        if (detailOpen.value) {
          try {
            detail.value = await reservationsApi.detail(row.id);
          } catch {
            detailOpen.value = false;
          }
        }
        await statistics.refresh();
      }
      throw e;
    }
    await Promise.all([load(), statistics.refresh()]);
    if (detailOpen.value) detail.value = await reservationsApi.detail(row.id);
  });
}
onMounted(() => {
  void load();
  void loadLabs();
  void loadDevices();
  timer = setInterval(() => {
    now.value = Date.now();
  }, 10000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">预约与审批</div>
      <h1>预约管理</h1>
      <p>
        {{
          auth.isSystemAdmin
            ? "查看全校预约记录与处理过程，系统管理员不参与审批。"
            : "查看授权实验室预约，及时审批并登记设备归还。"
        }}
      </p>
    </div>
    <el-button :icon="Refresh" :loading="loading" @click="load()">
      刷新列表
    </el-button>
  </div>
  <section class="panel">
    <el-form inline class="filter-panel" @submit.prevent="search">
      <el-select
        v-model="filters.status"
        clearable
        placeholder="全部预约状态"
        aria-label="预约状态筛选"
      >
        <el-option
          v-for="(label, value) in reservationStatuses"
          :key="value"
          :value="value"
          :label="label"
        />
</el-select
      ><el-select
        v-model="filters.labId"
        clearable
        filterable
        placeholder="全部实验室"
        aria-label="预约实验室筛选"
        @change="labChanged"
      >
        <el-option
          v-for="lab in labs"
          :key="lab.id"
          :value="lab.id"
          :label="lab.name"
        />
</el-select
      ><el-select
        v-model="filters.equipmentId"
        clearable
        filterable
        placeholder="全部设备"
        aria-label="预约设备筛选"
      >
        <el-option
          v-for="device in devices"
          :key="device.id"
          :value="device.id"
          :label="device.name"
        />
</el-select
      ><el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="预约开始日期"
        end-placeholder="预约结束日期"
        range-separator="至"
      /><el-button type="primary" native-type="submit">
        查询
      </el-button>
</el-form
    ><el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="table-alert"
    /><el-table :data="rows" row-key="id" v-loading="loading">
      <el-table-column
        prop="id"
        label="预约 ID"
        min-width="110"
      /><el-table-column
        prop="equipmentName"
        label="设备"
        min-width="140"
      /><el-table-column label="实验室" min-width="150">
        <template #default="scope">
          {{ labName(scope.row.labId) }}
        </template>
</el-table-column
      ><el-table-column
        prop="studentName"
        label="学生姓名"
        min-width="110"
      /><el-table-column
        prop="username"
        label="学号"
        min-width="130"
      /><el-table-column label="预约时间（北京时间）" min-width="210">
        <template #default="scope">
          <div>{{ dateTime(scope.row.startTime) }}</div>
          <span class="muted">至 {{ dateTime(scope.row.endTime) }}</span>
        </template>
</el-table-column
      ><el-table-column
        prop="purpose"
        label="用途"
        min-width="130"
        show-overflow-tooltip
      /><el-table-column label="状态" width="110">
        <template #default="scope">
          <StatusTag :value="scope.row.status" />
        </template>
</el-table-column
      ><el-table-column label="创建时间" min-width="160">
        <template #default="scope">
          {{ dateTime(scope.row.createdAt) }}
        </template>
</el-table-column
      ><el-table-column label="操作" min-width="200" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            :disabled="busy"
            @click="showDetail(scope.row)"
          >
            详情
</el-button
          ><el-button
            v-for="action in actions(scope.row)"
            :key="action"
            link
            :type="
              action === 'REJECT' || action === 'REVOKE' ? 'danger' : 'primary'
            "
            :disabled="busy"
            @click="openAction(scope.row, action)"
          >
            {{ actionLabels[action] }}
          </el-button>
        </template>
</el-table-column
      ><template #empty>
        <el-empty description="暂无符合条件的预约记录" :image-size="70" />
      </template>
</el-table
    ><Pager
      :page="page"
      @change="
        page.page = $event;
        load();
      "
      @size="
        page.size = $event;
        load(true);
      "
    />
  </section>
  <p class="page-note">
    日期按预约开始日筛选。按钮时间仅供交互判断，最终状态、时间与授权均由服务端验证。
  </p>
  <el-drawer
    v-model="detailOpen"
    title="预约详情与操作记录"
    size="min(720px, 96vw)"
  >
    <template v-if="detail">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="预约 ID">
          {{ detail.id }}
</el-descriptions-item
        ><el-descriptions-item label="设备 / 实验室">
          {{ detail.equipmentName }} /
          {{ labName(detail.labId) }}
</el-descriptions-item
        ><el-descriptions-item label="学生">
          {{ detail.studentName }}（{{
            detail.username
          }}）
</el-descriptions-item
        ><el-descriptions-item label="预约时间">
          {{ dateTime(detail.startTime) }} 至
          {{ dateTime(detail.endTime) }}
</el-descriptions-item
        ><el-descriptions-item label="用途">
          {{ detail.purpose }}
</el-descriptions-item
        ><el-descriptions-item label="状态">
          <StatusTag :value="detail.status" />
        </el-descriptions-item>
      </el-descriptions>
      <h3>操作日志</h3>
      <el-timeline>
        <el-timeline-item
          v-for="log in detail.logs"
          :key="log.id"
          :timestamp="dateTime(log.createdAt)"
          placement="top"
        >
          <strong>{{
            actionLabels[log.action as keyof typeof actionLabels] || log.action
          }}</strong>
          <p class="muted">
            {{
              log.operatorId === null
                ? "系统操作"
                : `操作人 ID：${log.operatorId}`
            }}
            ·
            {{ log.fromStatus ? reservationStatuses[log.fromStatus] : "新建" }}
            → {{ reservationStatuses[log.toStatus] }}
          </p>
          <p v-if="log.reason">{{ log.reason }}</p>
        </el-timeline-item>
</el-timeline
      ><el-empty
        v-if="!detail.logs.length"
        description="暂无操作日志"
        :image-size="60"
      />
    </template>
  </el-drawer>
  <el-dialog
    v-model="actionDialog"
    :title="actionLabels[chosen]"
    width="540px"
    :close-on-click-modal="false"
    :show-close="!busy"
    :close-on-press-escape="!busy"
  >
    <p>
      预约 {{ target?.id }} · {{ target?.equipmentName }} ·
      {{ target?.studentName }}
    </p>
    <el-form
      ref="reasonForm"
      :model="reason"
      :rules="reasonRules"
      label-position="top"
    >
      <el-form-item v-if="needsReason" label="处理原因" prop="text">
        <el-input
          v-model="reason.text"
          type="textarea"
          :rows="4"
          :maxlength="500"
          show-word-limit
          placeholder="请填写明确原因"
        />
      </el-form-item>
      <p v-else class="muted">
        {{
          chosen === "COMPLETE"
            ? "请确认设备已归还，且预约结束时间已经到达。"
            : "同意后将进入已通过状态，请核实设备与实验用途。"
        }}
      </p>
</el-form
    ><template #footer>
      <el-button :disabled="busy" @click="actionDialog = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        :disabled="busy"
        @click="perform"
      >
        提交处理
      </el-button>
    </template>
  </el-dialog>
</template>
