<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MapPin } from '../public-map/types'
import StatePanel from '../../shared/StatePanel.vue'
import { validateGuideValues } from './api'
import type { GuideDetail } from './types'
import { useGuides } from './useGuides'

const props = defineProps<{
  mapId: string
  /** 当前地图的全部图钉，用于选择关联地点并显示名称。 */
  pins: MapPin[]
  /** 可以维护这张地图的攻略时给出录入入口，否则只读浏览。 */
  editable: boolean
}>()
const emit = defineEmits<{ changed: []; focusPin: [pinId: string] }>()

const { guides, state, error, busy, actionError, notice, reload, save, remove } = useGuides({
  mapId: () => props.mapId,
  onChanged: () => emit('changed'),
})

// 面板三态：idle 看列表，creating 新建，editing 修改已有攻略。
const mode = ref<'idle' | 'creating' | 'editing'>('idle')
const activeId = ref('')
const title = ref('')
const body = ref('')
const sourceUrl = ref('')
/** 关联图钉的当前顺序，数组下标即站序，拖动排序只改这个数组。 */
const ordered = ref<string[]>([])
const formError = ref('')
const confirmingDelete = ref(false)
const expandId = ref('')
const dragFrom = ref(-1)
const addingPinId = ref('')

const editing = computed(() => mode.value === 'editing')
const canSave = computed(() => !busy.value && title.value.trim().length > 0 && body.value.trim().length > 0)
const unlinkedPins = computed(() => props.pins.filter((pin) => !ordered.value.includes(pin.id)))

function pinName(pinId: string): string {
  return props.pins.find((pin) => pin.id === pinId)?.name ?? '已删除的地点'
}

function orderedByPosition(guide: GuideDetail): string[] {
  return [...guide.pins].sort((left, right) => left.position - right.position).map((link) => link.pinId)
}

function clearForm() {
  mode.value = 'idle'
  activeId.value = ''
  title.value = ''
  body.value = ''
  sourceUrl.value = ''
  ordered.value = []
  formError.value = ''
  confirmingDelete.value = false
  addingPinId.value = ''
}

function startCreate() {
  if (busy.value) return
  clearForm()
  mode.value = 'creating'
}

function startEdit(guide: GuideDetail) {
  if (busy.value) return
  clearForm()
  activeId.value = guide.id
  title.value = guide.title
  body.value = guide.body
  sourceUrl.value = guide.sourceUrl ?? ''
  ordered.value = orderedByPosition(guide)
  mode.value = 'editing'
}

/** 切换地图或失去编辑权限时放弃草稿，避免误写到另一张地图。 */
watch(() => [props.mapId, props.editable], () => {
  if (!props.editable || mode.value !== 'idle') clearForm()
})
watch(() => props.mapId, () => { expandId.value = '' })

async function submit() {
  if (busy.value) return
  let values
  try {
    values = validateGuideValues(title.value, body.value, sourceUrl.value)
  } catch (cause) {
    formError.value = cause instanceof Error ? cause.message : '内容格式有误，请检查后重试。'
    return
  }
  formError.value = ''
  const saved = await save(values, editing.value ? activeId.value : null, ordered.value)
  // 保存成功后回到列表；失败时保留草稿，用户可以直接改完再提交。
  if (saved) clearForm()
}

async function confirmDelete() {
  if (!editing.value) return
  const removed = await remove(activeId.value)
  if (removed) clearForm()
  else confirmingDelete.value = false
}

function addPin() {
  const pinId = addingPinId.value
  if (!pinId || ordered.value.includes(pinId)) return
  ordered.value = [...ordered.value, pinId]
  addingPinId.value = ''
}

function removePin(index: number) {
  ordered.value = ordered.value.filter((_, current) => current !== index)
}

function movePin(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= ordered.value.length) return
  const next = [...ordered.value]
  const [moved] = next.splice(index, 1)
  next.splice(target, 0, moved)
  ordered.value = next
}

function onDragStart(index: number) {
  dragFrom.value = index
}

function onDrop(index: number) {
  const from = dragFrom.value
  dragFrom.value = -1
  if (from < 0 || from === index) return
  const next = [...ordered.value]
  const [moved] = next.splice(from, 1)
  next.splice(index, 0, moved)
  ordered.value = next
}

function toggleExpand(guideId: string) {
  expandId.value = expandId.value === guideId ? '' : guideId
}

/** 由外部（图钉列表的出处链接）请求展开某一篇；正在编辑时先放弃草稿再定位。 */
function openGuide(guideId: string) {
  if (mode.value !== 'idle') clearForm()
  expandId.value = guideId
}

defineExpose({ openGuide })
</script>

<template>
  <section class="guide-panel" aria-labelledby="guide-heading">
    <div class="panel-heading">
      <h2 id="guide-heading">攻略与出处</h2>
      <span v-if="state === 'ready'" class="panel-count">{{ guides.length }} 篇</span>
      <span v-if="mode !== 'idle'" class="guide-badge">{{ editing ? '编辑中' : '新攻略' }}</span>
    </div>

    <StatePanel v-if="state === 'loading'" kind="loading" title="正在加载攻略" description="请稍候。" />
    <StatePanel v-else-if="state === 'error'" kind="error" title="攻略加载失败" :description="error"
      @retry="reload" />

    <template v-else>
      <!-- 编辑态：标题、正文、来源链接，以及可以拖动排序的关联地点。 -->
      <form v-if="mode !== 'idle'" class="stack-form" @submit.prevent="submit">
        <label class="form-field" for="guide-title">攻略标题
          <input id="guide-title" v-model="title" maxlength="120" :disabled="busy"
            placeholder="例如：秦淮河半日散步" />
        </label>
        <label class="form-field" for="guide-body">正文或摘录
          <textarea id="guide-body" v-model="body" rows="6" maxlength="5000" :disabled="busy"
            placeholder="写下来源里的路线、看点或原文摘录。"></textarea>
        </label>
        <label class="form-field" for="guide-source">来源链接（可选）
          <input id="guide-source" v-model="sourceUrl" maxlength="2048" :disabled="busy"
            placeholder="https://…" inputmode="url" />
        </label>

        <div class="guide-links" aria-labelledby="guide-links-heading">
          <h3 id="guide-links-heading">提到的地点</h3>
          <p class="form-help">顺序就是攻略里的先后。按住条目拖动可以调整，也可以用地名后的箭头。</p>
          <ol v-if="ordered.length" class="guide-link-list">
            <li v-for="(pinId, index) in ordered" :key="pinId" class="guide-link-item" draggable="true"
              @dragstart="onDragStart(index)" @dragover.prevent @drop.prevent="onDrop(index)">
              <span class="guide-link-handle" aria-hidden="true">⋮⋮</span>
              <span class="guide-link-index" aria-hidden="true">{{ index + 1 }}</span>
              <span class="guide-link-name">{{ pinName(pinId) }}</span>
              <span class="guide-link-actions">
                <button type="button" class="text-link" :disabled="busy || index === 0"
                  :aria-label="`上移 ${pinName(pinId)}`" @click="movePin(index, -1)">↑</button>
                <button type="button" class="text-link" :disabled="busy || index === ordered.length - 1"
                  :aria-label="`下移 ${pinName(pinId)}`" @click="movePin(index, 1)">↓</button>
                <button type="button" class="text-link" :disabled="busy"
                  :aria-label="`移除 ${pinName(pinId)}`" @click="removePin(index)">移除</button>
              </span>
            </li>
          </ol>
          <p v-else class="form-help">还没有关联地点。可以保存成草稿攻略，稍后再补。</p>
          <div v-if="unlinkedPins.length" class="guide-link-add">
            <label class="form-field" for="guide-link-select">添加地点
              <select id="guide-link-select" v-model="addingPinId" :disabled="busy">
                <option value="">选择地图上的地点…</option>
                <option v-for="pin in unlinkedPins" :key="pin.id" :value="pin.id">{{ pin.name }}</option>
              </select>
            </label>
            <button type="button" class="button button--secondary" :disabled="busy || !addingPinId" @click="addPin">
              添加
            </button>
          </div>
          <p v-else-if="pins.length === 0" class="form-help">这张地图还没有图钉，先在「编辑图钉」里落一枚。</p>
        </div>

        <p v-if="busy" class="form-help" role="status">正在保存…</p>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        <p v-else-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
        <div class="guide-actions">
          <button class="button button--primary" type="submit" :disabled="!canSave">保存攻略</button>
          <button class="button button--secondary" type="button" :disabled="busy" @click="clearForm">取消</button>
          <button v-if="editing && !confirmingDelete" class="button button--secondary guide-delete" type="button"
            :disabled="busy" @click="confirmingDelete = true">删除攻略</button>
        </div>
        <div v-if="confirmingDelete" class="guide-confirm" role="group" aria-label="确认删除攻略">
          <p>删除后这篇攻略与它的地点关联都会消失，图钉本身保留。</p>
          <div class="guide-actions">
            <button class="button button--secondary guide-delete" type="button" :disabled="busy"
              @click="confirmDelete">确认删除</button>
            <button class="button button--secondary" type="button" :disabled="busy" @click="confirmingDelete = false">
              保留攻略
            </button>
          </div>
        </div>
      </form>

      <!-- 列表态：可编辑时每篇带编辑入口，只读时点开可以看正文与提到的地点。 -->
      <template v-else>
        <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
        <p v-if="notice" class="form-success" role="status">{{ notice }}</p>
        <button v-if="editable" class="button button--secondary guide-start" type="button" @click="startCreate">
          <span aria-hidden="true">＋</span>新增攻略
        </button>
        <StatePanel v-if="guides.length === 0 && !actionError" kind="empty" title="还没有攻略"
          :description="editable ? '把一篇路线或摘录记下来，就能挂到沿途地点上。' : '这张地图还没有人留下攻略。'" />
        <ol v-else class="guide-list" aria-label="攻略列表">
          <li v-for="guide in guides" :key="guide.id" class="guide-item">
            <div class="guide-item-head">
              <button type="button" class="guide-item-toggle" :aria-expanded="expandId === guide.id"
                @click="toggleExpand(guide.id)">
                <span class="guide-item-title">{{ guide.title }}</span>
                <span class="guide-item-meta">{{ guide.pins.length }} 个地点</span>
              </button>
              <button v-if="editable" type="button" class="button button--secondary guide-item-edit"
                :aria-label="`编辑攻略：${guide.title}`" @click="startEdit(guide)">编辑</button>
            </div>
            <div v-if="expandId === guide.id" class="guide-item-body">
              <p class="guide-body-text">{{ guide.body }}</p>
              <p v-if="guide.sourceUrl" class="guide-source">
                <a class="text-link" :href="guide.sourceUrl" target="_blank" rel="noopener noreferrer">{{ guide.sourceUrl }}</a>
              </p>
              <p v-if="guide.pins.length" class="guide-pins">
                <span class="guide-pins-label">提到：</span>
                <button v-for="link in guide.pins" :key="link.pinId" type="button" class="text-link guide-pin-link"
                  @click="emit('focusPin', link.pinId)">{{ pinName(link.pinId) }}</button>
              </p>
            </div>
          </li>
        </ol>
      </template>
    </template>
  </section>
</template>
