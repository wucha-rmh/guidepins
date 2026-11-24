import { readonly, ref, shallowRef } from 'vue'
import { ApiError } from '../../shared/apiResponse'
import { errorMessage } from '../../shared/apiRequest'
import { currentAccount, loginAccount, logoutAccount } from './api'
import type { AccountIdentity, Credentials } from './types'

// 全站只持有一份内存身份；Cookie 由浏览器管理，不在本地存储令牌或密码。
const user = shallowRef<AccountIdentity | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const busy = ref(false)
const error = ref('')
const notice = ref('')
let generation = 0
let pendingRestore: Promise<void> | undefined
let restoring: AbortController | undefined

function cancelRestore() {
  generation++
  restoring?.abort()
  restoring = undefined
  pendingRestore = undefined
}

function expire() {
  cancelRestore()
  user.value = null
  state.value = 'ready'
  notice.value = '登录已失效，请重新登录。'
}

/** 受保护请求的 401 才清除身份；登录表单的密码错误由表单自己展示。 */
function handleAuthError(cause: unknown) {
  if (cause instanceof ApiError && cause.status === 401) expire()
}

async function restore() {
  if (busy.value) return
  if (pendingRestore) return pendingRestore
  const version = ++generation
  const controller = new AbortController()
  restoring = controller
  state.value = 'loading'
  error.value = ''
  pendingRestore = (async () => {
    try {
      const account = await currentAccount(controller.signal)
      if (version === generation) { user.value = account; state.value = 'ready' }
    } catch (cause) {
      if (version !== generation) return
      user.value = null
      if (cause instanceof ApiError && cause.status === 401) state.value = 'ready'
      else { state.value = 'error'; error.value = errorMessage(cause) }
    } finally {
      if (version === generation) { pendingRestore = undefined; restoring = undefined }
    }
  })()
  return pendingRestore
}

async function login(body: Credentials) {
  if (busy.value) return false
  busy.value = true
  cancelRestore()
  error.value = ''
  try {
    user.value = await loginAccount(body)
    state.value = 'ready'
    notice.value = ''
    return true
  } catch (cause) {
    // 写请求超时可能已在服务端完成；不自动重试，随后重新确认 Cookie 身份。
    user.value = null
    state.value = cause instanceof ApiError && cause.status < 500 ? 'ready' : 'error'
    if (state.value === 'error') error.value = '登录状态未确认，请重新检查。'
    throw cause
  } finally { busy.value = false }
}

async function logout() {
  if (busy.value) return
  busy.value = true
  cancelRestore()
  // 退出开始就移除私人内容；失败后明确提示，并允许重试退出。
  user.value = null
  state.value = 'loading'
  error.value = ''
  try {
    await logoutAccount()
    state.value = 'ready'
    notice.value = '已退出登录。'
  } catch (cause) {
    state.value = 'error'
    error.value = `退出未确认：${errorMessage(cause)}`
    throw cause
  } finally { busy.value = false }
}

export function useAuth() {
  return { user: readonly(user), state: readonly(state), busy: readonly(busy), error: readonly(error),
    notice: readonly(notice), restore, login, logout, handleAuthError }
}
