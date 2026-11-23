<script setup lang="ts">
import { RouterLink } from 'vue-router'
import StatePanel from '../../shared/StatePanel.vue'
import { useCities } from './useCities'

const { cities, state, error, reload } = useCities()
</script>

<template>
  <div class="city-page">
    <section class="city-intro" aria-labelledby="city-heading">
      <span class="eyebrow">探索城市 · 拾起沿途的地点</span>
      <h1 id="city-heading">从一座城市，<br />开始拾点。</h1>
      <p>选择想去的城市，打开地图，看看旅途中值得停留的地方。</p>
    </section>

    <section class="city-directory" aria-labelledby="directory-heading">
      <div class="section-heading">
        <h2 id="directory-heading">选择地市</h2>
        <span class="muted">公共地图，随时浏览</span>
      </div>
      <StatePanel v-if="state === 'loading'" kind="loading" title="正在加载地市" description="请稍候，地市列表即将呈现。" />
      <StatePanel v-else-if="state === 'error'" kind="error" title="地市列表加载失败"
        :description="error" @retry="reload" />
      <StatePanel v-else-if="cities.length === 0" kind="empty" title="暂无可选地市" description="新的城市准备好后，会在这里与你见面。" />
      <div v-else class="city-grid">
        <RouterLink v-for="city in cities" :key="city.id" class="city-card" :to="{ name: 'city-map', params: { cityId: city.id } }">
          <div class="city-card-art" aria-hidden="true">
            <span class="city-art-orbit"></span><span class="city-art-pin"></span><span class="city-art-name">{{ city.name }}</span>
          </div>
          <div class="city-card-content">
            <div class="city-card-title"><h3>{{ city.name }}</h3><span class="tag">浏览底图</span></div>
            <p>打开地市地图，探索沿途地点</p>
            <span class="city-card-action">进入地图</span>
          </div>
        </RouterLink>
      </div>
    </section>
    <p class="page-footnote">每一段旅途，从找到第一个地点开始。</p>
  </div>
</template>
