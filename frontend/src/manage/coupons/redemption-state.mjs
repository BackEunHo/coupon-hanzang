import { createIssueAttempt } from '../domain.mjs'
export function canRedeemCoupon(coupon, now = Date.now()) {
  return coupon.status === 'ACTIVE' && coupon.issueMessageStatus === 'DELIVERED' && !coupon.customerDataDeleted
    && Date.parse(coupon.startsAt) <= now && now < Date.parse(coupon.expiresAt)
}
export function createRedemptionAttempt(couponId, code, send, keyFactory) {
  return createIssueAttempt({ code }, (body, key) => send(couponId, body, key), keyFactory)
}
