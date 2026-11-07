// Local browser-only fixture. This server never calls SOLAPI or the real backend.
import { createServer } from 'node:http'
import { createServer as createViteServer } from 'vite'

const template = { id: '11111111-1111-4111-8111-111111111111', title: '다음 방문 할인', benefit: '음료 1,000원 할인', notice: '', expiry: { mode: 'RELATIVE_DAYS', durationDays: 14 }, createdAt: '2026-11-05T00:00:00Z', updatedAt: '2026-11-05T00:00:00Z' }
const now = new Date().toISOString()
const coupons = [{ id: '22222222-2222-4222-8222-222222222222', templateId: template.id, title: '재시도 검증 쿠폰', benefit: template.benefit, notice: '', displayPhone: '010-0000-0001', customerDataDeleted: false, issuedAt: now, startsAt: now, expiresAt: '2099-12-01T15:00:00Z', redeemedAt: null, status: 'SEND_FAILED', issueMessageStatus: 'FAILED', closureReason: null, expectedMessageType: 'LMS', messageByteLength: 360, actualMessageType: 'LMS' }]
const keys = new Map()
let sequence = 0
const page = items => ({ items, page: { page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 } })
const server = createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {}
  function send(value, status = 200) { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(value)) }
  const path = url.pathname.replace('/api/v1', '')
  if (path === '/templates') return send(page([template]))
  if (path === `/templates/${template.id}`) return send(template)
  if (path === '/coupons/preview') {
    const fields = { ...template, ...body.overrides }
    const unsupported = /😀/.test(fields.notice)
    const tooLong = fields.notice.length > 1500
    const bytes = unsupported ? null : tooLong ? 2300 : 360
    return send({ previewText: `[쿠폰한장] 미리보기 시험 가게\n쿠폰: ${fields.title}\n혜택: ${fields.benefit}\n안내: ${fields.notice}\n사용 코드: ******`, byteLength: bytes, expectedMessageType: unsupported ? null : 'LMS', sendable: !unsupported && !tooLong, validationErrors: unsupported ? ['UNSUPPORTED_MESSAGE_CHARACTERS'] : tooLong ? ['MESSAGE_TOO_LONG'] : [], calculatedAt: now })
  }
  if (path === '/coupons' && req.method === 'GET') {
    const filtered = coupons.filter(coupon => (!url.searchParams.get('status') || coupon.status === url.searchParams.get('status')) && (!url.searchParams.get('title') || coupon.title.includes(url.searchParams.get('title'))))
    return send(page(filtered))
  }
  if (path === '/coupons' && req.method === 'POST') {
    const key = req.headers['idempotency-key']
    if (keys.has(key)) return send(keys.get(key), 202)
    const coupon = { ...coupons[0], ...body.overrides, id: `33333333-3333-4333-8333-${String(++sequence).padStart(12, '0')}`, title: body.overrides?.title || template.title, displayPhone: body.recipientPhone, status: 'PENDING_SEND', issueMessageStatus: 'QUEUED', actualMessageType: null }
    keys.set(key, coupon); coupons.unshift(coupon)
    setTimeout(() => { coupon.issueMessageStatus = 'DELIVERED'; coupon.status = 'ACTIVE'; coupon.actualMessageType = 'LMS' }, 1500)
    return send(coupon, 202)
  }
  const match = path.match(/^\/coupons\/([^/]+)(\/delivery-retry)?$/)
  if (match) {
    const coupon = coupons.find(item => item.id === match[1])
    if (!coupon) return send({ code: 'RESOURCE_NOT_FOUND', message: 'Not found', requestId: 'fixture' }, 404)
    if (!match[2]) return send(coupon)
    const key = req.headers['idempotency-key']
    if (keys.has(key)) return send(keys.get(key), 202)
    if (coupon.status !== 'SEND_FAILED') return send({ code: 'DELIVERY_NOT_RETRYABLE', message: 'Not retryable', requestId: 'fixture' }, 409)
    const job = { id: '44444444-4444-4444-8444-444444444444', kind: 'COUPON_ISSUED', status: 'QUEUED', requestedAt: now }
    keys.set(key, job); coupon.status = 'PENDING_SEND'; coupon.issueMessageStatus = 'QUEUED'; coupon.actualMessageType = null
    setTimeout(() => { coupon.status = 'ACTIVE'; coupon.issueMessageStatus = 'DELIVERED'; coupon.actualMessageType = 'LMS' }, 1500)
    return send(job, 202)
  }
  return send({ code: 'RESOURCE_NOT_FOUND', message: 'Not found', requestId: 'fixture' }, 404)
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const vite = await createViteServer({ server: { host: '127.0.0.1', port: 5176, strictPort: true, proxy: { '/api': { target: `http://127.0.0.1:${server.address().port}` } } } })
await vite.listen()
console.log('SMS-free fixture browser: http://localhost:5176/manage/')
for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, async () => { await vite.close(); server.close(); process.exit(0) })
