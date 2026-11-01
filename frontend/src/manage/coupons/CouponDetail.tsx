import { api } from '../../api'
import type { Coupon } from '../../api/types'
import { couponStatuses, messageStatuses, periodText, timeText } from '../domain.mjs'
import { Dialog, ErrorNotice, useResource } from '../shared'

export function CouponDetail({ coupon, issued = false, onClose }: { coupon: Coupon; issued?: boolean; onClose: () => void }) {
  const detail = useResource(signal => api.coupon(coupon.id, signal), [coupon.id])
  const current = detail.data || coupon
  return <Dialog title={issued ? '발급 요청이 접수되었습니다' : '쿠폰 상세'} onClose={onClose}>
    {issued && <p className="manage-scope">쿠폰 정보를 저장했습니다. 실제 문자 발송은 아직 지원하지 않습니다.</p>}
    {detail.loading && <p role="status">최신 상태를 확인하는 중…</p>}{detail.error !== undefined && <ErrorNotice error={detail.error} />}
    <div className="coupon-detail"><h3>{current.title}</h3><p className="detail-benefit">{current.benefit}</p><dl><dt>고객 번호</dt><dd>{current.customerDataDeleted ? '010-****-****' : current.displayPhone}{current.customerDataDeleted && <small> · 고객 정보 삭제됨</small>}</dd><dt>쿠폰 상태</dt><dd><span className={`coupon-status status-${current.status}`}>{couponStatuses[current.status]}</span></dd><dt>발급 문자 상태</dt><dd>{messageStatuses[current.issueMessageStatus]}</dd><dt>유효 기간</dt><dd>{periodText(current.startsAt, current.expiresAt)}<small>한국 시간 · 종료일까지 유효</small></dd><dt>발급 접수일</dt><dd>{timeText(current.issuedAt)}</dd>{current.redeemedAt && <><dt>사용 완료일</dt><dd>{timeText(current.redeemedAt)}</dd></>}<dt>안내문</dt><dd className="preserve-lines">{current.notice || '없음'}</dd></dl><p className="manage-hint">이 내용은 발급 당시 저장된 정보입니다. 템플릿을 변경하거나 삭제해도 유지됩니다.</p></div>
    <div className="dialog-actions"><button disabled={detail.loading} onClick={detail.refresh}>상태 새로고침</button><button className="manage-primary" onClick={onClose}>목록으로</button></div>
  </Dialog>
}
