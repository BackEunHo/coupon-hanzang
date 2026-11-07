import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createDeliveryTracker } from '../src/manage/coupons/message-state.mjs'
import { fakeTime } from './helpers/time.mjs'

const coupon = status => ({ id: 'coupon', status: 'PENDING_SEND', issueMessageStatus: status })
test('detail polls pending deliveries and stops on final delivery without rewriting coupon status', async () => {
  const time = fakeTime()
  const responses = [coupon('QUEUED'), coupon('SUBMITTED'), { ...coupon('DELIVERED'), status: 'EXPIRED', actualMessageType: 'LMS' }]
  let state
  let calls = 0
  const tracker = createDeliveryTracker(coupon('QUEUED'), async () => responses[calls++], value => { state = value }, time)
  await tracker.start()
  await time.advance(3000)
  assert.equal(state.data.issueMessageStatus, 'SUBMITTED')
  await time.advance(3000)
  assert.equal(state.data.status, 'EXPIRED')
  assert.equal(state.data.actualMessageType, 'LMS')
  assert.equal(time.count(), 0)
  tracker.stop()
})
test('polling stops after one minute, permits manual refresh and cancels on close', async () => {
  const time = fakeTime()
  let calls = 0
  let state
  const tracker = createDeliveryTracker(coupon('UNKNOWN'), async () => { calls++; return coupon('UNKNOWN') }, value => { state = value }, time)
  await tracker.start()
  for (let i = 0; i < 20; i++) await time.advance(3000)
  assert.equal(state.autoStopped, true)
  assert.equal(time.count(), 0)
  const before = calls
  await tracker.refresh()
  assert.equal(calls, before + 1)
  tracker.stop()
  assert.equal(time.count(), 0)
})
test('hidden and closed dialogs ignore in-flight responses; failures retain last good data', async () => {
  const time = fakeTime()
  const requests = []
  let state
  const initial = coupon('SUBMITTED')
  const tracker = createDeliveryTracker(initial, (_, signal) => new Promise((resolve, reject) => requests.push({ resolve, reject, signal })), value => { state = value }, time)
  const first = tracker.start()
  tracker.setVisible(false)
  requests[0].resolve(coupon('DELIVERED'))
  await first
  assert.equal(state.data, initial)
  assert.equal(time.count(), 0)
  tracker.setVisible(true)
  requests[1].reject(new Error('offline'))
  await time.advance(0)
  assert.equal(state.data, initial)
  assert.ok(state.error)
  assert.equal(time.count(), 0)
  tracker.stop()
})
test('a stalled request is aborted within the polling window and late results cannot change the detail', async () => {
  const time = fakeTime()
  let signal
  let finish
  let state
  const initial = coupon('QUEUED')
  const tracker = createDeliveryTracker(initial, (_, inputSignal) => { signal = inputSignal; return new Promise(resolve => { finish = resolve }) }, value => { state = value }, time)
  const pending = tracker.start()
  await time.advance(60_000)
  assert.equal(signal.aborted, true)
  assert.equal(state.loading, false)
  assert.equal(state.autoStopped, true)
  finish(coupon('DELIVERED'))
  await pending
  assert.equal(state.data, initial)
  tracker.stop()
})
test('manual detail refresh returns only its newly confirmed coupon, never stale failed data', async () => {
  const time = fakeTime()
  const initial = { ...coupon('FAILED'), status: 'SEND_FAILED' }
  const next = coupon('QUEUED')
  let fail = false
  const tracker = createDeliveryTracker(initial, async () => { if (fail) throw new Error('offline'); return next }, () => {}, time)
  assert.equal(await tracker.refresh(), next)
  fail = true
  assert.equal(await tracker.refresh(), undefined)
  tracker.stop()
})
