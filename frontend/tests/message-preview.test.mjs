import { test } from 'node:test'
import assert from 'node:assert/strict'
import { fakeTime } from './helpers/time.mjs'
import { createPreviewController, previewRequest } from '../src/manage/coupons/message-state.mjs'

const fields = { title: '쿠폰', benefit: '할인', notice: '', expiry: { mode: 'RELATIVE_DAYS', durationDays: 14 } }
const fixture = (bytes, sendable = true) => ({ previewText: '서버 원문\n사용 코드: ******', byteLength: bytes, expectedMessageType: bytes === null ? null : bytes <= 90 ? 'SMS' : 'LMS', sendable, validationErrors: sendable ? [] : [bytes === null ? 'UNSUPPORTED_MESSAGE_CHARACTERS' : 'MESSAGE_TOO_LONG'], calculatedAt: '2026-11-05T00:00:00Z' })
test('preview request includes only template and changed fields, without empty overrides', () => {
  assert.deepEqual(previewRequest({ ...fields, id: 'template' }, fields), { templateId: 'template' })
  assert.deepEqual(previewRequest({ ...fields, id: 'template' }, { ...fields, notice: '안내' }), { templateId: 'template', overrides: { notice: '안내' } })
})
test('preview debounces input, invalidates immediately and ignores obsolete responses', async () => {
  const time = fakeTime()
  const requests = []
  let state
  const controller = createPreviewController((body, signal) => new Promise(resolve => requests.push({ body, signal, resolve })), value => { state = value }, time)
  controller.update({ templateId: 'a' })
  await time.advance(399)
  assert.equal(requests.length, 0)
  await time.advance(1)
  controller.update({ templateId: 'b' })
  assert.equal(state.loading, true)
  assert.equal(state.data, undefined)
  assert.equal(requests[0].signal.aborted, true)
  await time.advance(400)
  requests[1].resolve(fixture(91))
  await time.advance(0)
  requests[0].resolve(fixture(90))
  await time.advance(0)
  assert.equal(state.data.byteLength, 91)
  assert.equal(state.key, '{"templateId":"b"}')
  controller.cancel()
  assert.equal(time.count(), 0)
})
test('HTTP success retains server sendability, null bytes, and full text at all limits', async () => {
  for (const data of [fixture(90), fixture(91), fixture(2000), fixture(2001, false), fixture(null, false)]) {
    let state
    const controller = createPreviewController(async () => data, value => { state = value }, fakeTime())
    assert.deepEqual(await controller.refresh({ templateId: 'id' }), data)
    assert.deepEqual(state.data, data)
    controller.cancel()
  }
})
test('preview failure clears successful data and manual refresh obtains a new result', async () => {
  let state
  let fail = false
  const controller = createPreviewController(async () => { if (fail) throw new Error('offline'); return fixture(100) }, value => { state = value }, fakeTime())
  await controller.refresh({ templateId: 'id' })
  fail = true
  assert.equal(await controller.refresh({ templateId: 'id' }), undefined)
  assert.equal(state.data, undefined)
  assert.ok(state.error)
  controller.cancel()
})
test('final preview refresh settles on timeout so issuing controls can unlock', async () => {
  const time = fakeTime()
  let state
  let signal
  const controller = createPreviewController((_, inputSignal) => { signal = inputSignal; return new Promise(() => {}) }, value => { state = value }, time)
  let settled = false
  const refresh = controller.refresh({ templateId: 'id' }).then(value => { settled = true; return value })
  await time.advance(10_000)
  assert.equal(settled, true)
  assert.equal(await refresh, undefined)
  assert.equal(signal.aborted, true)
  assert.equal(state.loading, false)
  assert.ok(state.error)
  controller.cancel()
})
