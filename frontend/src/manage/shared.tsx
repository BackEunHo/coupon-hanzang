import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { ApiError, errorText } from '../api/client.mjs'
import type { Page } from '../api/types'

export function ErrorNotice({ error }: { error: unknown }) {
  return <div className="manage-error" role="alert"><p>{errorText(error)}</p>{error instanceof ApiError && error.requestId && <small>문의 번호: {error.requestId}</small>}</div>
}
export function Dialog({ title, children, onClose, locked = false }: { title: string; children: ReactNode; onClose: () => void; locked?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current!
    dialog.showModal()
    return () => { dialog.close(); previous?.focus() }
  }, [])
  return <dialog ref={ref} className="manage-dialog" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!locked) onClose() }}>
    <header><h2 id={titleId}>{title}</h2><button type="button" className="manage-icon-button" aria-label="닫기" disabled={locked} onClick={onClose}>×</button></header>
    <div className="dialog-content">{children}</div>
  </dialog>
}
export function Pagination({ page, onPage }: { page: Page<unknown>['page']; onPage: (page: number) => void }) {
  if (!page.totalElements) return null
  return <nav className="manage-pagination" aria-label="페이지 이동"><span>총 {page.totalElements.toLocaleString()}개 · {page.page + 1} / {page.totalPages}페이지</span><div><button disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>이전</button><button disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>다음</button></div></nav>
}
export function useResource<T>(load: (signal: AbortSignal) => Promise<T>, dependencies: readonly unknown[]) {
  const [state, setState] = useState<{ data?: T; error?: unknown; loading: boolean }>({ loading: true })
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setState({ loading: true })
    load(controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ loading: false, data })
    }).catch(error => {
      if (!controller.signal.aborted) setState({ loading: false, error })
    })
    return () => controller.abort()
    // Callers supply every value used by their load function.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencies, revision])
  return { ...state, refresh: () => setRevision(value => value + 1) }
}
