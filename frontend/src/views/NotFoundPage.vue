<script setup lang="ts">
import { RouterLink } from 'vue-router'
import StatePanel from '../components/StatePanel.vue'
</script>

<template>
  <div class="not-found-page">
    <StatePanel kind="empty" title="这个页面不存在" description="请检查地址，或重新选择一座城市。">
      <RouterLink class="button button--primary" to="/">返回地市选择</RouterLink>
    </StatePanel>
  </div>
</template>
