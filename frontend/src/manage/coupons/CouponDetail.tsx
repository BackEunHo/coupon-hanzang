import { useState } from 'react'
import type { Coupon } from '../../api/types'
import { couponStatuses, messageStatuses, periodText, timeText } from '../domain.mjs'
import { Dialog, ErrorNotice } from '../shared'
import { useCouponDelivery } from './useCouponDelivery'
import { DeliveryRetry } from './DeliveryRetry'

export function CouponDetail({ coupon, issued = false, onClose }: { coupon: Coupon; issued?: boolean; onClose: () => void }) {
  const detail = useCouponDelivery(coupon)
  const current = detail.data
  const [retryBusy, setRetryBusy] = useState(false)
  const [retryUncertain, setRetryUncertain] = useState(false)
  function close() {
    if (retryBusy) return
    if (retryUncertain && !window.confirm('문자 재시도 결과를 아직 확인하지 못했습니다. 닫은 뒤에는 새로 보내기 전에 최신 상태를 확인해 주세요. 닫을까요?')) return
    onClose()
  }
  return <Dialog title={issued ? '발급 요청이 접수되었습니다' : '쿠폰 상세'} onClose={close} locked={retryBusy}>
    {issued && <p className="manage-scope">발급 요청이 접수되었습니다. 문자 상태에서 전달 결과를 확인해 주세요.</p>}
    {detail.loading && <p role="status">최신 상태를 확인하는 중…</p>}{detail.error !== undefined && <ErrorNotice error={detail.error} />}
    <div className="coupon-detail"><h3>{current.title}</h3><p className="detail-benefit">{current.benefit}</p><dl><dt>고객 번호</dt><dd>{current.customerDataDeleted ? '010-****-****' : current.displayPhone}{current.customerDataDeleted && <small> · 고객 정보 삭제됨</small>}</dd><dt>쿠폰 상태</dt><dd><span className={`coupon-status status-${current.status}`}>{couponStatuses[current.status]}</span></dd><dt>발급 문자 상태</dt><dd>{messageStatuses[current.issueMessageStatus]}</dd><dt>유효 기간</dt><dd>{periodText(current.startsAt, current.expiresAt)}<small>한국 시간 · 종료일까지 유효</small></dd><dt>발급 접수일</dt><dd>{timeText(current.issuedAt)}</dd>{current.redeemedAt && <><dt>사용 완료일</dt><dd>{timeText(current.redeemedAt)}</dd></>}<dt>안내문</dt><dd className="preserve-lines">{current.notice || '없음'}</dd></dl><p className="manage-hint">이 내용은 발급 당시 저장된 정보입니다. 템플릿을 변경하거나 삭제해도 유지됩니다.</p></div>
    <dl className="delivery-metadata"><dt>예상 문자 유형</dt><dd>{current.expectedMessageType || '정보 없음'}</dd><dt>발급 당시 문자 길이</dt><dd>{current.messageByteLength == null ? '정보 없음' : `${current.messageByteLength.toLocaleString()}바이트`}</dd><dt>실제 문자 유형</dt><dd>{current.actualMessageType || (current.expectedMessageType ? '확인 중' : '정보 없음')}</dd></dl>
    {current.issueMessageStatus === 'UNKNOWN' && <p role="status" className="manage-scope">전송 결과 확인 중입니다. 중복 발송 방지를 위해 다시 보내기는 할 수 없습니다.</p>}
    {current.issueMessageStatus === 'DELIVERED' && <p className="manage-hint">전달 완료는 고객이 메시지를 읽었다는 의미는 아닙니다.</p>}
    {detail.autoStopped && detail.error === undefined && <p className="manage-hint" role="status">자동 상태 확인을 마쳤습니다. 최신 결과는 새로고침으로 확인해 주세요.</p>}
    <DeliveryRetry coupon={current} onRefresh={detail.refresh} onBusy={setRetryBusy} onUncertain={setRetryUncertain} />
    <div className="dialog-actions"><button disabled={detail.loading || retryBusy} onClick={detail.refresh}>상태 새로고침</button><button className="manage-primary" disabled={retryBusy} onClick={close}>목록으로</button></div>
  </Dialog>
}
