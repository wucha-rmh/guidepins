<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../api/client'
import { registerAccount } from '../api/auth'
import FormField from '../components/FormField.vue'
import { useAuth } from '../composables/useAuth'
import type { Credentials } from '../types/auth'
import { DEFAULT_PHONE_REGION, phoneForRequest, phoneRegions } from '../lib/phone'
import './AuthPage.css'

const route = useRoute()
const router = useRouter()
const { user, state, busy: authBusy, login } = useAuth()
const registering = computed(() => route.query.mode === 'register')
const method = ref<'email' | 'phone'>('email')
const identifier = ref('')
const phoneRegion = ref(DEFAULT_PHONE_REGION)
const password = ref('')
const busy = ref(false)
const error = ref('')
const message = ref('')
let active = true
// 仅允许回到应用内部地图页面，避免把外部地址作为登录跳转目标。
const returnTo = computed(() => typeof route.query.redirect === 'string' &&
  /^(?:\/\?tab=mine|\/cities\/[^/?#]+(?:\/maps\/[^/?#]+)?(?:\?tab=mine)?)$/.test(route.query.redirect)
  ? route.query.redirect : '/')

function changeMode(mode: 'login' | 'register') {
  if (busy.value || authBusy.value) return
  void router.replace({ name: 'auth', query: { ...route.query, mode } })
}

watch(registering, () => { password.value = ''; error.value = ''; message.value = '' })
watch(method, () => { identifier.value = ''; error.value = ''; message.value = '' })
watch(phoneRegion, () => { error.value = ''; message.value = '' })
onBeforeUnmount(() => { active = false; password.value = '' })

async function submit() {
  if (busy.value || authBusy.value) return
  error.value = ''
  message.value = ''
  const value = identifier.value.trim()
  if (method.value === 'email' && (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || value.length > 254)) {
    error.value = '请输入有效邮箱。'; return
  }
  let fullPhone = ''
  if (method.value === 'phone') {
    try { fullPhone = phoneForRequest(phoneRegion.value, value) }
    catch (cause) { error.value = errorMessage(cause); return }
  }
  if (!password.value.trim() || password.value.length > 128 || (registering.value && password.value.length < 6)) {
    error.value = registering.value ? '密码需为 6 至 128 个字符。' : '请输入密码，最多 128 个字符。'; return
  }
  const credentials: Credentials = method.value === 'email'
    ? { email: value.toLowerCase(), password: password.value }
    : { phone: fullPhone, password: password.value }
  const wasRegistering = registering.value
  busy.value = true
  try {
    if (wasRegistering) {
      await registerAccount(credentials)
      if (!active) return
      await router.replace({ name: 'auth', query: { ...route.query, mode: 'login' } })
      message.value = '注册成功，请使用刚才的账号和密码登录。'
    } else if (await login(credentials)) {
      if (active) await router.replace(returnTo.value)
    }
  } catch (cause) { if (active) error.value = errorMessage(cause) }
  finally { password.value = ''; busy.value = false }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">把想去的地方，留在自己的地图上</p>
      <h1>你的城市手记，<br />从这里继续</h1>
      <p>登录后，在选定的城市创建自己的地图。<br />先留给自己，也可以公开分享。</p>
      <RouterLink class="text-link" :to="returnTo">先随意逛逛 →</RouterLink>
    </section>
    <section class="auth-card" aria-labelledby="account-heading">
      <h2 id="account-heading">欢迎来到拾点地图</h2>
      <template v-if="state === 'ready' && user">
        <p class="form-help">当前已登录 {{ user.email || user.phone }}。</p>
        <RouterLink class="button button--primary" :to="returnTo">返回地图</RouterLink>
      </template>
      <template v-else>
      <div class="auth-mode-switch" role="group" aria-label="登录或注册">
        <button type="button" :aria-pressed="!registering" :disabled="busy || authBusy" @click="changeMode('login')">登录</button>
        <button type="button" :aria-pressed="registering" :disabled="busy || authBusy" @click="changeMode('register')">注册</button>
      </div>
      <form class="stack-form" :aria-label="registering ? '注册账号' : '登录账号'" @submit.prevent="submit">
        <fieldset class="method-selector" :disabled="busy || authBusy">
          <legend>选择{{ registering ? '注册' : '登录' }}方式</legend>
          <label><input v-model="method" type="radio" value="email" name="identifier-method" />邮箱</label>
          <label><input v-model="method" type="radio" value="phone" name="identifier-method" />手机号</label>
        </fieldset>
        <FormField v-if="method === 'email'" label="邮箱" control-id="auth-identifier">
          <input id="auth-identifier" v-model="identifier" type="email" placeholder="you@example.com" autocomplete="username"
            maxlength="254" required :disabled="busy || authBusy" />
        </FormField>
        <div v-else class="form-field">
          <label for="auth-identifier">手机号</label>
          <div class="phone-input-row">
            <select id="phone-region" v-model="phoneRegion" aria-label="国家／地区码" :disabled="busy || authBusy">
              <option v-for="item in phoneRegions" :key="item.region" :value="item.region">{{ item.name }} {{ item.callingCode }}</option>
            </select>
            <input id="auth-identifier" v-model="identifier" type="tel" inputmode="numeric" autocomplete="tel-national"
              placeholder="请输入手机号" aria-describedby="phone-input-hint" required :disabled="busy || authBusy" />
          </div>
          <p id="phone-input-hint" class="form-help">区号在左侧选择，右侧填写手机号即可。</p>
        </div>
        <FormField label="密码" control-id="auth-password">
          <input id="auth-password" v-model="password" type="password" :autocomplete="registering ? 'new-password' : 'current-password'"
            :minlength="registering ? 6 : 1" maxlength="128" required :disabled="busy || authBusy" />
        </FormField>
        <p class="form-help">{{ registering ? '密码为 6 至 128 个字符。暂不验证邮箱或手机号，暂不支持找回密码。' : '密码中的首尾空格会保留。' }}</p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <p v-if="message" class="form-success" role="status">{{ message }}</p>
        <button class="button button--primary" type="submit" :disabled="busy || authBusy">{{ busy ? '正在处理…' : registering ? '注册账号' : '登录' }}</button>
      </form>
      </template>
    </section>
  </div>
</template>
