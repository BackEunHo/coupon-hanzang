import { test } from 'node:test'
import assert from 'node:assert/strict'
import { bundleModule } from './helpers/bundle.mjs'
import { ApiError, errorText } from '../src/api/client.mjs'
test('redemption sends strict string code, session and stable idempotency header', async () => {
    const old = globalThis.fetch; const calls = []
    globalThis.fetch = async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify({ status: 'REDEEMED' })) }
    try {
        await bundleModule("import {api} from './src/api'; module.exports=api;", async api => {
            await api.redeemCoupon('coupon/id', { code: '012345' }, 'request-key')
            const { url, options } = calls[0]
            assert.equal(url, '/api/v1/coupons/coupon%2Fid/redeem'); assert.equal(options.method, 'POST'); assert.equal(options.credentials, 'include')
            assert.equal(options.headers['Idempotency-Key'], 'request-key'); assert.deepEqual(JSON.parse(options.body), { code: '012345' })
        })
    } finally { globalThis.fetch = old }
})
test('redemption errors explain incorrect code, unavailable coupon and code cooldown', () => {
    assert.match(errorText(new ApiError(422, { code: 'COUPON_CODE_INVALID' })), /코드/)
    assert.match(errorText(new ApiError(409, { code: 'COUPON_NOT_REDEEMABLE' })), /사용|상태/)
    assert.match(errorText(new ApiError(429, { code: 'COUPON_CODE_RATE_LIMITED' })), /15분/)
})
