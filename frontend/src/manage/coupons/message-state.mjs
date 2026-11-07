import { changedFields, createIssueAttempt } from '../domain.mjs'

const timers = { now: () => Date.now(), set: (fn, delay) => setTimeout(fn, delay), clear: id => clearTimeout(id) }
export function previewRequest(template, fields) {
  const overrides = changedFields(template, fields)
  return { templateId: template.id, ...(Object.keys(overrides).length ? { overrides } : {}) }
}
export function createPreviewController(request, onChange, clock = timers) {
  let timer
  let requestTimer
  let abort
  let version = 0
  function cancel() {
    version++
    clock.clear(timer)
    clock.clear(requestTimer)
    abort?.abort()
  }
  async function load(body) {
    cancel()
    const current = version
    const key = JSON.stringify(body)
    abort = new AbortController()
    const signal = abort.signal
    onChange({ key, loading: true })
    try {
      const timeout = new Promise((_, reject) => {
        requestTimer = clock.set(() => {
          if (version !== current) return
          abort.abort()
          reject(new Error('문자 미리보기 조회 시간이 초과되었습니다.'))
        }, 10_000)
      })
      const data = await Promise.race([request(body, signal), timeout])
      if (version !== current) return undefined
      onChange({ key, loading: false, data })
      return data
    } catch (error) {
      if (version === current) onChange({ key, loading: false, error })
      return undefined
    } finally { if (version === current) clock.clear(requestTimer) }
  }
  return {
    cancel,
    update(body) {
      cancel()
      if (!body) { onChange({ key: null, loading: false }); return }
      onChange({ key: JSON.stringify(body), loading: true })
      timer = clock.set(() => { void load(body) }, 400)
    },
    refresh: load,
  }
}
const pending = status => ['QUEUED', 'SUBMITTED', 'UNKNOWN'].includes(status)
export function createDeliveryTracker(initial, request, onChange, clock = timers) {
  let state = { data: initial, loading: false, autoStopped: false }
  let timer
  let requestTimer
  let abort
  let version = 0
  let active = true
  let visible = true
  let startedAt = clock.now()
  function emit(patch) { state = { ...state, ...patch }; onChange(state) }
  function cancel() { version++; clock.clear(timer); clock.clear(requestTimer); abort?.abort() }
  function schedule() {
    if (!active || !visible || !pending(state.data.issueMessageStatus)) return
    const remaining = 60_000 - (clock.now() - startedAt)
    if (remaining <= 0) { emit({ autoStopped: true }); return }
    timer = clock.set(() => { void load(true) }, Math.min(3000, remaining))
  }
  async function load(automatic = false) {
    if (!active || !visible) return
    if (automatic && clock.now() - startedAt >= 60_000) { emit({ autoStopped: true }); return }
    cancel()
    const current = version
    abort = new AbortController()
    emit({ loading: true, error: undefined })
    const remaining = 60_000 - (clock.now() - startedAt)
    requestTimer = clock.set(() => {
      if (version !== current || !active) return
      cancel()
      emit({ loading: false, autoStopped: true, error: new Error('전달 상태 조회 시간이 초과되었습니다.') })
    }, remaining > 0 ? Math.min(10_000, remaining) : 10_000)
    try {
      const data = await request(initial.id, abort.signal)
      if (current !== version || !active) return
      clock.clear(requestTimer)
      emit({ data, loading: false })
      schedule()
      return data
    } catch (error) {
      if (current === version && active) {
        clock.clear(requestTimer)
        emit({ loading: false, error, autoStopped: true })
      }
    }
  }
  return {
    start() { return load() },
    refresh() { startedAt = clock.now(); emit({ autoStopped: false }); return load() },
    setVisible(value) {
      visible = value
      if (!value) { cancel(); emit({ loading: false }) }
      else if (active) void load()
    },
    stop() { active = false; cancel() },
  }
}
export function canRetryDelivery(coupon, now = Date.now()) {
  return coupon.status === 'SEND_FAILED' && coupon.issueMessageStatus === 'FAILED' && !coupon.customerDataDeleted && Date.parse(coupon.expiresAt) > now
}
export function createDeliveryRetryAttempt(couponId, send, keyFactory) {
  return createIssueAttempt({ couponId }, (body, key) => send(body.couponId, key), keyFactory)
}
