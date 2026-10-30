<template>
  <el-result
    icon="warning"
    title="权限不足"
    sub-title="当前账号没有此页面的访问权限，请返回授权范围内的工作台。"
  >
    <template #extra>
      <router-link to="/dashboard">
        <el-button type="primary">返回工作台</el-button>
      </router-link>
    </template>
  </el-result>
</template>
