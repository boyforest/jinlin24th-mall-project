import { apiRequest } from '@/api/client'

export interface WxPayParamsVO {
  appId?: string
  timeStamp: string
  nonceStr: string
  packageValue: string
  signType: 'RSA' | 'MD5' | string
  paySign: string
  prepayId?: string
}

export function createOrderPayment(orderId: number) {
  return apiRequest<WxPayParamsVO>(`/user/order/${orderId}/pay`, { method: 'POST' })
}

export function requestMiniAppPayment(params: WxPayParamsVO) {
  return new Promise<void>((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: params.timeStamp,
      nonceStr: params.nonceStr,
      package: params.packageValue,
      signType: params.signType,
      paySign: params.paySign,
      success: () => resolve(),
      fail: (error: any) => reject(error),
    } as any)
  })
}
