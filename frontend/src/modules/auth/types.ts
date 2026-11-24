/** 当前账号公开给本人的身份信息，不含密码或会话令牌。 */
export interface AccountIdentity {
  id: string
  email: string | null
  phone: string | null
  role: 'user' | 'admin'
  avatarPath: string | null
  createdAt: string
}

export type Credentials = { email: string; password: string } | { phone: string; password: string }
