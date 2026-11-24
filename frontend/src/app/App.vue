<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const mainContent = ref<HTMLElement | null>(null)
const route = useRoute()
</script>

<template>
  <!-- 阻止原生锚点改写 Hash 路由，只移动键盘焦点。 -->
  <a class="skip-link" href="#main-content" @click.prevent="mainContent?.focus()">跳到主要内容</a>
  <header class="site-header">
    <RouterLink class="brand" to="/" aria-label="拾点地图首页">
      <span class="brand-mark" aria-hidden="true"><span></span></span>
      <span class="brand-wordmark">拾点地图<small>GUIDEPINS</small></span>
    </RouterLink>
    <span class="header-caption">沿着地图，发现值得停留的地方</span>
    <nav class="header-nav" aria-label="主要导航">
      <RouterLink to="/" :aria-current="route.name === 'cities' ? 'page' : undefined">城市目录</RouterLink>
      <span class="header-note">公共地图 · 自由探索</span>
    </nav>
  </header>

  <main id="main-content" ref="mainContent" tabindex="-1"><RouterView /></main>
</template>
