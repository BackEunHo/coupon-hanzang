import { useRef, useState, type FormEvent } from 'react'
import { api } from '../../api'
import type { Template } from '../../api/types'
import { CouponFields, CouponPreview, emptyFields } from '../CouponFields'
import { changedFields, validateFields } from '../domain.mjs'
import { Dialog, ErrorNotice } from '../shared'

export function TemplateForm({ template, onClose, onSaved }: { template?: Template; onClose: () => void; onSaved: (template: Template) => void }) {
  const [fields, setFields] = useState(template ? { title: template.title, benefit: template.benefit, notice: template.notice, expiry: template.expiry } : emptyFields)
  const [error, setError] = useState<unknown>()
  const [validation, setValidation] = useState('')
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  const changed = !template || Object.keys(changedFields(template, fields)).length > 0
  async function submit(event: FormEvent) {
    event.preventDefault()
    const message = validateFields(fields)
    if (message) { setValidation(message); return }
    if (lock.current || !changed) return
    lock.current = true; setBusy(true); setError(undefined); setValidation('')
    try { onSaved(await api.saveTemplate(template ? changedFields(template, fields) : fields, template?.id)) }
    catch (error) { setError(error) }
    finally { lock.current = false; setBusy(false) }
  }
  return <Dialog title={template ? '템플릿 수정' : '새 템플릿 만들기'} onClose={onClose} locked={busy}>
    <p className="manage-hint">{template ? '수정한 내용은 앞으로 발급할 쿠폰에 적용됩니다. 이미 발급한 쿠폰은 바뀌지 않습니다.' : '자주 쓰는 혜택을 저장해 두고 손님에게 빠르게 발급하세요.'}</p>
    <form onSubmit={submit} noValidate><div className="coupon-editor"><CouponFields value={fields} onChange={setFields} disabled={busy} /><CouponPreview value={fields} /></div>{validation && <p role="alert" className="manage-error">{validation}</p>}{error !== undefined && <ErrorNotice error={error} />}<div className="dialog-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button className="manage-primary" disabled={busy || !changed}>{busy ? '저장 중…' : '템플릿 저장'}</button></div></form>
  </Dialog>
}
