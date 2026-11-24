<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import { useAuth } from './useAuth'
import './accountMenu.css'

type MenuItem = { id: string; label: string; to: RouteLocationRaw }
  | { id: string; label: string; action: 'logout' | 'restore' }

const emit = defineEmits<{ personalMaps: [] }>()
const route = useRoute()
const { user, state, busy, error, logout, restore } = useAuth()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const open = ref(false)
const signedIn = computed(() => state.value === 'ready' && user.value !== null)
const identity = computed(() => signedIn.value ? user.value?.email || user.value?.phone : '')
const initial = computed(() => signedIn.value && user.value?.email ? user.value.email.charAt(0).toUpperCase() : '')

// 菜单只集中列出现有个人功能；后续功能在此加入入口，无需继续扩充全站导航模板。
const items = computed<MenuItem[]>(() => {
  if (state.value === 'loading') return []
  if (state.value === 'error') return [
    { id: 'restore', label: '重新检查登录状态', action: 'restore' },
    { id: 'logout', label: '重试退出', action: 'logout' },
  ]
  if (!signedIn.value) return [{
    id: 'auth', label: '登录 / 注册', to: {
      name: 'auth', query: route.path.startsWith('/cities/') ? { redirect: route.fullPath }
        : route.query.tab === 'mine' ? { redirect: '/?tab=mine' } : {},
    },
  }]
  return [
    { id: 'maps', label: '我的地图', to: route.params.cityId
      ? { name: 'city-map', params: { cityId: String(route.params.cityId) }, query: { tab: 'mine' } }
      : { name: 'cities', query: { tab: 'mine' } } },
    { id: 'logout', label: '退出登录', action: 'logout' },
  ]
})

function close(returnFocus = false) {
  open.value = false
  if (returnFocus) trigger.value?.focus()
}
async function focusFirst() {
  open.value = true
  await nextTick()
  root.value?.querySelector<HTMLElement>('.personal-menu-action')?.focus()
}
function selectLink(id: string) {
  if (id === 'maps') emit('personalMaps')
  close()
}
async function act(action: 'logout' | 'restore') {
  close(true)
  if (action === 'restore') await restore()
  else {
    try { await logout() } catch { /* 全站状态提示保留失败原因和重试入口。 */ }
  }
}
function outside(event: Event) {
  if (open.value && event.target instanceof Node && !root.value?.contains(event.target)) close()
}
function escape(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    close(true)
  }
}
watch(() => route.fullPath, () => close())
// 身份切换时关闭旧菜单，避免菜单焦点留在已被替换的账号操作上。
watch([state, () => user.value?.id], () => { if (open.value) close(true) })
onMounted(() => {
  document.addEventListener('pointerdown', outside)
  document.addEventListener('focusin', outside)
  document.addEventListener('keydown', escape)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', outside)
  document.removeEventListener('focusin', outside)
  document.removeEventListener('keydown', escape)
})
</script>

<template>
  <div ref="root" class="personal-center">
    <button ref="trigger" class="personal-center-trigger" :class="{ 'personal-center-trigger--signed-in': signedIn }"
      type="button" aria-label="个人中心" title="个人中心" :aria-expanded="open" aria-controls="personal-center-panel"
      @click="open = !open" @keydown.down.prevent="focusFirst">
      <span v-if="initial" aria-hidden="true">{{ initial }}</span>
      <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false"><circle cx="12" cy="8" r="3.5" /><path d="M5 20v-2a7 7 0 0 1 14 0v2" /></svg>
      <span v-if="signedIn" class="personal-center-status" aria-hidden="true"></span>
    </button>
    <section v-if="open" id="personal-center-panel" class="personal-menu" aria-label="个人中心菜单">
      <div class="personal-menu-heading">
        <h2>个人中心</h2>
        <p v-if="signedIn" class="personal-menu-identity" :title="identity || ''">{{ identity }}</p>
        <p v-if="signedIn" class="personal-menu-note">{{ user?.role === 'admin' ? '管理员' : '普通用户' }}</p>
        <p v-else-if="state === 'loading'" class="personal-menu-note" role="status">{{ busy ? '正在处理账号…' : '正在恢复登录…' }}</p>
        <p v-else-if="state === 'error'" class="personal-menu-note">{{ error }}</p>
        <p v-else class="personal-menu-note">登录后，留一份自己的城市手记。</p>
      </div>
      <!-- 使用普通链接和按钮的原生 Tab 顺序，保留链接打开方式与键盘操作。 -->
      <ul v-if="items.length" class="personal-menu-list">
        <li v-for="item in items" :key="item.id">
          <RouterLink v-if="'to' in item" class="personal-menu-action" :to="item.to" @click="selectLink(item.id)">
            <svg v-if="item.id === 'maps'" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false"><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Zm6-3v15m6-12v15" /></svg>
            <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false"><circle cx="12" cy="8" r="3.5" /><path d="M5 20v-2a7 7 0 0 1 14 0v2" /></svg>
            <span>{{ item.label }}</span><span class="personal-menu-arrow" aria-hidden="true">›</span>
          </RouterLink>
          <button v-else class="personal-menu-action" type="button" :disabled="busy" @click="act(item.action)">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false"><path v-if="item.action === 'logout'" d="M10 4H4v16h6m4-12 4 4-4 4m-5-4h12" /><path v-else d="M20 10a8 8 0 1 0-2 8M20 4v6h-6" /></svg>
            <span>{{ item.label }}</span>
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>
