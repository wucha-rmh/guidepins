/** 图钉编辑模块的本地类型：只在地图交互、编辑面板和写入接口之间传递。 */
export interface PinCoordinates {
  /** WGS84 经度，经度在前，与后端字段顺序一致。 */
  longitude: number
  /** WGS84 纬度。 */
  latitude: number
}

/** 正在编辑的对象：新建时没有 pinId，修改时指向详情里已有的图钉。 */
export type PinEditingTarget = { kind: 'create' } | { kind: 'edit'; pinId: string }
