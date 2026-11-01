import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createApiClient, ApiError, errorText } from '../src/api/client.mjs'

test('API client sends cookies and idempotency header and accepts empty success', async () => {
  const calls = []
  const request = createApiClient({ base: 'https://api.example', fetcher: async (url, init) => {
    calls.push({ url, init }); return new Response(null, { status: 204 })
  } })
  assert.equal(await request('/api/v1/coupons', { method: 'POST', body: { title: '쿠폰' }, key: 'key' }), undefined)
  assert.equal(calls[0].url, 'https://api.example/api/v1/coupons')
  assert.equal(calls[0].init.credentials, 'include')
  assert.equal(calls[0].init.headers['Idempotency-Key'], 'key')
  assert.equal(calls[0].init.body, '{"title":"쿠폰"}')
})
test('server authentication failure triggers expiry handling; domain errors keep code and request ID', async () => {
  let expired = 0
  const request = createApiClient({ onUnauthorized: () => expired++, fetcher: async () => new Response(JSON.stringify({ code: 'QUOTA_EXCEEDED', message: '한도 초과', requestId: 'request-1' }), { status: 409 }) })
  await assert.rejects(request('/test'), error => error instanceof ApiError && error.code === 'QUOTA_EXCEEDED' && error.requestId === 'request-1' && /한도/.test(errorText(error)))
  assert.equal(expired, 0)
  const unauthorized = createApiClient({ onUnauthorized: () => expired++, fetcher: async () => new Response(null, { status: 401 }) })
  await assert.rejects(unauthorized('/test'))
  assert.equal(expired, 1)
})
test('network failure does not automatically repeat a mutation', async () => {
  let calls = 0
  const request = createApiClient({ fetcher: async () => { calls++; throw new TypeError('Failed to fetch') } })
  await assert.rejects(request('/test', { method: 'POST', body: {} }))
  assert.equal(calls, 1)
})
