<script setup lang="ts">
import { reactive, ref, onMounted } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { Plus, Search, Refresh } from "@element-plus/icons-vue";
import type { ManagedUser, Role } from "../../types";
import { usersApi } from "../../api";
import { usePage } from "../../components/usePage";
import { useAction } from "../../components/useAction";
import { useAuthStore } from "../../stores/auth";
import { roles, validPassword } from "../../utils/business";
import { confirmAction } from "../../utils/ui";
import Pager from "../../components/Pager.vue";
import StatusTag from "../../components/StatusTag.vue";
const auth = useAuthStore(),
  filters = reactive({
    username: "",
    role: undefined as Role | undefined,
    enabled: undefined as number | undefined,
  });
const { rows, loading, error, page, load } = usePage<ManagedUser>((p) =>
  usersApi.list({
    ...p,
    ...filters,
    username: filters.username.trim() || undefined,
  }),
);
const { busy, run } = useAction(),
  dialog = ref(false),
  form = ref<FormInstance>(),
  editing = ref<ManagedUser | null>(null);
const model = reactive({
  username: "",
  realName: "",
  role: "STUDENT" as Role,
  initialPassword: "",
});
const passwordRule: FormRules[string] = [
  {
    validator: (_rule, value: string, callback) =>
      validPassword(value)
        ? callback()
        : callback(new Error("密码需 8—64 字符，且 UTF-8 不超过 72 字节")),
    trigger: "blur",
  },
];
const rules: FormRules = {
  username: [
    {
      required: true,
      max: 64,
      whitespace: true,
      message: "请输入不超过 64 字符的账号",
      trigger: "blur",
    },
  ],
  realName: [
    {
      required: true,
      max: 50,
      whitespace: true,
      message: "请输入不超过 50 字符的姓名",
      trigger: "blur",
    },
  ],
  role: [{ required: true, message: "请选择角色", trigger: "change" }],
  initialPassword: passwordRule,
};
function openEdit(row?: ManagedUser): void {
  editing.value = row ?? null;
  Object.assign(model, {
    username: row?.username ?? "",
    realName: row?.realName ?? "",
    role: row?.role ?? "STUDENT",
    initialPassword: "",
  });
  dialog.value = true;
  form.value?.clearValidate();
}
async function save(): Promise<void> {
  if (busy.value || !(await form.value?.validate().catch(() => false))) return;
  const result = await run(async () => {
    if (editing.value) {
      if (editing.value.realName === model.realName.trim()) {
        ElMessage.info("没有修改内容");
        return;
      }
      await usersApi.update(editing.value.id, {
        realName: model.realName.trim(),
      });
    } else
      await usersApi.create({
        username: model.username.trim(),
        realName: model.realName.trim(),
        role: model.role,
        initialPassword: model.initialPassword,
      });
    model.initialPassword = "";
    ElMessage.success("用户已保存");
    await load();
    if (editing.value?.id === auth.user?.id) await auth.restore();
  });
  if (result.ok) dialog.value = false;
}
function toggle(row: ManagedUser): void {
  if (row.id === auth.user?.id) {
    ElMessage.warning("不能停用当前登录账号");
    return;
  }
  void run(async () => {
    if (
      !(await confirmAction(
        `确认${row.enabled ? "停用" : "启用"}账号“${row.username}”？停用后该用户无法继续访问受保护接口。`,
      ))
    )
      return;
    await usersApi.update(row.id, { enabled: row.enabled ? 0 : 1 });
    ElMessage.success("账号状态已更新");
    await load();
  });
}
const resetDialog = ref(false),
  resetUser = ref<ManagedUser>(),
  resetForm = ref<FormInstance>(),
  resetModel = reactive({ newPassword: "" });
function openReset(row: ManagedUser): void {
  resetUser.value = row;
  resetModel.newPassword = "";
  resetDialog.value = true;
  resetForm.value?.clearValidate();
}
async function resetPassword(): Promise<void> {
  if (busy.value || !(await resetForm.value?.validate().catch(() => false)))
    return;
  void run(async () => {
    if (
      !resetUser.value ||
      !(await confirmAction(
        `确认重置“${resetUser.value.username}”的密码？原密码将不能再用于登录。`,
      ))
    )
      return;
    await usersApi.reset(resetUser.value.id, resetModel.newPassword);
    resetModel.newPassword = "";
    resetDialog.value = false;
    ElMessage.success("密码已重置，请通过安全渠道告知用户");
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
      <div class="eyebrow">账号与访问</div>
      <h1>用户管理</h1>
      <p>维护校园账号与角色，控制系统访问范围。</p>
    </div>
    <el-button type="primary" :icon="Plus" @click="openEdit()">
      创建用户
    </el-button>
  </div>
  <section class="panel">
    <el-form class="filter-panel" inline @submit.prevent="load(true)">
      <el-input
        v-model="filters.username"
        clearable
        :prefix-icon="Search"
        placeholder="搜索用户名 / 学号"
        aria-label="用户搜索"
      /><el-select
        v-model="filters.role"
        placeholder="全部角色"
        clearable
        aria-label="用户角色筛选"
      >
        <el-option
          v-for="(label, value) in roles"
          :key="value"
          :label="label"
          :value="value"
        />
</el-select
      ><el-select
        v-model="filters.enabled"
        placeholder="全部状态"
        clearable
        aria-label="用户状态筛选"
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
    /><el-table :data="rows" row-key="id" v-loading="loading">
      <el-table-column
        prop="username"
        label="账号 / 学号"
        min-width="180"
      /><el-table-column
        prop="realName"
        label="姓名"
        min-width="140"
      /><el-table-column label="角色" min-width="150">
        <template #default="scope">
          {{ roles[scope.row.role as Role] }}
        </template>
</el-table-column
      ><el-table-column label="状态" width="110">
        <template #default="scope">
          <StatusTag :value="scope.row.enabled" />
        </template>
</el-table-column
      ><el-table-column
        prop="id"
        label="用户 ID"
        min-width="120"
      /><el-table-column label="操作" min-width="240" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            :disabled="busy"
            @click="openEdit(scope.row)"
          >
            编辑
</el-button
          ><el-button
            link
            type="primary"
            :disabled="busy"
            @click="openReset(scope.row)"
          >
            重置密码
</el-button
          ><el-button
            link
            :type="scope.row.enabled ? 'danger' : 'success'"
            :disabled="busy || scope.row.id === auth.user?.id"
            @click="toggle(scope.row)"
          >
            {{
              scope.row.id === auth.user?.id
                ? "当前账号"
                : scope.row.enabled
                  ? "停用"
                  : "启用"
            }}
          </el-button>
        </template>
</el-table-column
      ><template #empty>
        <el-empty description="暂无符合条件的用户" :image-size="70" />
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
    :title="editing ? '编辑用户' : '创建用户'"
    width="540px"
    :close-on-click-modal="false"
    :show-close="!busy"
    :close-on-press-escape="!busy"
    @closed="model.initialPassword = ''"
  >
    <el-form ref="form" :model="model" :rules="rules" label-position="top">
      <el-form-item label="账号 / 学号" prop="username">
        <el-input
          v-model="model.username"
          :disabled="!!editing"
          :maxlength="64"
          autocomplete="off"
        />
</el-form-item
      ><el-form-item label="姓名" prop="realName">
        <el-input v-model="model.realName" :maxlength="50" />
</el-form-item
      ><el-form-item label="角色" prop="role">
        <el-select v-model="model.role" :disabled="!!editing">
          <el-option
            v-for="(label, value) in roles"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
</el-form-item
      ><el-form-item v-if="!editing" label="初始密码" prop="initialPassword">
        <el-input
          v-model="model.initialPassword"
          type="password"
          show-password
          autocomplete="new-password"
          :maxlength="64"
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
    v-model="resetDialog"
    title="重置用户密码"
    width="500px"
    :close-on-click-modal="false"
    :show-close="!busy"
    @closed="resetModel.newPassword = ''"
  >
    <p>重置账号：{{ resetUser?.username }}</p>
    <el-form
      ref="resetForm"
      :model="resetModel"
      :rules="{ newPassword: passwordRule }"
      label-position="top"
    >
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="resetModel.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          :maxlength="64"
        />
      </el-form-item>
</el-form
    ><template #footer>
      <el-button :disabled="busy" @click="resetDialog = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        :disabled="busy"
        @click="resetPassword"
      >
        确认重置
      </el-button>
    </template>
  </el-dialog>
</template>
