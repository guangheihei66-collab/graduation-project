<script setup lang="ts">
defineProps<{ page: { page: number; size: number; total: number } }>();
const emit = defineEmits<{ change: [page: number]; size: [size: number] }>();
</script>
<template>
  <div class="pager">
    <span>共 {{ page.total }} 条记录</span
    ><el-pagination
      :current-page="page.page"
      :page-size="page.size"
      :total="page.total"
      :page-sizes="[10, 20, 50, 100]"
      layout="sizes, prev, pager, next"
      @current-change="emit('change', $event)"
      @size-change="emit('size', $event)"
    />
  </div>
</template>
