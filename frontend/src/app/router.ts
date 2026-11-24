import { createRouter, createWebHashHistory } from 'vue-router'
import CitySelectionPage from '../modules/city/CitySelectionPage.vue'
import NotFoundPage from './NotFoundPage.vue'

// Hash 路由支持直接访问和刷新恢复地市，不依赖服务器页面回退配置。
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'cities', component: CitySelectionPage, meta: { title: '选择地市' } },
    { path: '/auth', name: 'auth', component: () => import('../modules/auth/AuthPage.vue'), meta: { title: '登录与注册' } },
    // 保留旧入口及原地图回跳参数，统一在同一页内切换认证方式。
    { path: '/login', name: 'login', redirect: (to) => ({ name: 'auth', query: { ...to.query, mode: 'login' } }) },
    { path: '/register', name: 'register', redirect: (to) => ({ name: 'auth', query: { ...to.query, mode: 'register' } }) },
    { path: '/cities/:cityId', name: 'city-map', component: () => import('./MapPage.vue'), meta: { title: '地市地图' } },
    { path: '/cities/:cityId/maps/:mapId', name: 'public-map', component: () => import('./MapPage.vue'), meta: { title: '地图详情' } },
    { path: '/:pathMatch(.*)*', component: NotFoundPage, meta: { title: '页面不存在' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '地图')} · 拾点地图`
})
