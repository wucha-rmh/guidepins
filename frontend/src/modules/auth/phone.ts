import { phoneCallingCodes } from './phoneCallingCodes'

export const DEFAULT_PHONE_REGION = 'CN'

const regionNames = new Intl.DisplayNames(['zh-CN'], { type: 'region' })
const displayOverrides: Record<string, string> = { CN: '中国', HK: '中国香港', MO: '中国澳门', TW: '中国台湾' }
const preferredRegions = ['CN', 'HK', 'MO', 'TW']

/** 地区名称由浏览器本地化，使用地区作为选项值，避免共享 +1 等区号的地区互相覆盖。 */
export const phoneRegions = phoneCallingCodes.map(([region, callingCode]) => ({
  region,
  name: displayOverrides[region] ?? regionNames.of(region) ?? region,
  callingCode: `+${callingCode}`,
})).sort((a, b) => {
  const aRank = preferredRegions.indexOf(a.region)
  const bRank = preferredRegions.indexOf(b.region)
  return (aRank < 0 ? preferredRegions.length : aRank) - (bRank < 0 ? preferredRegions.length : bRank) ||
    a.name.localeCompare(b.name, 'zh-CN')
})

/** 将两个输入组合为后端既有 E.164 形式；保留数字，不猜测各地的国内长途前缀。 */
export function phoneForRequest(region: string, number: string): string {
  const selected = phoneRegions.find((item) => item.region === region)
  if (!selected) throw new Error('请选择国家／地区码。')
  let nationalNumber = number.trim()
  if (nationalNumber.startsWith('+')) {
    // 允许粘贴带当前区号的完整号码；不同区号要求用户先纠正选择，避免静默改错账号。
    if (!nationalNumber.startsWith(selected.callingCode)) {
      throw new Error('号码中的区号与所选国家／地区不一致，请修改区号或只填写手机号。')
    }
    nationalNumber = nationalNumber.slice(selected.callingCode.length)
  }
  if (!/^[0-9]+$/.test(nationalNumber)) throw new Error('请输入仅含数字的手机号，区号在左侧选择。')
  const fullNumber = selected.callingCode + nationalNumber
  if (!/^\+[1-9][0-9]{1,14}$/.test(fullNumber)) throw new Error('区号和手机号合计需为 2 至 15 位数字。')
  return fullNumber
}
