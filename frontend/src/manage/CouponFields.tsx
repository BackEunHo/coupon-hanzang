import { useId } from 'react'
import type { CouponFields as Fields } from '../api/types'
import { expiryText } from './domain.mjs'

export const emptyFields = (): Fields => ({ title: '', benefit: '', notice: '', expiry: { mode: 'RELATIVE_DAYS', durationDays: 14 } })
export function CouponFields({ value, onChange, disabled = false }: { value: Fields; onChange: (value: Fields) => void; disabled?: boolean }) {
  const id = useId()
  return <fieldset className="coupon-fields" disabled={disabled}>
    <label htmlFor={`${id}-title`}>쿠폰명 <span>필수 · 최대 100자</span></label><input id={`${id}-title`} value={value.title} maxLength={100} required onChange={e => onChange({ ...value, title: e.target.value })} placeholder="예: 다음 방문 커피 할인" />
    <label htmlFor={`${id}-benefit`}>혜택 내용 <span>필수 · 최대 500자</span></label><textarea id={`${id}-benefit`} value={value.benefit} maxLength={500} required rows={3} onChange={e => onChange({ ...value, benefit: e.target.value })} placeholder="예: 아메리카노 1잔 1,000원 할인" />
    <label htmlFor={`${id}-expiry`}>유효 기간</label><select id={`${id}-expiry`} value={value.expiry.mode} onChange={e => onChange({ ...value, expiry: e.target.value === 'RELATIVE_DAYS' ? { mode: 'RELATIVE_DAYS', durationDays: 14 } : { mode: 'FIXED_DATE', startDate: '', date: '' } })}><option value="RELATIVE_DAYS">발급일 기준</option><option value="FIXED_DATE">날짜 직접 지정</option></select>
    {value.expiry.mode === 'RELATIVE_DAYS' ? <label className="duration-field">발급일을 포함한 일수<input type="number" min={1} step={1} required value={value.expiry.durationDays || ''} onChange={e => onChange({ ...value, expiry: { mode: 'RELATIVE_DAYS', durationDays: Number(e.target.value) } })} /></label> : <div className="date-fields"><label>시작일<input type="date" min="0001-01-01" max="9999-12-30" required value={value.expiry.startDate} onChange={e => onChange({ ...value, expiry: { ...value.expiry, mode: 'FIXED_DATE', startDate: e.target.value } as Fields['expiry'] })} /></label><label>종료일<input type="date" min={value.expiry.startDate || '0001-01-01'} max="9999-12-30" required value={value.expiry.date} onChange={e => onChange({ ...value, expiry: { ...value.expiry, mode: 'FIXED_DATE', date: e.target.value } as Fields['expiry'] })} /></label></div>}
    <p className="manage-hint">한국 시간 기준이며 종료일까지 유효합니다.</p>
    <label htmlFor={`${id}-notice`}>안내문 <span>선택 · 최대 2,000자</span></label><textarea id={`${id}-notice`} value={value.notice} maxLength={2000} rows={3} onChange={e => onChange({ ...value, notice: e.target.value })} placeholder="사용 조건이나 방문 안내를 적어 주세요." />
  </fieldset>
}
export function CouponPreview({ value }: { value: Fields }) {
  return <aside className="manage-preview"><span className="preview-eyebrow">쿠폰 미리보기</span><h3>{value.title || '쿠폰 이름'}</h3><p className="preview-benefit">{value.benefit || '손님에게 드릴 혜택을 입력해 주세요.'}</p><p className="preview-period">{expiryText(value.expiry)}</p><p className="preview-notice">{value.notice || '추가 안내문이 없습니다.'}</p><span className="preview-footer">쿠폰한장</span></aside>
}
