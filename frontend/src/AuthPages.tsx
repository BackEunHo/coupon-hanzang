import { useState, type FormEvent } from 'react'
import { authDestination, plans, selectedPlan, validateSignup } from './flow.mjs'

const signedInKey = 'couponhanjang-signed-in-until'
const planQuery = () => {
  const plan = selectedPlan(window.location.search)
  return plan ? `?plan=${plan}` : ''
}
function recentlySignedIn() {
  return Number(sessionStorage.getItem(signedInKey)) > Date.now()
}
export function planLink(plan: string) {
  return recentlySignedIn() ? `/pricing/confirm/?plan=${plan}` : `/login/?plan=${plan}`
}
function Shell({ children }: { children: React.ReactNode }) {
  return <div className="auth-shell"><header className="auth-header"><a className="wordmark" href="/"><span className="brand-symbol" aria-hidden="true"><span /></span><span>쿠폰한장</span></a><a href="/pricing/">요금 안내</a></header><main className="auth-main">{children}</main></div>
}
export function AuthPage({ mode }: { mode: 'login' | 'signup' }) {
  const signup = mode === 'signup'
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validation = signup ? validateSignup(name, phone, password, confirmation) : (!phone || !password ? '휴대전화 번호와 비밀번호를 입력해 주세요.' : '')
    if (validation) { setError(validation); return }
    setBusy(true); setError('')
    try {
      const base = import.meta.env.VITE_API_BASE_URL || ''
      const response = await fetch(`${base}/api/v1/auth/${mode}`, {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(signup ? { merchantName: name.trim(), phone, password } : { phone, password }),
      })
      if (!response.ok) {
        if (response.status === 409 && signup) setError('이미 가입된 번호입니다. 로그인해 주세요.')
        else if (response.status === 401) setError('휴대전화 번호 또는 비밀번호를 확인해 주세요.')
        else if (response.status === 429) setError('로그인 시도가 너무 많습니다. 15분 후 다시 시도해 주세요.')
        else if (response.status === 400) setError('입력 내용을 확인해 주세요.')
        else setError('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
        return
      }
      sessionStorage.setItem(signedInKey, String(Date.now() + 24 * 60 * 60 * 1000))
      window.location.assign(authDestination(window.location.search))
    } catch {
      setError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.')
    } finally { setBusy(false) }
  }
  return <Shell><div className="auth-card"><p className="section-label">쿠폰한장</p><h1>{signup ? '우리 가게 시작하기' : '다시 만나서 반가워요'}</h1><p className="auth-intro">{signup ? '가게와 운영자 정보를 입력해 주세요.' : '운영자 휴대전화 번호로 로그인해 주세요.'}</p>
    <form onSubmit={submit} noValidate>
      {signup && <label>가게 이름<input value={name} onChange={e => setName(e.target.value)} autoComplete="organization" maxLength={100} required /></label>}
      <label>휴대전화 번호<input value={phone} onChange={e => setPhone(e.target.value)} autoComplete="tel" type="tel" placeholder="010-0000-0000" required /></label>
      <label>비밀번호<input value={password} onChange={e => setPassword(e.target.value)} type="password" autoComplete={signup ? 'new-password' : 'current-password'} required /></label>
      {signup && <><span className="field-hint">8~128자로 입력해 주세요.</span><label>비밀번호 확인<input value={confirmation} onChange={e => setConfirmation(e.target.value)} type="password" autoComplete="new-password" required /></label><p className="field-hint">휴대전화 번호 인증은 추후 제공됩니다.</p></>}
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="auth-submit" type="submit" disabled={busy}>{busy ? '처리 중...' : signup ? '회원가입' : '로그인'}</button>
    </form><p className="auth-switch">{signup ? '이미 계정이 있으신가요?' : '처음이신가요?'} <a href={`${signup ? '/login/' : '/signup/'}${planQuery()}`}>{signup ? '로그인' : '회원가입'}</a></p>
  </div></Shell>
}
export function ConfirmPage() {
  const id = selectedPlan(window.location.search)
  const plan = id ? plans[id] : null
  if (!plan) return <Shell><div className="auth-card"><h1>요금제를 확인할 수 없습니다</h1><a href="/pricing/">요금 안내로 돌아가기</a></div></Shell>
  if (!recentlySignedIn()) return <Shell><div className="auth-card"><h1>로그인이 필요합니다</h1><p className="auth-intro">선택한 요금제를 확인하려면 로그인해 주세요.</p><a className="auth-submit auth-action" href={`/login/?plan=${id}`}>로그인</a></div></Shell>
  return <Shell><div className="auth-card"><p className="section-label">요금제 확인</p><h1>{plan.name}</h1><p className="plan-price">{plan.price}</p><p>{plan.description}</p><div className="plan-notice"><strong>임시 가격입니다</strong><p>문자 발송 조건은 미정이며, 요금제 신청과 결제는 준비 중입니다. 이 화면에서 요금제가 적용되거나 결제되지 않습니다.</p></div><a className="text-link" href="/pricing/">다른 요금제 보기 →</a></div></Shell>
}
export function WelcomePage() {
  return <Shell><div className="auth-card"><p className="section-label">쿠폰한장</p><h1>환영합니다</h1><p className="auth-intro">로그인했습니다. 서비스 이용과 요금제 신청은 준비 중입니다.</p><a className="auth-submit auth-action" href="/pricing/">요금 안내 보기</a></div></Shell>
}
