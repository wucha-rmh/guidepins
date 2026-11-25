/** 公共地图与图钉契约，字段与 GET /api/cities/{cityId}/maps、GET /api/maps/{mapId} 一致。 */
export interface PublicMap {
  id: string
  /** 地图所属地市；用于校验地址中的地市与地图是否匹配。 */
  cityId: string
  name: string
}

/** 挂在图钉下的攻略摘要；完整正文由攻略接口单独提供。 */
export interface GuideSummary {
  id: string
  title: string
  /** 来源链接，可为空。 */
  sourceUrl: string | null
}

export interface MapPin {
  id: string
  name: string
  /** 站点说明，可能为空；为空时详情只显示名称。 */
  description: string | null
  /** WGS84 经度，经度在前。 */
  longitude: number
  /** WGS84 纬度。 */
  latitude: number
  /** 提到这个地点的攻略摘要；没有出处时为空数组。 */
  guides: GuideSummary[]
}

/** 地图详情在列表字段之外附带当前地图的全部图钉。 */
export interface PublicMapDetail extends PublicMap {
  pins: MapPin[]
}
