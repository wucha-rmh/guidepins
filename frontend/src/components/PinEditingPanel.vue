<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import FormField from './FormField.vue'
import type { MapPin } from '../types/publicMap'
import type { PinCoordinates, PinEditingTarget } from '../types/pinEditing'

const props = defineProps<{
  phase: 'idle' | 'placing' | 'editing'
  target: PinEditingTarget | null
  /** 草稿坐标；拖动地图圆环时实时变化。 */
  coordinates: PinCoordinates | null
  /** 正在修改的图钉原文，用于回填表单。 */
  initial: MapPin | null
  busy: boolean
  error: string
  notice: string
}>()
const emit = defineEmits<{
  place: []
  cancel: []
  save: [values: { name: string; description: string }]
  delete: []
}>()

const name = ref('')
const description = ref('')
const confirmingDelete = ref(false)

// 每次进入编辑都从图钉当前内容重新开始，避免上一次的输入或删除确认残留。
watch(() => props.target, (target) => {
  name.value = target?.kind === 'edit' && props.initial ? props.initial.name : ''
  description.value = target?.kind === 'edit' && props.initial ? props.initial.description ?? '' : ''
  confirmingDelete.value = false
}, { immediate: true })

const editing = computed(() => props.phase === 'editing' && props.coordinates !== null)
const canSave = computed(() => editing.value && name.value.trim().length > 0 && !props.busy)
</script>

<template>
  <section class="pin-editing-panel" aria-labelledby="pin-editing-heading">
    <div class="panel-heading">
      <h2 id="pin-editing-heading">编辑图钉</h2>
      <span v-if="phase === 'placing'" class="pin-editing-badge">放置中</span>
      <span v-else-if="phase === 'editing'" class="pin-editing-badge">编辑中</span>
    </div>

    <template v-if="phase === 'idle'">
      <p class="form-help">在地图上新增地点，或点地点旁的「编辑」调整名称、说明和位置。</p>
      <button class="button button--secondary pin-editing-start" type="button" @click="emit('place')">
        <span aria-hidden="true">＋</span>新增图钉
      </button>
    </template>

    <p v-else-if="phase === 'placing'" class="form-help pin-editing-placing" role="status">
      在地图上点一下确定位置，按 Esc 取消。
      <button class="text-link" type="button" @click="emit('cancel')">取消</button>
    </p>

    <form v-else-if="editing && coordinates" class="stack-form" @submit.prevent="emit('save', { name, description })">
      <FormField label="图钉名称" control-id="pin-editing-name">
        <input id="pin-editing-name" v-model="name" required maxlength="120" :disabled="busy"
          placeholder="例如：秦淮河畔的旧书店" />
      </FormField>
      <FormField label="说明（可选）" control-id="pin-editing-description">
        <textarea id="pin-editing-description" v-model="description" maxlength="2000" :disabled="busy" rows="4"
          placeholder="写下这个地点的看点、路线或提醒。"></textarea>
      </FormField>
      <p class="pin-editing-coordinates">
        <span>经度 {{ coordinates.longitude.toFixed(6) }}</span>
        <span>纬度 {{ coordinates.latitude.toFixed(6) }}</span>
      </p>
      <p class="form-help">拖动地图上的虚线圆环可以调整位置。</p>
      <p v-if="busy" class="form-help" role="status">正在保存…</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="pin-editing-actions">
        <button class="button button--primary" type="submit" :disabled="!canSave">保存图钉</button>
        <button class="button button--secondary" type="button" :disabled="busy" @click="emit('cancel')">取消</button>
        <button v-if="target?.kind === 'edit' && !confirmingDelete" class="button button--secondary pin-editing-delete"
          type="button" :disabled="busy" @click="confirmingDelete = true">删除图钉</button>
      </div>
      <div v-if="confirmingDelete" class="pin-editing-confirm" role="group" aria-label="确认删除图钉">
        <p>删除后地图上不再显示这个地点，也无法恢复。</p>
        <div class="pin-editing-actions">
          <button class="button button--secondary pin-editing-delete" type="button" :disabled="busy"
            @click="emit('delete')">确认删除</button>
          <button class="button button--secondary" type="button" :disabled="busy" @click="confirmingDelete = false">
            保留图钉
          </button>
        </div>
      </div>
    </form>

    <p v-if="notice" class="form-success" role="status">{{ notice }}</p>
    <p v-if="error && !editing" class="form-error" role="alert">{{ error }}</p>
  </section>
</template>
