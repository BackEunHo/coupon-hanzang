import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Coupon } from '../../api/types'
import { api } from '../../api'
import { ApiError } from '../../api/client.mjs'
import { ErrorNotice } from '../shared'
import { canRedeemCoupon, createRedemptionAttempt } from './redemption-state.mjs'

type Props = {
  coupon: Coupon; disabled?: boolean
  onRedeemed: (coupon: Coupon) => void
  onRefresh: () => Promise<Coupon | undefined> | undefined
  onBusy: (busy: boolean) => void; onUncertain: (uncertain: boolean) => void
}
export function RedeemCoupon({ coupon, disabled = false, onRedeemed, onRefresh, onBusy, onUncertain }: Props) {
  const [code, setCode] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [validation, setValidation] = useState('')
  const [error, setError] = useState<unknown>()
  const input = useRef<HTMLInputElement>(null)
  const lock = useRef(false)
  const attempt = useRef<ReturnType<typeof createRedemptionAttempt> | undefined>(undefined)
  useEffect(() => {
    if (!busy && error instanceof ApiError && error.code === 'COUPON_CODE_INVALID') input.current?.focus()
  }, [busy, error])
  function confirm(event: FormEvent) {
    event.preventDefault()
    if (lock.current || uncertain || disabled) return
    if (!/^[0-9]{6}$/.test(code)) { setValidation('사용 코드 숫자 6자리를 입력해 주세요.'); input.current?.focus(); return }
    setValidation(''); setError(undefined); setConfirming(true)
  }
  async function redeem() {
    if (lock.current || disabled || (!attempt.current && !canRedeemCoupon(coupon))) return
    lock.current = true; setBusy(true); onBusy(true); setError(undefined)
    try {
      attempt.current ??= createRedemptionAttempt(coupon.id, code, api.redeemCoupon)
      const result = await attempt.current.send()
      attempt.current = undefined; setCode(''); setConfirming(false); setUncertain(false); onUncertain(false)
      onRedeemed(result)
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
  if (!canRedeemCoupon(coupon) && !uncertain) {
    const reason = coupon.status === 'REDEEMED' ? '사용 완료된 쿠폰입니다.'
      : coupon.customerDataDeleted ? '고객 정보가 삭제되어 사용할 수 없습니다.'
        : Date.parse(coupon.startsAt) > Date.now() ? '아직 사용 기간이 시작되지 않았습니다.'
          : Date.parse(coupon.expiresAt) <= Date.now() ? '사용 기간이 종료되었습니다.'
            : '문자 전달이 완료된 사용 가능 쿠폰만 처리할 수 있습니다.'
    return <p className="manage-hint" role="status">{reason}</p>
  }
  return <section className="coupon-redemption" aria-label="쿠폰 사용 처리">
    <h3>쿠폰 사용 처리</h3><p>고객에게 실제 혜택을 적용한 뒤 문자에 있는 사용 코드로 처리해 주세요.</p>
    <form onSubmit={confirm} noValidate>
      <label htmlFor="coupon-use-code">사용 코드</label>
      <input ref={input} id="coupon-use-code" type="text" inputMode="numeric" autoComplete="off" maxLength={6}
        value={code} disabled={busy || disabled || uncertain || confirming} aria-invalid={Boolean(validation) || undefined}
        aria-describedby={validation ? 'coupon-code-error' : undefined}
        onChange={event => { setCode(event.target.value); setValidation(''); setError(undefined) }} />
      {validation && <p id="coupon-code-error" className="manage-error" role="alert">{validation}</p>}
      {uncertain ? <p role="status">사용 처리가 완료되었을 수 있습니다. 같은 요청으로 결과를 확인해 주세요.</p>
        : confirming ? <p role="status">이 쿠폰을 사용 완료 처리하시겠습니까? 처리 후 되돌릴 수 없습니다.</p> : null}
      <div className="retry-actions">
        {confirming && !uncertain && <button type="button" disabled={busy} onClick={() => setConfirming(false)}>취소</button>}
        {confirming || uncertain ? <button type="button" className="manage-primary" disabled={busy || disabled} onClick={redeem}>{busy ? '처리 중…' : uncertain ? '같은 요청으로 결과 확인' : '사용 완료 확정'}</button>
          : <button type="submit" className="manage-primary" disabled={disabled}>사용 완료</button>}
      </div>
    </form>
    {error !== undefined && <ErrorNotice error={error} />}
  </section>
}
