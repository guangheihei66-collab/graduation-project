<script setup lang="ts">
import { computed } from "vue";
import { equipmentStatuses, reservationStatuses } from "../utils/business";
const props = defineProps<{ value: string | number }>();
const label = computed(() =>
  props.value === 1
    ? "启用"
    : props.value === 0
      ? "停用"
      : (
          { ...equipmentStatuses, ...reservationStatuses } as Record<
            string,
            string
          >
        )[props.value] || props.value,
);
const type = computed(() =>
  [1, "AVAILABLE", "APPROVED", "COMPLETED"].includes(props.value)
    ? "success"
    : ["PENDING", "MAINTENANCE"].includes(String(props.value))
      ? "warning"
      : ["REJECTED", "DISABLED", 0].includes(props.value)
        ? "danger"
        : "info",
);
</script>
<template>
  <el-tag :type="type" effect="light" round>{{ label }}</el-tag>
</template>
