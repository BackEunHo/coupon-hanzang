import { test } from 'node:test'
import assert from 'node:assert/strict'
import { bundleModule } from './helpers/bundle.mjs'
import { ApiError, errorText } from '../src/api/client.mjs'

test('preview posts only message fields and retry sends no body with its own idempotency key', async () => {
    const previous = globalThis.fetch
    const calls = []
    const preview = { previewText: '사용 코드: ******', byteLength: null, expectedMessageType: null, sendable: false, validationErrors: ['UNSUPPORTED_MESSAGE_CHARACTERS'], calculatedAt: '2026-11-05T00:00:00Z' }
    globalThis.fetch = async (url, init) => {
      calls.push({ url, init })
      return new Response(JSON.stringify(calls.length === 1 ? preview : { id: 'job', kind: 'COUPON_ISSUED', status: 'QUEUED', requestedAt: '2026-11-05T00:00:00Z' }), { status: calls.length === 1 ? 200 : 202 })
    }
    try {
      // Import after installing the transport: createApiClient captures fetch at module load.
      await bundleModule(`import {api} from './src/api'; module.exports=api`, async client => {
        assert.deepEqual(await client.preview({ templateId: 'template', overrides: { notice: '안내' } }), preview)
        const job = await client.retryDelivery('coupon', 'retry-key')
        assert.equal(job.id, 'job')
      })
      assert.equal(calls[0].url, '/api/v1/coupons/preview')
      assert.deepEqual(JSON.parse(calls[0].init.body), { templateId: 'template', overrides: { notice: '안내' } })
      assert.equal(calls[0].init.credentials, 'include')
      assert.equal(calls[1].url, '/api/v1/coupons/coupon/delivery-retry')
      assert.equal(calls[1].init.body, undefined)
      assert.equal(calls[1].init.headers['Idempotency-Key'], 'retry-key')
    } finally { globalThis.fetch = previous }
})
test('delivery business errors explain the recoverable action rather than mislabeling every 403', () => {
  assert.match(errorText(new ApiError(403, { code: 'RECIPIENT_NOT_ALLOWED' })), /시험.*번호/)
  assert.match(errorText(new ApiError(422, { code: 'MESSAGE_TOO_LONG' })), /2,000/)
  assert.match(errorText(new ApiError(422, { code: 'UNSUPPORTED_MESSAGE_CHARACTERS' })), /문자|이모지/)
  assert.match(errorText(new ApiError(409, { code: 'DELIVERY_NOT_RETRYABLE' })), /다시|재시도/)
})
