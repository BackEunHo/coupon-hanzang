import { useEffect, useRef, useState } from 'react'
import { api } from '../../api'
import type { Template } from '../../api/types'
import { expiryText, latestRequest } from '../domain.mjs'
import { Dialog, ErrorNotice, Pagination, useResource } from '../shared'
import { TemplateForm } from './TemplateForm'

export function TemplateList({ select, onCreate, revision = 0 }: { select?: (template: Template) => void; onCreate?: () => void; revision?: number }) {
  const [page, setPage] = useState(0)
  const list = useResource(signal => api.templates(page, signal), [page, revision])
  const [editing, setEditing] = useState<Template | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Template | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [loadingDetail, setLoadingDetail] = useState(false)
  const detailRequest = useRef(latestRequest())
  useEffect(() => () => detailRequest.current.cancel(), [])
  useEffect(() => { if (list.data && page > 0 && !list.data.items.length) setPage(Math.max(0, list.data.page.totalPages - 1)) }, [list.data, page])
  async function choose(template: Template) {
    setLoadingDetail(true); setError(undefined)
    try { await detailRequest.current.run(signal => api.template(template.id, signal), fresh => { if (select) select(fresh); else setEditing(fresh) }) }
    catch (error) { setError(error) }
    finally { setLoadingDetail(false) }
  }
  async function remove() {
    if (!deleting || busy) return
    setBusy(true); setError(undefined)
    try { await api.deleteTemplate(deleting.id); setDeleting(null); list.refresh() }
    catch (error) { setError(error) }
    finally { setBusy(false) }
  }
  return <section aria-label="쿠폰 템플릿" className="template-section">
    {error !== undefined && !deleting && <ErrorNotice error={error} />}
    {list.loading && <p role="status" className="manage-empty">템플릿을 불러오는 중…</p>}
    {list.error !== undefined && <><ErrorNotice error={list.error} /><button onClick={list.refresh}>다시 불러오기</button></>}
    {list.data && <><div className="template-grid"><button className="new-template" onClick={() => { detailRequest.current.cancel(); setLoadingDetail(false); if (onCreate) onCreate(); else setEditing('new') }}><span aria-hidden="true">＋</span><strong>새 템플릿</strong><small>새로운 혜택 만들기</small></button>{list.data.items.map(template => <article className="template-card" key={template.id}><h3>{template.title}</h3><p>{template.benefit}</p><small>{expiryText(template.expiry)}</small><div className="template-actions"><button className={select ? 'manage-primary' : ''} disabled={loadingDetail} onClick={() => choose(template)}>{select ? '이 템플릿 선택' : '상세·수정'}</button>{!select && <button className="manage-danger-text" onClick={() => { setError(undefined); setDeleting(template) }}>삭제</button>}</div></article>)}</div>{!list.data.items.length && <p className="manage-empty">아직 템플릿이 없습니다. 첫 쿠폰 혜택을 만들어 보세요.</p>}<Pagination page={list.data.page} onPage={setPage} /></>}
    {editing && <TemplateForm key={editing === 'new' ? 'new' : editing.id} template={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); list.refresh() }} />}
    {deleting && <Dialog title="템플릿을 삭제할까요?" locked={busy} onClose={() => setDeleting(null)}><p><strong>{deleting.title}</strong> 템플릿은 앞으로 선택할 수 없습니다. 이미 발급한 쿠폰은 유지됩니다.</p>{error !== undefined && <ErrorNotice error={error} />}<div className="dialog-actions"><button disabled={busy} onClick={() => setDeleting(null)}>취소</button><button className="manage-danger" disabled={busy} onClick={remove}>{busy ? '삭제 중…' : '삭제'}</button></div></Dialog>}
  </section>
}
