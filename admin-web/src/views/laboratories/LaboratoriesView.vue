<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { Plus, Search, Refresh } from "@element-plus/icons-vue";
import { labsApi, usersApi, allPages } from "../../api";
import type { Lab, LabEditable, ManagedUser } from "../../types";
import { useAuthStore } from "../../stores/auth";
import { usePage } from "../../components/usePage";
import { useAction } from "../../components/useAction";
import { changedFields, nullableText } from "../../utils/business";
import { dateTime } from "../../utils/date";
import { confirmAction } from "../../utils/ui";
import Pager from "../../components/Pager.vue";
import StatusTag from "../../components/StatusTag.vue";
import DetailsDrawer from "../../components/DetailsDrawer.vue";
const auth = useAuthStore(),
  filters = reactive({ name: "", enabled: undefined as number | undefined });
const { rows, loading, error, page, load } = usePage<Lab>((p) =>
  labsApi.list({
    ...p,
    name: filters.name.trim() || undefined,
    enabled: filters.enabled,
  }),
);
const { busy, run } = useAction(),
  dialog = ref(false),
  form = ref<FormInstance>(),
  editing = ref<Lab | null>(null);
const model = reactive({ code: "", name: "", location: "", description: "" });
const rules: FormRules = {
  code: [
    {
      required: true,
      whitespace: true,
      max: 32,
      message: "请输入不超过 32 字符的编号",
      trigger: "blur",
    },
  ],
  name: [
    {
      required: true,
      whitespace: true,
      max: 100,
      message: "请输入不超过 100 字符的名称",
      trigger: "blur",
    },
  ],
  location: [
    {
      required: true,
      whitespace: true,
      max: 200,
      message: "请输入不超过 200 字符的地点",
      trigger: "blur",
    },
  ],
  description: [{ max: 1000, message: "描述最多 1000 字符", trigger: "blur" }],
};
function openEdit(row?: Lab): void {
  editing.value = row ?? null;
  Object.assign(model, {
    code: row?.code ?? "",
    name: row?.name ?? "",
    location: row?.location ?? "",
    description: row?.description ?? "",
  });
  dialog.value = true;
  form.value?.clearValidate();
}
async function save(): Promise<void> {
  if (busy.value || !(await form.value?.validate().catch(() => false))) return;
  const result = await run(async () => {
    const body: LabEditable = {
      name: model.name.trim(),
      location: model.location.trim(),
      description: nullableText(model.description),
    };
    if (editing.value) {
      const patch = changedFields<LabEditable>(editing.value, body, [
        "name",
        "location",
        "description",
      ]);
      if (!Object.keys(patch).length) {
        ElMessage.info("没有修改内容");
        return;
      }
      await labsApi.update(editing.value.id, patch);
    } else await labsApi.create({ ...body, code: model.code.trim() });
    ElMessage.success("实验室已保存");
    await load();
  });
  if (result.ok) dialog.value = false;
}
function toggle(row: Lab): void {
  void run(async () => {
    if (
      !(await confirmAction(
        `确认${row.enabled ? "停用" : "启用"}实验室“${row.name}”？停用将影响学生查询和预约，有有效预约时服务端会阻止操作。`,
      ))
    )
      return;
    await labsApi.update(row.id, { enabled: row.enabled ? 0 : 1 });
    ElMessage.success("实验室状态已更新");
    await load();
  });
}
const detail = ref<Lab | null>(null),
  detailOpen = ref(false);
function showDetail(row: Lab): void {
  void run(async () => {
    detail.value = await labsApi.detail(row.id);
    detailOpen.value = true;
  });
}
const detailFields = computed(() =>
  detail.value
    ? [
        { label: "实验室编号", value: detail.value.code },
        { label: "名称", value: detail.value.name },
        { label: "地点", value: detail.value.location },
        { label: "描述", value: detail.value.description },
        { label: "状态", value: detail.value.enabled ? "启用" : "停用" },
        { label: "创建时间", value: dateTime(detail.value.createdAt) },
      ]
    : [],
);
const assignmentOpen = ref(false),
  assignmentLab = ref<Lab>(),
  candidates = ref<ManagedUser[]>([]),
  selectedIds = ref<string[]>([]),
  previousNames = ref("");
const invalidIds = computed(() =>
  selectedIds.value.filter(
    (id) => !candidates.value.some((item) => item.id === id),
  ),
);
function openAssignment(row: Lab): void {
  void run(async () => {
    const [assigned, eligible] = await Promise.all([
      labsApi.admins(row.id),
      allPages((p) =>
        usersApi.list({ page: p, size: 100, role: "LAB_ADMIN", enabled: 1 }),
      ),
    ]);
    candidates.value = eligible;
    selectedIds.value = assigned.map((item) => item.id);
    previousNames.value =
      assigned.map((item) => item.realName).join("、") || "暂无";
    assignmentLab.value = row;
    assignmentOpen.value = true;
  });
}
function saveAssignment(): void {
  void run(async () => {
    if (!assignmentLab.value) return;
    if (invalidIds.value.length) {
      ElMessage.warning("存在停用或不可用账号，请先移除后再提交");
      return;
    }
    if (
      !(await confirmAction(
        `将整体替换“${assignmentLab.value.name}”的管理员为 ${selectedIds.value.length} 人，未选择的原管理员会失去授权。确认提交？`,
        "替换实验室授权",
      ))
    )
      return;
    await labsApi.assign(assignmentLab.value.id, selectedIds.value);
    ElMessage.success("管理员授权已替换");
    assignmentOpen.value = false;
    await load();
  });
}
onMounted(() => {
  void load();
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">空间与资源</div>
      <h1>实验室管理</h1>
      <p>维护实验室信息与开放状态，规范资源管理范围。</p>
    </div>
    <el-button
      v-if="auth.isSystemAdmin"
      type="primary"
      :icon="Plus"
      @click="openEdit()"
    >
      新增实验室
    </el-button>
  </div>
  <section class="panel">
    <el-form class="filter-panel" inline @submit.prevent="load(true)">
      <el-input
        v-model="filters.name"
        placeholder="搜索实验室名称"
        clearable
        :prefix-icon="Search"
        aria-label="实验室名称搜索"
      /><el-select
        v-model="filters.enabled"
        placeholder="全部状态"
        clearable
        aria-label="实验室状态筛选"
      >
        <el-option label="启用" :value="1" /><el-option
          label="停用"
          :value="0"
        />
</el-select
      ><el-button type="primary" native-type="submit">查询</el-button
      ><el-button :icon="Refresh" :loading="loading" @click="load()">
        刷新
      </el-button>
</el-form
    ><el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="table-alert"
    /><el-table :data="rows" v-loading="loading" row-key="id">
      <el-table-column
        prop="code"
        label="实验室编号"
        min-width="150"
      /><el-table-column
        prop="name"
        label="实验室名称"
        min-width="180"
      /><el-table-column
        prop="location"
        label="地点"
        min-width="180"
      /><el-table-column label="状态" width="100">
        <template #default="scope">
          <StatusTag :value="scope.row.enabled" />
        </template>
</el-table-column
      ><el-table-column
        prop="description"
        label="说明"
        min-width="160"
        show-overflow-tooltip
      /><el-table-column label="操作" min-width="260" fixed="right">
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
            link
            type="primary"
            :disabled="busy"
            @click="openEdit(scope.row)"
          >
            编辑
</el-button
          ><el-button
            v-if="auth.isSystemAdmin"
            link
            type="primary"
            :disabled="busy"
            @click="openAssignment(scope.row)"
          >
            分配管理员
</el-button
          ><el-button
            link
            :type="scope.row.enabled ? 'danger' : 'success'"
            :disabled="busy"
            @click="toggle(scope.row)"
          >
            {{ scope.row.enabled ? "停用" : "启用" }}
          </el-button>
        </template>
</el-table-column
      ><template #empty>
        <el-empty description="暂无符合条件的实验室" :image-size="70" />
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
  <el-dialog
    v-model="dialog"
    :title="editing ? '编辑实验室' : '新增实验室'"
    width="560px"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @closed="form?.resetFields()"
  >
    <el-form ref="form" :model="model" :rules="rules" label-position="top">
      <el-form-item label="实验室编号" prop="code">
        <el-input
          v-model="model.code"
          :disabled="!!editing"
          :maxlength="32"
        />
</el-form-item
      ><el-form-item label="实验室名称" prop="name">
        <el-input v-model="model.name" :maxlength="100" />
</el-form-item
      ><el-form-item label="地点" prop="location">
        <el-input v-model="model.location" :maxlength="200" />
</el-form-item
      ><el-form-item label="描述" prop="description">
        <el-input
          v-model="model.description"
          type="textarea"
          :rows="3"
          :maxlength="1000"
          show-word-limit
          placeholder="留空并保存可清空描述"
        />
      </el-form-item>
</el-form
    ><template #footer>
      <el-button :disabled="busy" @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="busy" :disabled="busy" @click="save">
        保存
      </el-button>
    </template>
  </el-dialog>
  <el-dialog
    v-model="assignmentOpen"
    title="分配实验室管理员"
    width="580px"
    :close-on-click-modal="false"
  >
    <el-alert
      title="此操作将整体替换管理员列表，未选择的原管理员会被撤销授权。"
      type="warning"
      :closable="false"
      show-icon
    />
    <p>实验室：{{ assignmentLab?.name }}</p>
    <p class="muted">当前管理员：{{ previousNames }}</p>
    <el-select
      v-model="selectedIds"
      :multiple-limit="100"
      multiple
      filterable
      placeholder="选择已启用的实验室管理员"
      class="full-width"
      aria-label="实验室管理员选择"
    >
      <el-option
        v-for="item in candidates"
        :key="item.id"
        :value="item.id"
        :label="`${item.realName}（${item.username}）`"
      />
    </el-select>
    <div v-if="invalidIds.length" class="form-alert">
      <el-alert
        title="当前授权含停用或不可用账号，提交前需要明确移除。"
        type="warning"
        :closable="false"
      /><el-button
        @click="
          selectedIds = selectedIds.filter((id) => !invalidIds.includes(id))
        "
      >
        移除不可用账号
      </el-button>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="assignmentOpen = false">
        取消
</el-button
      ><el-button
        type="primary"
        :disabled="busy"
        :loading="busy"
        @click="saveAssignment"
      >
        替换授权
      </el-button>
    </template>
</el-dialog
  ><DetailsDrawer
    v-model="detailOpen"
    title="实验室详情"
    :fields="detailFields"
  />
</template>
