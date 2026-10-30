<script setup lang="ts">
import { reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../../stores/auth";
import { safeRedirect } from "../../router";
import { errorMessage } from "../../api/http";
const auth = useAuthStore(),
  route = useRoute(),
  router = useRouter(),
  form = ref<FormInstance>(),
  error = ref("");
const model = reactive({ username: "", password: "" });
const rules: FormRules = {
  username: [{ required: true, message: "请输入账号", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};
async function submit(): Promise<void> {
  if (auth.busy || !(await form.value?.validate().catch(() => false))) return;
  error.value = "";
  try {
    if (await auth.login(model.username.trim(), model.password)) {
      model.password = "";
      await router.replace(safeRedirect(route.query.redirect));
    }
  } catch (e) {
    error.value = errorMessage(e);
    model.password = "";
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <div class="login-brand">
        <img src="/favicon.svg" alt="" />高校实验室设备预约系统
      </div>
      <div class="login-story-body">
        <div class="eyebrow">让实验资源连接每一次探索</div>
        <h1>有序预约，<br />让科研更专注。</h1>
        <p>统一管理实验室与设备资源，<br />让预约、审批与使用记录清晰可循。</p>
        <div class="lab-illustration" aria-hidden="true">
          <div class="illustration-grid"></div>
          <div class="illustration-card card-a">
            <span class="icon-square">⌁</span>
            <div>实验设备<strong>资源统一管理</strong></div>
            <i></i>
          </div>
          <div class="illustration-card card-b">
            <span class="icon-square">✓</span>
            <div>预约审批<strong>流程有据可查</strong></div>
            <i></i>
          </div>
          <div class="illustration-orbit"></div>
        </div>
      </div>
      <div class="login-story-foot">规范预约 · 高效共享 · 安全使用</div>
    </section>
    <section class="login-panel">
      <div class="login-form-wrap">
        <span class="eyebrow">管理后台</span>
        <h2>欢迎回来</h2>
        <p>请使用管理员账号登录工作台</p>
        <el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
          class="form-alert"
        /><el-form
          ref="form"
          :model="model"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="model.username"
              name="username"
              autocomplete="username"
              placeholder="请输入管理员账号"
              :maxlength="64"
            />
</el-form-item
          ><el-form-item label="密码" prop="password">
            <el-input
              v-model="model.password"
              name="password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              :maxlength="64"
              @keyup.enter="submit"
            />
</el-form-item
          ><el-button
            native-type="submit"
            type="primary"
            class="login-submit"
            :loading="auth.busy"
            :disabled="auth.busy"
          >
            登录管理后台
          </el-button>
        </el-form>
        <div class="login-hint">
          仅面向系统管理员和实验室管理员开放<br />账号问题请联系系统管理员
        </div>
      </div>
      <div class="login-copyright">高校实验室设备预约管理平台</div>
    </section>
  </div>
</template>
