<script setup lang="ts">
/**
 * 表单字段外壳。
 *
 * 职责：只负责「标签文字 + 控件」的统一结构与语义（label 与控件的 for/id 关联），
 * 控件本身由调用方通过默认插槽传入，组件不持有任何表单状态，也不做内容校验 ——
 * 校验仍然留在 api 层的 validate* 函数与各表单自己的提交逻辑里。
 *
 * 这样登录、地图管理、图钉编辑、攻略编辑四处的字段不再各写一遍 label + form-field。
 */
defineProps<{
  /** 字段名，作为标签文字显示 */
  label: string
  /** 控件 id，点标签时聚焦对应控件 */
  controlId: string
}>()
</script>

<template>
  <label class="form-field" :for="controlId">
    {{ label }}
    <slot />
  </label>
</template>
