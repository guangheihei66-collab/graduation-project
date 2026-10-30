<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { Plus, Refresh, Search } from "@element-plus/icons-vue";
import { equipmentApi } from "../../api";
import type {
  Equipment,
  EquipmentEditable,
  Category,
  EquipmentStatus,
} from "../../types";
import { usePage } from "../../components/usePage";
import { useAction } from "../../components/useAction";
import { useLabOptions } from "../../components/useOptions";
import {
  categories,
  equipmentStatuses,
  changedFields,
  nullableText,
} from "../../utils/business";
import { confirmAction } from "../../utils/ui";
import { dateTime } from "../../utils/date";
import Pager from "../../components/Pager.vue";
import StatusTag from "../../components/StatusTag.vue";
import DetailsDrawer from "../../components/DetailsDrawer.vue";
const filters = reactive({
  keyword: "",
  labId: undefined as string | undefined,
  category: undefined as Category | undefined,
  status: undefined as EquipmentStatus | undefined,
});
const { labs, loadLabs } = useLabOptions(),
  { rows, loading, error, page, load } = usePage<Equipment>((p) =>
    equipmentApi.list({
      ...p,
      ...filters,
      keyword: filters.keyword.trim() || undefined,
    }),
  );
const { busy, run } = useAction(),
  dialog = ref(false),
  form = ref<FormInstance>(),
  editing = ref<Equipment | null>(null);
const model = reactive({
  labId: "",
  assetNo: "",
  name: "",
  category: "INSTRUMENT" as Category,
  model: "",
  location: "",
  description: "",
});
const rules: FormRules = {
  labId: [{ required: true, message: "请选择所属实验室", trigger: "change" }],
  assetNo: [
    {
      required: true,
      whitespace: true,
      max: 64,
      message: "请输入不超过 64 字符的资产编号",
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
  category: [{ required: true, message: "请选择分类", trigger: "change" }],
  model: [{ max: 100, message: "型号最多 100 字符", trigger: "blur" }],
  location: [
    {
      required: true,
      whitespace: true,
      max: 200,
      message: "请输入不超过 200 字符的位置",
      trigger: "blur",
    },
  ],
  description: [{ max: 1000, message: "描述最多 1000 字符", trigger: "blur" }],
};
function openEdit(row?: Equipment): void {
  editing.value = row ?? null;
  Object.assign(model, {
    labId: row?.labId ?? "",
    assetNo: row?.assetNo ?? "",
    name: row?.name ?? "",
    category: row?.category ?? "INSTRUMENT",
    model: row?.model ?? "",
    location: row?.location ?? "",
    description: row?.description ?? "",
  });
  dialog.value = true;
  form.value?.clearValidate();
}
async function save(): Promise<void> {
  if (busy.value || !(await form.value?.validate().catch(() => false))) return;
  const result = await run(async () => {
    const body: EquipmentEditable = {
      name: model.name.trim(),
      category: model.category,
      model: nullableText(model.model),
      location: model.location.trim(),
      description: nullableText(model.description),
    };
    if (editing.value) {
      const patch = changedFields<EquipmentEditable>(editing.value, body, [
        "name",
        "category",
        "model",
        "location",
        "description",
      ]);
      if (!Object.keys(patch).length) {
        ElMessage.info("没有修改内容");
        return;
      }
      await equipmentApi.update(editing.value.id, patch);
    } else
      await equipmentApi.create({
        ...body,
        labId: model.labId,
        assetNo: model.assetNo.trim(),
      });
    ElMessage.success("设备已保存");
    await load();
  });
  if (result.ok) dialog.value = false;
}
function changeStatus(row: Equipment, status: EquipmentStatus): void {
  void run(async () => {
    if (
      !(await confirmAction(
        `确认将“${row.name}”设为${equipmentStatuses[status]}？存在有效预约时，服务端会阻止维修或停用。`,
      ))
    )
      return;
    await equipmentApi.update(row.id, { status });
    ElMessage.success("设备状态已更新");
    await load();
  });
}
const detail = ref<Equipment | null>(null),
  detailOpen = ref(false);
function showDetail(row: Equipment): void {
  void run(async () => {
    detail.value = await equipmentApi.detail(row.id);
    detailOpen.value = true;
  });
}
const fields = computed(() =>
  detail.value
    ? [
        { label: "资产编号", value: detail.value.assetNo },
        { label: "设备名称", value: detail.value.name },
        { label: "实验室", value: detail.value.labName },
        { label: "分类", value: categories[detail.value.category] },
        { label: "型号", value: detail.value.model },
        { label: "位置", value: detail.value.location },
        { label: "状态", value: equipmentStatuses[detail.value.status] },
        { label: "描述", value: detail.value.description },
        { label: "更新时间", value: dateTime(detail.value.updatedAt) },
      ]
    : [],
);
onMounted(() => {
  void load();
  void loadLabs();
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">设备资产</div>
      <h1>设备管理</h1>
      <p>维护设备档案与可用状态，支持实验资源有序共享。</p>
    </div>
    <el-button type="primary" :icon="Plus" @click="openEdit()">
      新增设备
    </el-button>
  </div>
  <section class="panel">
    <el-form inline class="filter-panel" @submit.prevent="load(true)">
      <el-input
        v-model="filters.keyword"
        placeholder="设备名称 / 资产编号"
        clearable
        :prefix-icon="Search"
        aria-label="设备搜索"
      /><el-select
        v-model="filters.labId"
        placeholder="全部实验室"
        clearable
        filterable
        aria-label="设备实验室筛选"
      >
        <el-option
          v-for="lab in labs"
          :key="lab.id"
          :value="lab.id"
          :label="lab.name"
        />
</el-select
      ><el-select
        v-model="filters.category"
        placeholder="全部分类"
        clearable
        aria-label="设备分类筛选"
      >
        <el-option
          v-for="(label, value) in categories"
          :key="value"
          :value="value"
          :label="label"
        />
</el-select
      ><el-select
        v-model="filters.status"
        placeholder="全部状态"
        clearable
        aria-label="设备状态筛选"
      >
        <el-option
          v-for="(label, value) in equipmentStatuses"
          :key="value"
          :value="value"
          :label="label"
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
      show-icon
      :closable="false"
      class="table-alert"
    /><el-table :data="rows" row-key="id" v-loading="loading">
      <el-table-column
        prop="assetNo"
        label="资产编号"
        min-width="150"
      /><el-table-column
        prop="name"
        label="设备名称"
        min-width="170"
      /><el-table-column
        prop="labName"
        label="所属实验室"
        min-width="150"
      /><el-table-column label="分类" width="100">
        <template #default="scope">
          {{ categories[scope.row.category as Category] }}
        </template>
</el-table-column
      ><el-table-column
        prop="model"
        label="型号"
        min-width="120"
      /><el-table-column label="状态" width="100">
        <template #default="scope">
          <StatusTag :value="scope.row.status" />
        </template>
</el-table-column
      ><el-table-column label="操作" min-width="230" fixed="right">
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
          ><el-dropdown
            trigger="click"
            :disabled="busy"
            @command="changeStatus(scope.row, $event)"
          >
            <el-button link type="primary" :disabled="busy">变更状态</el-button
            ><template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="(label, value) in equipmentStatuses"
                  :key="value"
                  :command="value"
                  :disabled="scope.row.status === value"
                >
                  设为{{ label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
</el-table-column
      ><template #empty>
        <el-empty description="暂无符合条件的设备" :image-size="70" />
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
    :title="editing ? '编辑设备' : '新增设备'"
    width="600px"
    :close-on-click-modal="false"
    :show-close="!busy"
    :close-on-press-escape="!busy"
  >
    <el-form ref="form" :model="model" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="所属实验室" prop="labId">
          <el-select
            v-model="model.labId"
            :disabled="!!editing"
            filterable
            placeholder="选择实验室"
          >
            <el-option
              v-for="lab in labs"
              :key="lab.id"
              :value="lab.id"
              :label="lab.name"
            />
          </el-select>
</el-form-item
        ><el-form-item label="资产编号" prop="assetNo">
          <el-input
            v-model="model.assetNo"
            :disabled="!!editing"
            :maxlength="64"
          />
</el-form-item
        ><el-form-item label="设备名称" prop="name">
          <el-input v-model="model.name" :maxlength="100" />
</el-form-item
        ><el-form-item label="分类" prop="category">
          <el-select v-model="model.category">
            <el-option
              v-for="(label, value) in categories"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
</el-form-item
        ><el-form-item label="型号" prop="model">
          <el-input
            v-model="model.model"
            :maxlength="100"
            placeholder="留空可清空型号"
          />
</el-form-item
        ><el-form-item label="存放位置" prop="location">
          <el-input v-model="model.location" :maxlength="200" />
        </el-form-item>
      </div>
      <el-form-item label="描述" prop="description">
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
      ><el-button type="primary" :disabled="busy" :loading="busy" @click="save">
        保存
      </el-button>
    </template>
</el-dialog
  ><DetailsDrawer v-model="detailOpen" title="设备详情" :fields="fields" />
</template>
