export const couponStatuses = { PENDING_SEND: '발송 대기', ACTIVE: '사용 가능', SEND_FAILED: '발송 실패', REDEEMED: '사용 완료', EXPIRED: '만료', CLOSED_INACTIVE: '비활동 종료' }
export const messageStatuses = { QUEUED: '접수 대기', SUBMITTED: '전송 요청됨', DELIVERED: '전달 완료', FAILED: '전달 실패', UNKNOWN: '확인 중' }
export const validPhone = phone => /^(010\d{8}|010-\d{4}-\d{4})$/.test(phone)
const dateString = value => /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value && value >= '0001-01-01'
export function validateFields(fields) {
  if (!fields.title.trim() || fields.title.length > 100) return '쿠폰명을 1~100자로 입력해 주세요.'
  if (!fields.benefit.trim() || fields.benefit.length > 500) return '혜택을 1~500자로 입력해 주세요.'
  if (fields.notice.length > 2000) return '안내문은 2,000자 이내로 입력해 주세요.'
  const expiry = fields.expiry
  if (expiry.mode === 'RELATIVE_DAYS') {
    if (!Number.isSafeInteger(expiry.durationDays) || expiry.durationDays < 1 || !relativeEnd(expiry.durationDays)) return '유효 기간을 올바른 양의 정수로 입력해 주세요.'
  } else if (!dateString(expiry.startDate) || !dateString(expiry.date) || expiry.startDate > expiry.date || expiry.date === '9999-12-31') return '시작일과 종료일을 확인해 주세요.'
  return ''
}
export function changedFields(original, next) {
  return Object.fromEntries(['title', 'benefit', 'notice', 'expiry'].filter(key => JSON.stringify(original[key]) !== JSON.stringify(next[key])).map(key => [key, next[key]]))
}
export function issueBody(templateId, original, fields, phone) {
  const overrides = changedFields(original, fields)
  return { templateId, recipientPhone: phone.replaceAll('-', ''), consentConfirmed: true, ...(Object.keys(overrides).length ? { overrides } : {}) }
}
function kstDate(value) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(value))
  return ['year', 'month', 'day'].map(type => parts.find(part => part.type === type).value).join('-')
}
export const displayDate = date => date.replace(/^(\d{4})-(\d{2})-(\d{2})$/, '$1. $2. $3.')
export function relativeEnd(days, now = new Date()) {
  const date = new Date(`${kstDate(now)}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days - 1)
  return !Number.isNaN(date.getTime()) && date.getUTCFullYear() <= 9999 ? date.toISOString().slice(0, 10) : ''
}
export function expiryText(expiry, now = new Date()) {
  if (expiry.mode === 'FIXED_DATE') return `${displayDate(expiry.startDate)} ~ ${displayDate(expiry.date)}`
  const end = relativeEnd(expiry.durationDays, now)
  return end ? `발급일 포함 ${expiry.durationDays}일 · 오늘 발급 시 ${displayDate(end)}까지` : '유효 기간을 입력해 주세요.'
}
export const periodText = (start, end) => `${displayDate(kstDate(start))} ~ ${displayDate(kstDate(new Date(new Date(end).getTime() - 1)))}`
export const timeText = value => new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
export function latestRequest() {
  let controller
  return {
    cancel() { controller?.abort() },
    async run(work, apply) {
      controller?.abort()
      const current = new AbortController()
      controller = current
      try {
        const result = await work(current.signal)
        if (!current.signal.aborted) apply(result)
      } catch (error) { if (!current.signal.aborted) throw error }
    },
  }
}
function requestKey() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
export function createIssueAttempt(body, send, keyFactory = requestKey) {
  const payload = JSON.parse(JSON.stringify(body))
  let key
  let pending
  let result
  return { send() {
    if (result) return Promise.resolve(result)
    if (pending) return pending
    try {
      key ??= keyFactory()
      pending = Promise.resolve(send(payload, key)).then(value => { result = value; return value }).finally(() => { pending = undefined })
    } catch (error) { return Promise.reject(error) }
    return pending
  } }
}
