<script setup lang="ts">
import { RouterLink } from 'vue-router'
import StatePanel from '../../shared/StatePanel.vue'
import { useCities } from './useCities'

const { cities, state, error, reload } = useCities()
</script>

<template>
  <div class="city-page">
    <section class="city-hero" aria-labelledby="city-heading">
      <div class="city-intro">
        <span class="eyebrow">城市漫游，从这里开始</span>
        <h1 id="city-heading">从一座城市，<br /><em>开始拾点</em></h1>
        <p>选择城市，打开公共地图，查看沿途地点</p>
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
      <div class="section-heading">
        <h2 id="directory-heading">选择城市</h2>
        <span class="directory-count" v-if="state === 'ready' && cities.length > 0">共 {{ cities.length }} 座城市</span>
      </div>
      <StatePanel v-if="state === 'loading'" kind="loading" title="正在加载城市" description="正在获取可浏览的城市列表" />
      <StatePanel v-else-if="state === 'error'" kind="error" title="城市列表加载失败"
        :description="error" @retry="reload" />
      <StatePanel v-else-if="cities.length === 0" kind="empty" title="暂无可选城市" description="城市开放后会显示在这里，请稍后再来查看" />
      <div v-else class="city-grid" :class="{ 'city-grid--single': cities.length === 1 }">
        <RouterLink v-for="city in cities" :key="city.id" class="city-card" :to="{ name: 'city-map', params: { cityId: city.id } }">
          <div class="city-card-art" aria-hidden="true">
            <svg viewBox="0 0 160 130" fill="none" focusable="false"><path class="card-map-land" d="M29 17L89 7L144 41L137 103L72 122L12 81Z"/><path class="card-map-street" d="M29 17L72 122M89 7L65 52L137 103M12 81L144 41M22 57L119 105"/><path class="card-map-route" d="M37 89L67 70L83 43L116 52"/><circle class="card-map-dot" cx="83" cy="43" r="7"/></svg>
          </div>
          <div class="city-card-content">
            <span class="city-card-kicker">城市地图</span>
            <h3>{{ city.name }}</h3>
            <p>浏览公共地图与地点</p>
          </div>
          <span class="city-card-action">进入地图<span class="city-card-arrow" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" focusable="false"><path d="M5 12h14m-6-6 6 6-6 6" /></svg></span></span>
        </RouterLink>
      </div>
    </section>
    <footer class="page-footnote"><span>拾点地图<span class="footer-dot" aria-hidden="true">·</span>城市漫游手记</span><span>公共地图，自由浏览</span></footer>
  </div>
</template>
