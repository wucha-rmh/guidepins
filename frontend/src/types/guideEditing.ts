import type { GuideSummary } from './publicMap'

/** 攻略与图钉的关联，position 是这篇攻略里的站序，从 0 开始。 */
export interface GuidePinLink {
  pinId: string
  position: number
}

/** 攻略详情；字段与 /api/maps/{mapId}/guides 系列接口保持一致。 */
export interface GuideDetail extends GuideSummary {
  mapId: string
  /** 攻略来源：manual 手动录入，agent 外部抓取（本阶段只产生 manual）。 */
  origin: string
  /** 正文或摘录，1 至 5000 字符。 */
  body: string
  createdAt: string
  updatedAt: string
  /** 按站序排列的关联图钉。 */
  pins: GuidePinLink[]
}

/** 新建与修改攻略时提交的字段。 */
export interface GuideValues {
  title: string
  body: string
  sourceUrl: string | null
}
