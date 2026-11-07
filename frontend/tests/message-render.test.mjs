import { test } from 'node:test'
import assert from 'node:assert/strict'
import { bundleModule } from './helpers/bundle.mjs'

test('server preview displays byte counts, preserves masked text and explains unsendable messages', async () => {
  await bundleModule(`import React from 'react'; import {renderToStaticMarkup} from 'react-dom/server'; import {MessagePreview} from './src/manage/coupons/MessagePreview'; module.exports=(data)=>renderToStaticMarkup(React.createElement(MessagePreview,{data, loading:false,onRefresh:()=>{}}));`, render => {
    const html = render({ previewText: '서버 본문\n사용 코드: ******', byteLength: 2001, expectedMessageType: 'LMS', sendable: false, validationErrors: ['MESSAGE_TOO_LONG'], calculatedAt: '2026-11-05T00:00:00Z' })
    assert.match(html, /2,001바이트/)
    assert.match(html, /LMS 예상/)
    assert.match(html, /서버 본문\n사용 코드: \*\*\*\*\*\*/)
    assert.match(html, /role="alert"/)
    assert.match(html, /안내문을 줄여/)
  })
})
test('detail preserves expired coupon status and hides retry for unknown delivery and old null metadata', async () => {
  await bundleModule(`import React from 'react'; import {renderToStaticMarkup} from 'react-dom/server'; import {CouponDetail} from './src/manage/coupons/CouponDetail'; module.exports=(coupon)=>renderToStaticMarkup(React.createElement(CouponDetail,{coupon,onClose:()=>{}}));`, render => {
    const coupon = { id: 'coupon', title: '시험 쿠폰', benefit: '혜택', notice: '', displayPhone: '010-****-****', customerDataDeleted: true, issuedAt: '2026-11-01T00:00:00Z', startsAt: '2026-11-01T00:00:00Z', expiresAt: '2026-11-02T15:00:00Z', status: 'EXPIRED', issueMessageStatus: 'DELIVERED', expectedMessageType: null, messageByteLength: null, actualMessageType: null }
    const expired = render(coupon)
    assert.match(expired, /만료/)
    assert.match(expired, /정보 없음/)
    assert.doesNotMatch(expired, /문자 다시 보내기/)
    const unknown = render({ ...coupon, status: 'PENDING_SEND', issueMessageStatus: 'UNKNOWN' })
    assert.match(unknown, /중복 발송 방지/)
    assert.doesNotMatch(unknown, /문자 다시 보내기/)
  })
})
