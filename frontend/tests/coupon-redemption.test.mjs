import { test } from 'node:test'
import assert from 'node:assert/strict'
import { canRedeemCoupon, createRedemptionAttempt } from '../src/manage/coupons/redemption-state.mjs'
import { createDeliveryTracker } from '../src/manage/coupons/message-state.mjs'
import { fakeTime } from './helpers/time.mjs'
const coupon = { id: 'coupon', status: 'ACTIVE', issueMessageStatus: 'DELIVERED', customerDataDeleted: false, startsAt: '2026-11-01T00:00:00Z', expiresAt: '2026-11-02T00:00:00Z' }
test('redemption eligibility includes start, excludes expiry and rejects all inactive conditions', () => {
    const start = Date.parse(coupon.startsAt), end = Date.parse(coupon.expiresAt)
    assert.equal(canRedeemCoupon(coupon, start), true)
    assert.equal(canRedeemCoupon(coupon, start - 1), false)
    assert.equal(canRedeemCoupon(coupon, end), false)
    for (const status of ['PENDING_SEND', 'SEND_FAILED', 'REDEEMED', 'EXPIRED', 'CLOSED_INACTIVE']) assert.equal(canRedeemCoupon({ ...coupon, status }, start), false)
    assert.equal(canRedeemCoupon({ ...coupon, customerDataDeleted: true }, start), false)
    assert.equal(canRedeemCoupon({ ...coupon, issueMessageStatus: 'UNKNOWN' }, start), false)
})
test('attempt preserves leading zero and key across lost responses and deduplicates concurrent clicks', async () => {
    const calls = []; let finish; let count = 0
    const attempt = createRedemptionAttempt('coupon', '012345', (id, body, key) => { calls.push({ id, body, key }); if (count++ === 0) return Promise.reject(new Error('offline')); return new Promise(resolve => finish = resolve) }, () => 'fixed-key')
    await assert.rejects(attempt.send())
    const a = attempt.send(), b = attempt.send()
    assert.equal(a, b); finish({ ...coupon, status: 'REDEEMED' }); await a
    await attempt.send()
    assert.equal(calls.length, 2)
    assert.deepEqual(calls[0], { id: 'coupon', body: { code: '012345' }, key: 'fixed-key' })
    assert.deepEqual(calls[0], calls[1])
})
test('replacing detail with redeemed result aborts older GET and ignores its late response', async () => {
    const time = fakeTime(); let finish, signal, state
    const tracker = createDeliveryTracker(coupon, (_, s) => { signal = s; return new Promise(resolve => finish = resolve) }, v => state = v, time)
    const loading = tracker.start()
    tracker.replace({ ...coupon, status: 'REDEEMED', redeemedAt: '2026-11-01T01:00:00Z' })
    assert.equal(signal.aborted, true)
    finish(coupon); await loading
    assert.equal(state.data.status, 'REDEEMED'); assert.equal(state.loading, false); assert.equal(time.count(), 0)
    tracker.stop()
})
