import { test } from 'node:test'
import assert from 'node:assert/strict'
import { canRetryDelivery, createDeliveryRetryAttempt } from '../src/manage/coupons/message-state.mjs'

const failed = { id: 'coupon', status: 'SEND_FAILED', issueMessageStatus: 'FAILED', expiresAt: '2026-12-01T00:00:00Z', customerDataDeleted: false }
test('only confirmed failed, unexpired deliveries with customer information allow retry', () => {
  const now = Date.parse('2026-11-05T00:00:00Z')
  assert.equal(canRetryDelivery(failed, now), true)
  for (const patch of [{ customerDataDeleted: true }, { status: 'EXPIRED' }, { issueMessageStatus: 'UNKNOWN' }, { issueMessageStatus: 'QUEUED' }, { issueMessageStatus: 'SUBMITTED' }, { issueMessageStatus: 'DELIVERED' }]) assert.equal(canRetryDelivery({ ...failed, ...patch }, now), false)
  assert.equal(canRetryDelivery(failed, Date.parse(failed.expiresAt)), false)
})
test('response loss and repeated retry clicks reuse the same job key for the same coupon', async () => {
  const calls = []
  let reject
  const retry = createDeliveryRetryAttempt('coupon', (id, key) => {
    calls.push({ id, key })
    if (calls.length === 1) return new Promise((_, fail) => { reject = fail })
    return Promise.resolve({ id: 'outbox-job', status: 'QUEUED' })
  }, () => 'retry-key')
  const first = retry.send()
  assert.equal(retry.send(), first)
  reject(new Error('lost response'))
  await assert.rejects(first)
  assert.equal((await retry.send()).id, 'outbox-job')
  assert.deepEqual(calls, [{ id: 'coupon', key: 'retry-key' }, { id: 'coupon', key: 'retry-key' }])
  await retry.send()
  assert.equal(calls.length, 2)
})
