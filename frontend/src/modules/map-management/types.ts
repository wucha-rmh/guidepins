/** 地图管理接口返回的元数据；详情和图钉仍由现有地图模块读取。 */
export interface ManagedMap {
  id: string
  cityId: string
  name: string
  ownerId: string
  visibility: 'private' | 'public'
  createdAt: string
  updatedAt: string
}
