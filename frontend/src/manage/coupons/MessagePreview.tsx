import type { MessagePreviewResponse } from '../../api/types'
import { ErrorNotice } from '../shared'

export function MessagePreview({ data, loading, error, onRefresh, unavailable = false }: { data?: MessagePreviewResponse; loading: boolean; error?: unknown; onRefresh: () => void; unavailable?: boolean }) {
  return <section className="message-inspection" aria-label="고객에게 전송될 문자" aria-busy={loading}>
    <div className="message-inspection-heading"><h3>고객에게 전송될 문자</h3><button type="button" disabled={loading || unavailable} onClick={onRefresh}>다시 확인</button></div>
    {unavailable ? <p className="manage-hint">쿠폰명·혜택·유효 기간을 올바르게 입력하면 문자 내용을 확인할 수 있습니다.</p> : loading ? <p role="status">문자 내용과 길이를 확인하는 중…</p> : error !== undefined ? <><p>미리보기를 확인할 수 없습니다.</p><ErrorNotice error={error} /></> : data && <>
      <p className="message-inspection-size">{data.byteLength == null ? '바이트 수를 계산할 수 없습니다' : `${data.byteLength.toLocaleString()}바이트 / 최대 2,000바이트`}{data.expectedMessageType && <strong> · {data.expectedMessageType} 예상</strong>}</p>
      <pre className="message-inspection-text">{data.previewText}</pre>
      {!data.sendable && <div role="alert" className="manage-error">{data.validationErrors.length ? data.validationErrors.map(code => <p key={code}>{code === 'MESSAGE_TOO_LONG' ? '최종 문자 내용이 2,000바이트를 초과합니다. 혜택이나 안내문을 줄여 주세요.' : code === 'UNSUPPORTED_MESSAGE_CHARACTERS' ? '지원하지 않는 문자나 이모지가 있습니다. 내용을 수정해 주세요.' : '이 내용으로는 발급할 수 없습니다. 문자 내용을 확인해 주세요.'}</p>) : <p>이 내용으로는 발급할 수 없습니다. 문자 내용을 확인해 주세요.</p>}</div>}
      <p className="manage-hint">사용 코드는 미리보기에서 가려집니다. 실제 기간과 발급 가능 여부는 접수 시 다시 확인합니다.</p>
    </>}
  </section>
}
