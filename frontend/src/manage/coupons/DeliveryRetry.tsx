import { useRef, useState } from 'react'
import { api } from '../../api'
import { ApiError } from '../../api/client.mjs'
import type { Coupon } from '../../api/types'
import { ErrorNotice } from '../shared'
import { canRetryDelivery, createDeliveryRetryAttempt } from './message-state.mjs'

export function DeliveryRetry({ coupon, onRefresh, onBusy, onUncertain }: { coupon: Coupon; onRefresh: () => Promise<Coupon | undefined> | undefined; onBusy: (busy: boolean) => void; onUncertain: (uncertain: boolean) => void }) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [completed, setCompleted] = useState(false)
  const [confirmedAfterRetry, setConfirmedAfterRetry] = useState(false)
  const [error, setError] = useState<unknown>()
  const attempt = useRef<ReturnType<typeof createDeliveryRetryAttempt> | undefined>(undefined)
  const lock = useRef(false)
  async function retry() {
    if (lock.current || (!attempt.current && !canRetryDelivery(coupon))) return
    lock.current = true; setBusy(true); onBusy(true); setError(undefined)
    try {
      attempt.current ??= createDeliveryRetryAttempt(coupon.id, api.retryDelivery)
      await attempt.current.send()
      setCompleted(true); setConfirming(false); setUncertain(false); onUncertain(false)
      setConfirmedAfterRetry(Boolean(await onRefresh()))
    } catch (error) {
      setError(error)
      const unknown = !(error instanceof ApiError) || error.status >= 500
      setUncertain(unknown); onUncertain(unknown)
      if (!unknown) {
        attempt.current = undefined; setConfirming(false)
        if (error instanceof ApiError && error.status === 409) await onRefresh()
      }
    } finally { lock.current = false; setBusy(false); onBusy(false) }
  }
  if (!canRetryDelivery(coupon) && !uncertain && !completed && error === undefined) return null
  return <section className="delivery-retry" aria-label="발급 문자 재시도">
    {completed ? <p role="status">문자 재시도 요청이 접수되었습니다. 위의 최신 전달 상태를 확인해 주세요.</p> : <>
      <p>기존 쿠폰의 원래 번호와 발급 당시 문안으로 다시 보냅니다. 월 문자 한도를 추가로 사용합니다.</p>
      {uncertain ? <p role="status">재시도가 이미 접수되었을 수 있습니다. 같은 요청으로 결과를 다시 확인해 주세요. 새 쿠폰을 발급하지 마세요.</p> : confirming ? <p>문자를 다시 보내시겠습니까?</p> : null}
      {uncertain || confirming ? <div className="retry-actions">{!uncertain && <button type="button" disabled={busy} onClick={() => setConfirming(false)}>취소</button>}<button type="button" className="manage-primary" disabled={busy} onClick={retry}>{busy ? '접수 중…' : uncertain ? '같은 재시도 요청 확인' : '다시 보내기 접수'}</button></div> : canRetryDelivery(coupon) && <button type="button" onClick={() => { setError(undefined); setConfirming(true) }}>문자 다시 보내기</button>}
    </>}
    {completed && !confirmedAfterRetry && <button type="button" onClick={async () => setConfirmedAfterRetry(Boolean(await onRefresh()))}>재시도 결과 확인</button>}
    {completed && confirmedAfterRetry && canRetryDelivery(coupon) && <button type="button" onClick={() => { attempt.current = undefined; setCompleted(false); setConfirmedAfterRetry(false); setConfirming(true) }}>새 재시도 요청 준비</button>}
    {error !== undefined && <ErrorNotice error={error} />}
  </section>
}
