<script setup lang="ts">
// 地市列表、公共地图和底图共用提示结构，避免重复维护可访问性与重试交互。
withDefaults(defineProps<{
  kind: 'loading' | 'empty' | 'error'
  title: string
  description: string
  retryLabel?: string
}>(), { retryLabel: '重试' })

defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="state-panel" :class="`state-panel--${kind}`" :role="kind === 'error' ? 'alert' : 'status'"
    :aria-busy="kind === 'loading'">
    <span class="state-symbol" :class="{ 'state-symbol--loading': kind === 'loading' }" aria-hidden="true">
      {{ kind === 'error' ? '!' : kind === 'empty' ? '—' : '' }}
    </span>
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
    <button v-if="kind === 'error'" class="button button--secondary" type="button" @click="$emit('retry')">
      {{ retryLabel }}
    </button>
    <slot />
  </div>
</template>
