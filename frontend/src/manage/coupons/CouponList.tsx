import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../../api'
import type { Coupon, CouponFilters } from '../../api/types'
import { couponStatuses, messageStatuses, periodText, timeText, validPhone } from '../domain.mjs'
import { ErrorNotice, Pagination, useResource } from '../shared'

const emptyFilters: CouponFilters = { phone: '', title: '', status: '' }
export function CouponList({ onSelect, revision }: { onSelect: (coupon: Coupon) => void; revision: number }) {
  const [input, setInput] = useState(emptyFilters)
  const [filters, setFilters] = useState(emptyFilters)
  const [page, setPage] = useState(0)
  const [validation, setValidation] = useState('')
  const list = useResource(signal => api.coupons(page, filters, signal), [page, filters, revision])
  useEffect(() => { if (list.data && page > 0 && !list.data.items.length) setPage(Math.max(0, list.data.page.totalPages - 1)) }, [list.data, page])
  function search(event: FormEvent) {
    event.preventDefault()
    if (input.phone && !validPhone(input.phone.trim())) { setValidation('검색할 휴대전화 번호 전체를 입력해 주세요.'); return }
    setValidation(''); setPage(0); setFilters({ ...input })
  }
  const hasFilters = Boolean(filters.phone || filters.title || filters.status)
  return <section aria-label="발급 쿠폰 목록"><form className="coupon-search" onSubmit={search} noValidate><label>고객 휴대전화 번호<input type="tel" autoComplete="off" placeholder="010-0000-0000" value={input.phone} onChange={e => setInput({ ...input, phone: e.target.value })} /></label><label>쿠폰명<input placeholder="쿠폰명으로 검색" value={input.title} onChange={e => setInput({ ...input, title: e.target.value })} /></label><label>상태<select value={input.status} onChange={e => setInput({ ...input, status: e.target.value })}><option value="">전체 상태</option>{Object.entries(couponStatuses).map(([key, name]) => <option value={key} key={key}>{name}</option>)}</select></label><button className="manage-primary">검색</button><button type="button" onClick={() => { setInput(emptyFilters); setFilters(emptyFilters); setPage(0); setValidation('') }}>초기화</button></form><p className="manage-hint">전화번호는 전체 번호로 검색합니다. 입력한 검색 조건을 모두 만족하는 쿠폰을 찾습니다.</p>
    {validation && <p className="manage-error" role="alert">{validation}</p>}
    <div className="list-toolbar"><span>발급 접수일 최신순</span><button onClick={list.refresh} disabled={list.loading}>새로고침</button></div>
    {list.loading && <p className="manage-empty" role="status">쿠폰을 불러오는 중…</p>}{list.error !== undefined && <><ErrorNotice error={list.error} /><button onClick={list.refresh}>다시 불러오기</button></>}
    {list.data && <>{list.data.items.length ? <div className="coupon-table-wrap"><table className="coupon-table"><caption className="sr-only">발급한 쿠폰과 고객, 유효 기간 및 상태</caption><thead><tr><th scope="col">쿠폰 / 고객</th><th scope="col">유효 기간</th><th scope="col">상태</th><th scope="col">발급 접수일</th><th scope="col"><span className="sr-only">상세</span></th></tr></thead><tbody>{list.data.items.map(coupon => <tr key={coupon.id}><td><strong>{coupon.title}</strong><span>{coupon.customerDataDeleted ? '010-****-****' : coupon.displayPhone}</span></td><td>{periodText(coupon.startsAt, coupon.expiresAt)}</td><td><span className={`coupon-status status-${coupon.status}`}>{couponStatuses[coupon.status]}</span><small>문자: {messageStatuses[coupon.issueMessageStatus]}</small>{(coupon.expectedMessageType || coupon.actualMessageType) && <small>{coupon.actualMessageType ? `${coupon.actualMessageType} 발송` : `${coupon.expectedMessageType} 예상`}</small>}</td><td>{timeText(coupon.issuedAt)}</td><td><button aria-label={`${coupon.title} 상세 보기`} onClick={() => onSelect(coupon)}>상세</button></td></tr>)}</tbody></table></div> : <div className="manage-empty"><h3>{hasFilters ? '검색 결과가 없습니다' : '아직 발급한 쿠폰이 없습니다'}</h3><p>{hasFilters ? '검색 조건을 바꾸거나 초기화해 주세요.' : '상단의 쿠폰 발급 버튼으로 첫 쿠폰을 접수해 보세요.'}</p></div>}<Pagination page={list.data.page} onPage={setPage} /></>}
  </section>
}
