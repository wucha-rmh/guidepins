<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import AccountMenu from '../modules/auth/AccountMenu.vue'
import { useAuth } from '../modules/auth/useAuth'

const mainContent = ref<HTMLElement | null>(null)
const route = useRoute()
const { state, error, notice, busy, restore, logout } = useAuth()
// 由页面组合层转交导航意图：即使已经在“我的地图”，菜单仍能展开收起的侧栏。
const personalMapsRequest = ref(0)

function checkVisibleSession() {
  if (document.visibilityState === 'visible') void restore()
}
async function exit() {
  try { await logout() } catch { /* 会话模块保留失败状态，导航提供重试入口。 */ }
}
onMounted(() => {
  void restore()
  window.addEventListener('focus', checkVisibleSession)
  document.addEventListener('visibilitychange', checkVisibleSession)
})
onBeforeUnmount(() => {
  window.removeEventListener('focus', checkVisibleSession)
  document.removeEventListener('visibilitychange', checkVisibleSession)
})
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
      <AccountMenu @personal-maps="personalMapsRequest++" />
    </nav>
  </header>

  <div v-if="state === 'error'" class="session-message session-message--error" role="alert">
    <span>{{ error }}</span><button class="text-link" type="button" @click="restore">重新检查</button>
    <button class="text-link" type="button" :disabled="busy" @click="exit">重试退出</button>
  </div>
  <div v-else-if="notice" class="session-message" role="status">{{ notice }}</div>

  <main id="main-content" ref="mainContent" tabindex="-1">
    <RouterView v-slot="{ Component }">
      <component :is="Component" v-bind="route.params.cityId ? { personalMapsRequest } : {}" />
    </RouterView>
  </main>
</template>
