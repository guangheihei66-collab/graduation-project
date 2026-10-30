<script setup lang="ts">
defineProps<{
  modelValue: boolean;
  title: string;
  fields: { label: string; value: string | number | null | undefined }[];
}>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
</script>
<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="min(580px, 95vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-descriptions :column="1" border>
      <el-descriptions-item
        v-for="field in fields"
        :key="field.label"
        :label="field.label"
      >
        {{ field.value ?? "—" }}
      </el-descriptions-item>
</el-descriptions
    ><slot />
  </el-drawer>
</template>
