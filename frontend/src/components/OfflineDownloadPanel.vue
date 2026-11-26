<script setup lang="ts">
import { useOfflineDownload } from '../composables/useOfflineDownload'

const props = defineProps<{ mapId: string }>()
const { busy, error, notice, download } = useOfflineDownload(() => props.mapId)
</script>

<template>
  <section class="offline-download-panel" aria-labelledby="offline-download-heading" :aria-busy="busy">
    <h2 id="offline-download-heading">带着地图出发</h2>
    <p>保存南京全市简图、地点周边约 2 公里的详细地图，以及这张地图的地点和攻略。下载后双击文件即可离线查看。</p>
    <button type="button" class="button button--secondary" :disabled="busy" @click="download">
      {{ busy ? '正在生成并下载…' : '下载离线地图' }}
    </button>
    <p v-if="error" class="offline-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
  </section>
</template>

<style scoped>
.offline-download-panel { padding: 20px 0; border-top: 1px solid var(--line, #d9dfd5); }
h2 { margin: 0 0 10px; font-size: 15px; }
p { font-size: 12px; line-height: 1.7; color: var(--muted, #6a7769); overflow-wrap: anywhere; }
.offline-error { color: #a03d29; }
</style>
