import type { Coupon, RedeemRequest } from '../../api/types'
export function canRedeemCoupon(coupon: Coupon, now?: number): boolean
export function createRedemptionAttempt(id: string, code: string, send: (id: string, body: RedeemRequest, key: string) => Promise<Coupon>, keyFactory?: () => string): { send: () => Promise<Coupon> }
