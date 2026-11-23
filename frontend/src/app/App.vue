<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'

const mainContent = ref<HTMLElement | null>(null)
</script>

<template>
  <!-- 阻止原生锚点改写 Hash 路由，只移动键盘焦点。 -->
  <a class="skip-link" href="#main-content" @click.prevent="mainContent?.focus()">跳到主要内容</a>
  <header class="site-header">
    <RouterLink class="brand" to="/" aria-label="拾点地图首页">
      <span class="brand-mark" aria-hidden="true">拾</span>
      <span>拾点地图</span>
    </RouterLink>
    <span class="header-caption">一座城，一张属于旅途的地图</span>
    <span class="tag">公共地图</span>
  </header>

  <main id="main-content" ref="mainContent" tabindex="-1"><RouterView /></main>
</template>
