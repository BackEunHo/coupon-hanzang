import { useState } from 'react'

const pricingHref = '/pricing/'

function Wordmark() {
  return (
    <a className="wordmark" href="/" aria-label="쿠폰한장 홈">
      <span className="brand-symbol" aria-hidden="true"><span /></span>
      <span>쿠폰한장</span>
    </a>
  )
}

function Arrow({ diagonal = false }: { diagonal?: boolean }) {
  return <span aria-hidden="true" className="arrow">{diagonal ? '↗' : '→'}</span>
}

function Header({ pricing = false }: { pricing?: boolean }) {
  const [open, setOpen] = useState(false)

  return (
    <header className="site-header">
      <div className="header-inner page-width">
        <Wordmark />
        <nav className={`site-nav ${open ? 'is-open' : ''}`} aria-label="주 메뉴">
          <a href={pricing ? '/#how-it-works' : '#how-it-works'} onClick={() => setOpen(false)}>이용 방법</a>
          <a href={pricing ? '/#features' : '#features'} onClick={() => setOpen(false)}>주요 기능</a>
          <a href={pricingHref} onClick={() => setOpen(false)}>요금 안내</a>
        </nav>
        <a className="header-cta" href={pricingHref}>시작하기 <Arrow diagonal /></a>
        <button
          className="menu-toggle"
          type="button"
          aria-expanded={open}
          aria-controls="mobile-menu"
          aria-label={open ? '메뉴 닫기' : '메뉴 열기'}
          onClick={() => setOpen(!open)}
        >
          <span />
          <span />
        </button>
      </div>
      <div id="mobile-menu" className={`mobile-nav ${open ? 'is-open' : ''}`}>
        <a href={pricing ? '/#how-it-works' : '#how-it-works'} onClick={() => setOpen(false)}>이용 방법</a>
        <a href={pricing ? '/#features' : '#features'} onClick={() => setOpen(false)}>주요 기능</a>
        <a href={pricingHref} onClick={() => setOpen(false)}>요금 안내</a>
        <a href={pricingHref} onClick={() => setOpen(false)}>시작하기 <Arrow diagonal /></a>
      </div>
    </header>
  )
}

function PrimaryLink({ children, href = pricingHref, light = false }: { children: React.ReactNode; href?: string; light?: boolean }) {
  return <a className={`primary-link ${light ? 'primary-link-light' : ''}`} href={href}>{children}<Arrow diagonal /></a>
}

function CouponPreview() {
  return (
    <div className="coupon-preview" aria-label="예시 쿠폰">
      <div className="coupon-preview-top">
        <span>쿠폰한장</span>
        <span className="preview-tag">예시 화면</span>
      </div>
      <div className="coupon-preview-body">
        <span className="coupon-preview-caption">다음 방문을 위한 한 장</span>
        <strong>아메리카노<br />1,000원 할인</strong>
        <span className="coupon-preview-detail">방문 시 직원에게 보여주세요.</span>
      </div>
      <div className="coupon-preview-bottom"><span>우리동네 커피</span></div>
    </div>
  )
}

function MessagePreview() {
  return (
    <div className="message-preview" aria-label="고객에게 전송되는 문자 예시">
      <div className="message-top"><span className="message-back">‹</span><span>문자 메시지</span><span aria-hidden="true">···</span></div>
      <div className="message-date">오늘 오전 10:24</div>
      <div className="message-bubble">
        <strong>[쿠폰한장] 쿠폰이 도착했어요</strong>
        <p>우리동네 커피에서<br />아메리카노 1,000원 할인 쿠폰을 보내드렸습니다.</p>
        <p>매장 방문 시 전화번호와 사용 코드를 알려주세요.</p>
        <span>사용 코드 123 · 예시 화면</span>
      </div>
    </div>
  )
}

const faqs = [
  {
    question: '고객도 앱을 설치해야 하나요?',
    answer: '고객은 문자로 쿠폰 발급 안내를 받습니다. 매장에서는 전화번호와 문자에 담긴 사용 코드를 알려주면 운영자가 쿠폰을 확인할 수 있습니다.',
  },
  {
    question: '쿠폰은 어떻게 사용하나요?',
    answer: '운영자가 고객의 전화번호로 쿠폰을 찾고, 문자에 담긴 사용 코드를 확인한 뒤 결제에 혜택을 적용하고 사용 완료 처리합니다.',
  },
  {
    question: '문자 발송과 요금은 어떻게 되나요?',
    answer: '무료, 월 요금제, 월 종량제 구성을 준비하고 있습니다. 각 요금제의 문자 발송 한도와 비용은 확정 후 요금 안내 페이지에서 알려드릴 예정입니다.',
  },
  {
    question: '쿠폰을 발급하기 전에 확인할 것이 있나요?',
    answer: '운영자는 고객에게 쿠폰을 받을 의사와 문자 메시지 수신 여부를 먼저 확인해야 합니다.',
  },
]

function LandingPage() {
  return (
    <>
      <Header />
      <main>
        <section className="hero" aria-labelledby="hero-title">
          <div className="hero-copy page-width">
            <p className="hero-label">작은 가게를 위한 쿠폰 서비스</p>
            <h1 id="hero-title">다음 방문을 위한<br /><span>쿠폰 한 장.</span></h1>
            <p className="hero-description">쿠폰을 만들어 문자로 전하고, 매장에서 사용을 확인하세요.<br className="desktop-only" /> 발급부터 사용 현황까지 한곳에서 관리할 수 있습니다.</p>
            <div className="hero-actions">
              <PrimaryLink>시작하기</PrimaryLink>
              <a className="text-link" href="#how-it-works">이용 방법 보기 <Arrow /></a>
            </div>
          </div>
          <div className="hero-photo-wrap">
            <img src="/cafe-coupon.jpg" alt="카페 직원이 손님에게 빈 쿠폰 카드를 건네는 모습" className="hero-photo" fetchPriority="high" />
            <div className="hero-photo-caption">가게와 손님 사이, 다음 방문을 위한 작은 약속.</div>
          </div>
        </section>

        <section className="statement page-width" aria-labelledby="statement-title">
          <p className="section-label">쿠폰한장이 하는 일</p>
          <h2 id="statement-title">쿠폰 발급부터<br /><span>사용 확인까지.</span></h2>
          <p>리뷰 이벤트 이외에 무엇을 할지 고민된다면, 다음 방문에 사용할 쿠폰을 준비해 보세요. 쿠폰한장은 작은 가게가 쿠폰을 직접 발급하고 사용 현황을 관리할 수 있도록 돕습니다.</p>
        </section>

        <section className="workflow" id="how-it-works" aria-labelledby="workflow-title">
          <div className="page-width">
            <div className="section-heading">
              <p className="section-label">이용 방법</p>
              <h2 id="workflow-title">발급부터 사용까지,<br />흐름은 간단하게.</h2>
            </div>
            <div className="workflow-grid">
              <article className="workflow-item">
                <span className="workflow-line" />
                <span className="workflow-number">1</span>
                <h3>쿠폰을 만드세요</h3>
                <p>혜택과 유효 기간, 안내문을 정하고 자주 쓰는 쿠폰은 템플릿으로 남겨두세요.</p>
              </article>
              <article className="workflow-item">
                <span className="workflow-line" />
                <span className="workflow-number">2</span>
                <h3>문자로 전하세요</h3>
                <p>고객의 수령 의사와 문자 수신 여부를 확인한 뒤, 전화번호로 쿠폰을 발급하세요.</p>
              </article>
              <article className="workflow-item">
                <span className="workflow-line" />
                <span className="workflow-number">3</span>
                <h3>매장에서 확인하세요</h3>
                <p>방문한 고객의 쿠폰과 사용 코드를 확인하고, 혜택을 적용한 뒤 사용 처리하세요.</p>
              </article>
            </div>
          </div>
        </section>

        <section className="showcase page-width" id="features" aria-labelledby="showcase-title">
          <div className="showcase-copy">
            <p className="section-label">한눈에 보는 쿠폰</p>
            <h2 id="showcase-title">가게에서 만든 쿠폰이<br />손님의 문자로.</h2>
            <p>쿠폰 이름과 혜택, 유효 기간, 안내문을 담아 발급하세요. 손님은 문자로 받은 쿠폰을 매장에서 사용합니다.</p>
            <span className="example-note">이해를 돕기 위한 예시 화면입니다.</span>
          </div>
          <div className="showcase-visual">
            <CouponPreview />
            <MessagePreview />
          </div>
        </section>

        <section className="management" aria-labelledby="management-title">
          <div className="page-width management-inner">
            <div className="management-copy">
              <p className="section-label">운영은 한곳에서</p>
              <h2 id="management-title">보낸 뒤에도,<br />놓치지 않도록.</h2>
              <p>발급, 만료, 사용 완료 쿠폰을 구분해 확인하세요. 방문한 손님의 쿠폰은 전화번호로 찾을 수 있습니다.</p>
            </div>
            <div className="dashboard-preview" aria-label="쿠폰 관리 화면 예시">
              <div className="dashboard-header"><span className="dashboard-mark">쿠폰한장</span><span>관리 페이지 · 예시 화면</span></div>
              <div className="dashboard-title"><span>쿠폰 현황</span><span className="dashboard-search">전화번호로 쿠폰 검색 <span aria-hidden="true">⌕</span></span></div>
              <div className="dashboard-tabs"><span className="active">발급 현황</span><span>만료 쿠폰</span><span>사용 완료</span></div>
              <div className="dashboard-row"><strong>아메리카노 1,000원 할인</strong><span>우리동네 커피</span><em>발급됨</em></div>
              <div className="dashboard-row"><strong>다음 방문 쿠폰</strong><span>우리동네 커피</span><em>발급됨</em></div>
              <div className="dashboard-row"><strong>디저트 쿠폰</strong><span>우리동네 커피</span><em>발급됨</em></div>
            </div>
          </div>
        </section>

        <section className="plans page-width" aria-labelledby="plans-title">
          <div className="section-heading plans-heading">
            <p className="section-label">요금 안내</p>
            <h2 id="plans-title">가게의 사용 방식에 맞게.</h2>
            <p>세 가지 요금 방식을 준비하고 있습니다. 구체적인 한도와 비용은 확정 후 안내합니다.</p>
          </div>
          <div className="plan-grid">
            <div className="plan-item"><h3>무료 요금제</h3><p>정해진 월 문자 발송 한도 안에서 시작하는 방식</p></div>
            <div className="plan-item"><h3>월 요금제</h3><p>매월 이용하며 쿠폰 발행 건수 제한 없이 사용하는 방식</p></div>
            <div className="plan-item"><h3>월 종량제</h3><p>무료 요금제 한도 이후 사용량에 따라 이용하는 방식</p></div>
          </div>
          <a className="text-link plans-link" href={pricingHref}>요금 안내 자세히 보기 <Arrow /></a>
        </section>

        <section className="faq" aria-labelledby="faq-title">
          <div className="page-width faq-inner">
            <div><p className="section-label">자주 묻는 질문</p><h2 id="faq-title">궁금한 점을<br />먼저 확인하세요.</h2></div>
            <div className="faq-list">
              {faqs.map(({ question, answer }) => (
                <details key={question}>
                  <summary>{question}<span aria-hidden="true">＋</span></summary>
                  <p>{answer}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        <section className="final-cta" aria-labelledby="final-title">
          <div className="page-width final-cta-inner">
            <div><p>작은 가게의 다음 방문을 위해</p><h2 id="final-title">우리 가게 쿠폰,<br />한 장부터 시작해 보세요.</h2></div>
            <PrimaryLink light>시작하기</PrimaryLink>
          </div>
        </section>
      </main>
      <Footer />
    </>
  )
}

function PricingPage() {
  return (
    <>
      <Header pricing />
      <main className="pricing-page">
        <section className="pricing-hero page-width">
          <p className="section-label">요금 안내</p>
          <h1>우리 가게에 맞는<br />쿠폰 요금제.</h1>
          <p>세 가지 요금 방식을 준비 중입니다. 가격과 문자 발송 조건은 확정 후 안내합니다.</p>
        </section>
        <section className="pricing-content page-width" aria-label="준비 중인 요금제">
          <div className="pricing-plan"><h2>무료 요금제</h2><p>월 문자 발송 한도 안에서 쿠폰을 발급하는 방식입니다.</p><small>월 발송 한도 · 확정 전</small></div>
          <div className="pricing-plan"><h2>월 요금제</h2><p>월 이용료를 내고 쿠폰 발행 건수 제한 없이 사용하는 방식입니다.</p><small>월 이용료·문자 제공 범위 · 확정 전</small></div>
          <div className="pricing-plan"><h2>월 종량제</h2><p>무료 한도를 넘긴 사용량에 따라 요금을 내는 방식입니다.</p><small>사용량당 요금 · 확정 전</small></div>
        </section>
        <div className="pricing-note page-width"><strong>서비스 신청은 준비 중입니다.</strong><p>요금과 신청 절차가 확정되면 이 페이지에서 안내하겠습니다.</p><a className="text-link" href="/">서비스 소개로 돌아가기 <Arrow /></a></div>
      </main>
      <Footer />
    </>
  )
}

function Footer() {
  return <footer className="site-footer"><div className="page-width footer-inner"><Wordmark /><p>작은 가게의 쿠폰 운영을 위한 서비스</p><span>© {new Date().getFullYear()} 쿠폰한장</span></div></footer>
}

export default function App() {
  return window.location.pathname.startsWith('/pricing') ? <PricingPage /> : <LandingPage />
}
