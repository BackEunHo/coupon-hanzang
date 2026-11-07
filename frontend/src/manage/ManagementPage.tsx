import { useEffect, useState } from 'react'
import { signOut } from '../auth/actions'
import type { Coupon } from '../api/types'
import { CouponList } from './coupons/CouponList'
import { CouponDetail } from './coupons/CouponDetail'
import { IssueCouponDialog } from './coupons/IssueCouponDialog'
import { TemplateList } from './templates/TemplateList'
import { Dialog, ErrorNotice } from './shared'
import './management.css'

export function ManagementPage() {
  const [tab, setTab] = useState<'coupons' | 'templates'>('coupons')
  const [issuing, setIssuing] = useState(false)
  const [selected, setSelected] = useState<{ coupon: Coupon; issued?: boolean }>()
  const [revision, setRevision] = useState(0)
  const [intro, setIntro] = useState(false)
  const [error, setError] = useState<unknown>()
  const [loggingOut, setLoggingOut] = useState(false)
  useEffect(() => { setIntro(!localStorage.getItem('couponhanjang-management-intro-v1')) }, [])
  async function logout() {
    setLoggingOut(true); setError(undefined)
    try { await signOut(); window.location.replace('/login/') }
    catch (error) { setError(error); setLoggingOut(false) }
  }
  return <div className="merchant-workspace"><header className="management-header"><div className="management-header-inner"><a href="/" className="wordmark"><span className="brand-symbol" aria-hidden="true"><span /></span><span>쿠폰한장</span></a><span className="management-label">가게 관리</span><div className="management-header-actions"><a href="/pricing/">요금 안내</a><button disabled={loggingOut} onClick={logout}>{loggingOut ? '로그아웃 중…' : '로그아웃'}</button></div></div></header><main className="management-main"><div className="management-title"><div><p>우리 가게의 다음 방문을 위해</p><h1>쿠폰 관리</h1></div><button className="manage-primary issue-button" onClick={() => setIssuing(true)}><span aria-hidden="true">＋</span> 쿠폰 발급</button></div><div className="manage-scope"><strong>발급 접수·조회 기능을 먼저 이용할 수 있습니다.</strong><span>문자 상태에서 전달 결과를 확인하세요. 쿠폰 사용 처리는 준비 중입니다.</span></div>{error !== undefined && <ErrorNotice error={error} />}<nav className="management-tabs" aria-label="쿠폰 관리 메뉴"><button aria-current={tab === 'coupons' ? 'page' : undefined} onClick={() => setTab('coupons')}>발급 쿠폰</button><button aria-current={tab === 'templates' ? 'page' : undefined} onClick={() => setTab('templates')}>템플릿 관리</button></nav><div className="management-panel">{tab === 'coupons' ? <CouponList revision={revision} onSelect={coupon => setSelected({ coupon })} /> : <><div className="template-heading"><h2>자주 쓰는 쿠폰 혜택</h2><p>템플릿을 수정해도 이미 발급한 쿠폰은 그대로 유지됩니다.</p></div><TemplateList /></>}</div><footer className="management-footer">쿠폰한장 · 작은 가게를 위한 쿠폰 관리</footer></main>
    {intro && <Dialog title="우리 가게 쿠폰 관리를 시작하세요" onClose={() => { localStorage.setItem('couponhanjang-management-intro-v1', 'seen'); setIntro(false) }}><p>자주 쓰는 혜택을 템플릿으로 만들고, 고객의 휴대전화 번호로 쿠폰을 발급·조회할 수 있습니다.</p><p><strong>발급 전 고객의 쿠폰 수령 의사와 문자 메시지 수신 여부를 확인해 주세요.</strong> 발급할 때마다 확인 항목에 체크해야 합니다.</p><p className="manage-scope">발급 접수 후 전달 결과를 확인해 주세요. 문자 길이에 따라 SMS 또는 LMS로 발송됩니다.</p><div className="dialog-actions"><button className="manage-primary" onClick={() => { localStorage.setItem('couponhanjang-management-intro-v1', 'seen'); setIntro(false) }}>확인하고 시작하기</button></div></Dialog>}
    {issuing && <IssueCouponDialog onClose={() => setIssuing(false)} onIssued={coupon => { setIssuing(false); setRevision(value => value + 1); setSelected({ coupon, issued: true }) }} />}
    {selected && <CouponDetail key={selected.coupon.id} coupon={selected.coupon} issued={selected.issued} onClose={() => { setSelected(undefined); setTab('coupons'); setRevision(value => value + 1) }} />}
  </div>
}
