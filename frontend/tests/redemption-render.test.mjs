import { test } from 'node:test'
import assert from 'node:assert/strict'
import { bundleModule } from './helpers/bundle.mjs'
test('active detail offers accessible string code input while redeemed detail shows completion', async () => {
    await bundleModule("import React from 'react';import {renderToStaticMarkup} from 'react-dom/server';import {CouponDetail} from './src/manage/coupons/CouponDetail';module.exports=coupon=>renderToStaticMarkup(React.createElement(CouponDetail,{coupon,onClose:()=>{},onChanged:()=>{}}));", render => {
        const coupon = { id: 'c', title: '시험', benefit: '혜택', notice: '', displayPhone: '010-****-****', customerDataDeleted: false, status: 'ACTIVE', issueMessageStatus: 'DELIVERED', issuedAt: '2026-11-01T00:00:00Z', startsAt: '2026-11-01T00:00:00Z', expiresAt: '2099-01-01T00:00:00Z' }
        const active = render(coupon); assert.match(active, /사용 코드/); assert.match(active, /inputMode="numeric"/); assert.match(active, /maxLength="6"/); assert.match(active, /실제 혜택/)
        const redeemed = render({ ...coupon, status: 'REDEEMED', redeemedAt: '2026-11-08T00:00:00Z' })
        assert.match(redeemed, /사용 완료일/); assert.doesNotMatch(redeemed, /inputMode="numeric"/)
        const deleted = render({ ...coupon, customerDataDeleted: true }); assert.doesNotMatch(deleted, /inputMode="numeric"/)
    })
})

test('delivery retry buttons respect a shared modal operation lock', async () => {
    await bundleModule("import React from 'react';import {renderToStaticMarkup} from 'react-dom/server';import {DeliveryRetry} from './src/manage/coupons/DeliveryRetry';module.exports=()=>renderToStaticMarkup(React.createElement(DeliveryRetry,{coupon:{id:'c',status:'SEND_FAILED',issueMessageStatus:'FAILED',customerDataDeleted:false,expiresAt:'2099-01-01T00:00:00Z'},disabled:true,onRefresh:()=>{},onBusy:()=>{},onUncertain:()=>{}}));", render => {
        assert.match(render(), /<button[^>]*disabled[^>]*>문자 다시 보내기<\/button>/)
    })
})
