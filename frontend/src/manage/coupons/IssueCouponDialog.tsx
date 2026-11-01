import { useRef, useState, type FormEvent } from 'react'
import { api } from '../../api'
import { ApiError } from '../../api/client.mjs'
import type { Coupon, CouponFields as Fields, Template } from '../../api/types'
import { CouponFields, CouponPreview } from '../CouponFields'
import { createIssueAttempt, issueBody, validPhone, validateFields } from '../domain.mjs'
import { Dialog, ErrorNotice } from '../shared'
import { TemplateList } from '../templates/TemplateList'
import { TemplateForm } from '../templates/TemplateForm'

export function IssueCouponDialog({ onClose, onIssued }: { onClose: () => void; onIssued: (coupon: Coupon) => void }) {
  const [template, setTemplate] = useState<Template>()
  const [fields, setFields] = useState<Fields>()
  const [creating, setCreating] = useState(false)
  const [phone, setPhone] = useState('')
  const [consent, setConsent] = useState(false)
  const [review, setReview] = useState(false)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [error, setError] = useState<unknown>()
  const [validation, setValidation] = useState('')
  const attempt = useRef<ReturnType<typeof createIssueAttempt<Coupon>> | undefined>(undefined)
  const lock = useRef(false)
  function select(value: Template) {
    setTemplate(value); setFields({ title: value.title, benefit: value.benefit, notice: value.notice, expiry: value.expiry }); setCreating(false)
  }
  function close() {
    if (uncertain && !window.confirm('발급 결과를 아직 확인하지 못했습니다. 닫은 뒤에는 새로 발급하기 전에 목록에서 기존 발급 여부를 확인해 주세요. 닫을까요?')) return
    onClose()
  }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!template || !fields || lock.current) return
    const message = validateFields(fields) || (!validPhone(phone) ? '올바른 010 휴대전화 번호를 입력해 주세요.' : !consent ? '고객의 쿠폰 수령 의사와 문자 수신 여부를 확인해 주세요.' : '')
    if (message) { setValidation(message); return }
    setValidation('')
    if (!review) { setReview(true); return }
    lock.current = true; setBusy(true); setError(undefined)
    try {
      attempt.current ??= createIssueAttempt(issueBody(template.id, template, fields, phone), api.issue)
      onIssued(await attempt.current.send())
    }
    catch (error) {
      setError(error)
      const unknownResult = !(error instanceof ApiError) || error.status >= 500
      setUncertain(unknownResult)
      if (!unknownResult) { attempt.current = undefined; setReview(false) }
    } finally { lock.current = false; setBusy(false) }
  }
  if (creating) return <TemplateForm onClose={() => setCreating(false)} onSaved={select} />
  return <Dialog title={!template ? '발급할 템플릿 선택' : review ? '발급 내용 최종 확인' : '쿠폰 발급'} onClose={close} locked={busy}>
    {!template || !fields ? <><p className="manage-hint">저장한 혜택을 선택하거나 새 템플릿을 만들어 주세요.</p><TemplateList select={select} onCreate={() => setCreating(true)} /></> : <form onSubmit={submit} noValidate>
      <p className="manage-hint">이번에 수정한 내용은 이 쿠폰에만 적용됩니다. 저장된 템플릿은 바뀌지 않습니다.</p>
      <div className="coupon-editor"><div>{!review ? <CouponFields value={fields} onChange={setFields} disabled={busy} /> : <p className="review-instruction">쿠폰 미리보기와 고객 번호를 확인해 주세요.</p>}
      <label className="recipient-field">고객 휴대전화 번호<input type="tel" autoComplete="off" value={phone} disabled={busy || review} onChange={e => setPhone(e.target.value)} placeholder="010-0000-0000" required /></label>
      <label className="consent-field"><input type="checkbox" checked={consent} disabled={busy || review} onChange={e => setConsent(e.target.checked)} /><span>고객의 쿠폰 수령 의사와 문자 메시지 수신 여부를 확인했습니다.</span></label></div><CouponPreview value={fields} /></div>
      <p className="manage-scope">현재는 발급 접수만 지원하며 실제 문자 발송은 아직 지원하지 않습니다.</p>
      {validation && <p role="alert" className="manage-error">{validation}</p>}{error !== undefined && <ErrorNotice error={error} />}
      {uncertain && <p className="manage-hint" role="status">서버가 접수했을 수 있습니다. 아래 버튼은 같은 요청으로 재확인하며 쿠폰을 중복 생성하지 않습니다. 내용을 바꾸려면 먼저 발급 목록을 확인해 주세요.</p>}
      <div className="dialog-actions">{!uncertain && <button type="button" disabled={busy} onClick={() => { setError(undefined); if (review) setReview(false); else { setTemplate(undefined); setFields(undefined) } }}>{review ? '내용 수정' : '템플릿 다시 선택'}</button>}<button className="manage-primary" disabled={busy}>{busy ? '접수 중…' : uncertain ? '같은 요청 다시 확인' : review ? '발급 요청 접수' : '발급 내용 확인'}</button></div>
    </form>}
  </Dialog>
}
