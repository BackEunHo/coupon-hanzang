import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validateFields, changedFields, issueBody, periodText, expiryText, validPhone, createIssueAttempt, latestRequest } from '../src/manage/domain.mjs'

const fields = { title: '커피 쿠폰', benefit: '1,000원 할인', notice: '', expiry: { mode: 'RELATIVE_DAYS', durationDays: 14 } }
test('coupon fields enforce required text, length and calendar ranges', () => {
  assert.equal(validateFields(fields), '')
  for (const patch of [{ title: ' ' }, { benefit: 'x'.repeat(501) }, { notice: 'x'.repeat(2001) }, { expiry: { mode: 'RELATIVE_DAYS', durationDays: 0 } }, { expiry: { mode: 'RELATIVE_DAYS', durationDays: 1.5 } }, { expiry: { mode: 'FIXED_DATE', startDate: '2026-02-30', date: '2026-03-01' } }, { expiry: { mode: 'FIXED_DATE', startDate: '2026-11-10', date: '2026-11-01' } }]) assert.ok(validateFields({ ...fields, ...patch }))
})
test('only changed fields go into PATCH and issuance overrides', () => {
  assert.deepEqual(changedFields(fields, { ...fields, title: '새 쿠폰' }), { title: '새 쿠폰' })
  assert.deepEqual(issueBody('id', fields, fields, '010-1234-5678'), { templateId: 'id', recipientPhone: '01012345678', consentConfirmed: true })
  assert.deepEqual(issueBody('id', fields, { ...fields, notice: '당일 1회' }, '01012345678').overrides, { notice: '당일 1회' })
  assert.equal(validPhone('010-1234-5678'), true)
  assert.equal(validPhone('0101234'), false)
})
test('Korean dates include issue day and respect exclusive expiry at midnight', () => {
  assert.equal(expiryText(fields.expiry, new Date('2026-11-01T15:05:00Z')), '발급일 포함 14일 · 오늘 발급 시 2026. 11. 15.까지')
  assert.equal(periodText('2026-11-01T15:00:00Z', '2026-11-15T15:00:00Z'), '2026. 11. 02. ~ 2026. 11. 15.')
})
test('issuance freezes payload, coalesces double clicks and reuses key after response loss', async () => {
  const requests = []
  let complete
  const body = issueBody('id', fields, fields, '01012345678')
  const attempt = createIssueAttempt(body, async (payload, key) => {
    requests.push({ payload, key })
    if (requests.length === 1) return new Promise((_, reject) => { complete = reject })
    return { id: 'coupon-1' }
  }, () => 'fixed-key')
  body.recipientPhone = '01099999999'
  const first = attempt.send()
  const duplicate = attempt.send()
  assert.equal(first, duplicate)
  complete(new Error('connection lost'))
  await assert.rejects(first)
  assert.deepEqual(await attempt.send(), { id: 'coupon-1' })
  assert.equal(requests.length, 2)
  assert.equal(requests[0].key, requests[1].key)
  assert.equal(requests[1].payload.recipientPhone, '01012345678')
})
test('key generation failure rejects safely and the attempt can be retried', async () => {
  let keys = 0
  const attempt = createIssueAttempt({}, async () => 'ok', () => { if (++keys === 1) throw new Error('crypto unavailable'); return 'key' })
  await assert.rejects(attempt.send(), /crypto unavailable/)
  assert.equal(await attempt.send(), 'ok')
})
test('obsolete template detail cannot replace a newer editing intent or update after unmount', async () => {
  const latest = latestRequest()
  let finish
  const applied = []
  const old = latest.run(() => new Promise(resolve => { finish = resolve }), value => applied.push(value))
  latest.cancel()
  finish('old-template')
  await old
  assert.deepEqual(applied, [])
  await latest.run(async () => 'fresh-template', value => applied.push(value))
  assert.deepEqual(applied, ['fresh-template'])
})
