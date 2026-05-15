import { getInviterUserId, setInviterUserId } from '@/utils/storage'

/**
 * 从小程序启动参数解析邀请人。
 *
 * 约定（你后端也按这个字段接收）：
 * - inviterUserId：邀请人用户ID（app_user.id）
 */
export function captureInviterFromLaunch(options?: Record<string, any>) {
  const query = (options as any)?.query || options || {}

  // 1) 常规 query 参数：?inviterUserId=123
  const raw = query.inviterUserId ?? query.parentUserId ?? query.shareUserId
  if (raw !== undefined && raw !== null && raw !== '') {
    const n = Number(raw)
    if (Number.isFinite(n) && n > 0) {
      setInviterUserId(n)
      return
    }
  }

  // 2) scene 场景值（扫码/分享进入）：这里先留占位，后续你决定编码方式再解析
  // const scene = query.scene
  // TODO: decode scene -> inviterUserId
}

export function hasInviter(): boolean {
  return getInviterUserId() != null
}

