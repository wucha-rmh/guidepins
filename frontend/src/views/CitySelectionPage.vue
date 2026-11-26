<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { provinceCodeOf, provinceNameOf } from '../lib/provinces'
import { useCities } from '../composables/useCities'
import type { City } from '../types/city'

const { cities, state, error, reload } = useCities()
const route = useRoute()
const router = useRouter()
const personalMaps = computed(() => route.query.tab === 'mine')
// 首页只保留一个入口按钮，城市选择放进对话框里分级展开，避免长期占版面。
const picker = ref<HTMLDialogElement | null>(null)

/** 省份分组：只汇总地市目录里真实存在的省份，空省份不会出现在下拉框中。 */
interface ProvinceGroup {
  code: string
  name: string
  cities: City[]
}

const provinces = computed<ProvinceGroup[]>(() => {
  const groups = new Map<string, ProvinceGroup>()
  for (const city of cities.value) {
    const code = provinceCodeOf(city.code)
    if (!code) continue
    const group = groups.get(code) ?? { code, name: provinceNameOf(code), cities: [] }
    group.cities.push(city)
    groups.set(code, group)
  }
  return [...groups.values()].sort((left, right) => left.code.localeCompare(right.code))
})

const selectedProvince = ref('')
const selectedCityId = ref('')
const selectedCities = computed(
  () => provinces.value.find((group) => group.code === selectedProvince.value)?.cities ?? [],
)
const canEnter = computed(() => selectedCities.value.some((city) => city.id === selectedCityId.value))

// 换省份后清空地市，避免残留上一个省份的选择。
watch(selectedProvince, () => { selectedCityId.value = '' })

function openPicker() {
  selectedProvince.value = ''
  selectedCityId.value = ''
  if (picker.value && !picker.value.open) picker.value.showModal()
}

function closePicker() {
  picker.value?.close()
}

function enterCity() {
  if (!canEnter.value) return
  const cityId = selectedCityId.value
  closePicker()
  void router.push({
    name: 'city-map',
    params: { cityId },
    query: personalMaps.value ? { tab: 'mine' } : {},
  })
}

// 选中城市就会换路由，对话框随之收起，避免返回首页时还压着一层。
watch(() => route.fullPath, closePicker)
</script>

<template>
  <div class="city-page">
    <section class="city-hero" aria-labelledby="city-heading">
      <div class="city-intro">
        <span class="eyebrow">城市漫游，从这里开始</span>
        <h1 id="city-heading">从一座城市，<br /><em>开始拾点</em></h1>
        <p>{{ personalMaps ? '选择一座城市，查看或创建自己的地图' : '选择城市，打开公共地图，查看沿途地点' }}</p>
        <!-- 步骤只说明已有的访客流程，不暗示路线规划或个人收藏功能。 -->
        <ol class="journey-steps" aria-label="探索方式"><li><span aria-hidden="true">1</span>选择城市</li><li><span aria-hidden="true">2</span>打开公共地图</li><li><span aria-hidden="true">3</span>查看地点</li></ol>
      </div>
      <!-- 手绘线稿仅作品牌装饰，不承担地理数据展示，也不请求底图资源。 -->
      <div class="journey-art" aria-hidden="true">
        <svg viewBox="0 0 500 300" fill="none" focusable="false">
          <path class="art-land" d="M86 30C159 2 180 69 245 39S382 1 418 59S465 153 421 220S350 272 279 260S186 302 111 259S25 191 43 130S34 50 86 30Z" />
          <g class="art-contour"><path d="M80 93C113 36 179 104 234 71S359 33 394 88S422 177 374 218S298 227 250 235S147 265 105 225S45 154 80 93Z"/><path d="M105 112C145 58 172 137 245 98S339 66 367 111S391 171 351 194S287 194 246 210S162 239 125 210S75 160 105 112Z"/><path d="M141 131C175 95 185 163 250 128S323 102 342 130S356 165 321 174S273 178 235 191S174 211 154 190S114 160 141 131Z"/></g>
          <path class="art-river" d="M384 12C337 52 375 74 332 109S266 138 285 176S254 228 211 290"/>
          <g class="art-streets"><path d="M69 187L207 70L392 190M118 243L254 92L428 139M102 83L322 250M161 38L208 263M41 153L406 80"/><path d="M82 201L269 48M120 88L375 214M76 141L395 114"/></g>
          <path class="art-route" d="M119 208C156 220 167 163 202 158S252 193 279 150S319 103 367 94"/>
          <g class="art-stops"><circle cx="119" cy="208" r="7"/><circle cx="202" cy="158" r="7"/><circle cx="279" cy="150" r="7"/></g>
          <circle class="art-destination-halo" cx="367" cy="94" r="20"/><circle class="art-destination" cx="367" cy="94" r="9"/>
        </svg>
        <span class="art-compass">N<br /><span>↑</span></span>
      </div>
    </section>

    <section class="city-directory" aria-labelledby="directory-heading">
      <h2 id="directory-heading" class="visually-hidden">{{ personalMaps ? '选择城市，查看我的地图' : '选择城市' }}</h2>
      <button type="button" class="button button--primary city-select-button"
        :disabled="state === 'loading'" aria-haspopup="dialog" @click="openPicker">
        {{ state === 'loading' ? '正在加载城市…' : '选择城市' }}
      </button>
      <!-- 城市列表加载失败时把原因与重试留在按钮下方，点按钮仍能看到同一个对话框。 -->
      <p v-if="state === 'error'" class="city-select-error">
        {{ error }}<button type="button" class="text-link" @click="reload">重试</button>
      </p>
    </section>

    <footer class="page-footnote"><span>拾点地图<span class="footer-dot" aria-hidden="true">·</span>城市漫游手记</span><span>公共地图，自由浏览</span></footer>

    <dialog ref="picker" class="city-picker-dialog" aria-labelledby="city-picker-title">
      <div class="city-picker-head">
        <h2 id="city-picker-title">选择城市</h2>
        <span v-if="state === 'ready' && cities.length > 0" class="directory-count">共 {{ cities.length }} 座城市</span>
      </div>
      <p v-if="state === 'error'" class="city-picker-note">{{ error }}<button type="button" class="text-link" @click="reload">重试</button></p>
      <p v-else-if="state === 'ready' && cities.length === 0" class="city-picker-note">暂无可选城市，开放后会显示在这里。</p>
      <!-- 分级选择：先省份，选定省份后再出现该省的地市下拉框。 -->
      <div v-else class="city-picker-fields">
        <label class="city-picker-field">
          <span class="city-picker-label">省份</span>
          <select v-model="selectedProvince" :disabled="provinces.length === 0">
            <option value="">请选择省份</option>
            <option v-for="group in provinces" :key="group.code" :value="group.code">{{ group.name }}</option>
          </select>
        </label>
        <label v-if="selectedProvince" class="city-picker-field">
          <span class="city-picker-label">城市</span>
          <select v-model="selectedCityId">
            <option value="">请选择城市</option>
            <option v-for="city in selectedCities" :key="city.id" :value="city.id">{{ city.name }}</option>
          </select>
        </label>
      </div>
      <div class="city-picker-actions">
        <button type="button" class="text-link" @click="closePicker">取消</button>
        <button type="button" class="button button--primary" :disabled="!canEnter" @click="enterCity">进入地图</button>
      </div>
    </dialog>
  </div>
</template>
