import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import DemoSimulator from './components/DemoSimulator.jsx';
import HeroSection from './components/HeroSection.jsx';
import LandingBootIntro from './components/LandingBootIntro.jsx';
import LandingFooter from './components/LandingFooter.jsx';
import LandingHeader from './components/LandingHeader.jsx';
import WorkflowSection from './components/WorkflowSection.jsx';
import { faqItems, featureItems, navItems } from './data/landingData.js';
import useLandingIntro from './hooks/useLandingIntro.js';
import useReducedMotion from './hooks/useReducedMotion.js';
import useScrollSpy from './hooks/useScrollSpy.js';
import useSectionScrollProgress from './hooks/useSectionScrollProgress.js';
import './landing.css';
import './intro.css';
import './validationIntro.css';
import './validationTransition.css';

function scrollToSection(id, reducedMotion, focus = false) {
  document.getElementById(id)?.scrollIntoView?.({
    behavior: reducedMotion ? 'auto' : 'smooth',
    block: 'start',
  });
  window.history.replaceState(null, '', `#${id}`);
  if (focus) {
    window.requestAnimationFrame(() => (
      document.getElementById(`${id}-title`)?.focus({ preventScroll: true })
    ));
  }
}

function IntroSection() {
  const problems = [
    ['하나의 6단계 여정', '사업 기획에서 최종 보고서까지 현재 단계와 다음 작업을 연결합니다.'],
    ['확정된 결과 기반 분석', '사용자가 확정한 사업안과 입력을 기준으로 다음 AI 분석을 실행합니다.'],
    ['분석에서 문서까지', '시장·BM·출시·인터뷰·마케팅 결과를 사업기획서의 한 버전으로 묶습니다.'],
  ];
  return (
    <section id="intro" className="landing-section landing-intro" aria-labelledby="intro-title">
      <div className="landing-container">
        <p className="landing-eyebrow">ONE CONNECTED JOURNEY</p>
        <h2 id="intro-title">사업 기획부터<br />결재·공유용 사업기획서까지.</h2>
        <p className="landing-section__lede">
          사업안을 확정하고 시장·BM·컨셉을 검증한 뒤, 기술·운영·재무 출시 준비, 시장 인터뷰,
          마케팅과 최종 사업기획서까지 진행합니다. 확정과 결과는 프로젝트에 저장됩니다.
        </p>
        <div className="problem-grid">
          {problems.map(([title, description], index) => (
            <article key={title}>
              <span>0{index + 1}</span>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
        <p className="landing-resolution">
          흩어진 기획과 분석을 하나의 흐름으로 연결해, <strong>현재 확정된 사업안과 다음 작업</strong>을 분명하게 만듭니다.
        </p>
      </div>
    </section>
  );
}

function FeatureSection() {
  return (
    <section id="features" className="landing-section landing-features" aria-labelledby="features-title">
      <div className="landing-container">
        <p className="landing-eyebrow">JOURNEY CAPABILITIES</p>
        <h2 id="features-title">실제 제품의 핵심 기능을<br />6개 업무 단계로 연결합니다.</h2>
        <div className="feature-grid">
          {featureItems.map(([title, description, size], index) => (
            <article key={title} className={`feature-card ${size}`}>
              <span className="feature-card__number">0{index + 1}</span>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function TrustAndOutcome() {
  return (
    <>
      <section className="landing-trust" aria-labelledby="trust-title">
        <div className="landing-container">
          <p className="landing-eyebrow">RESPONSIBLE ASSISTANCE</p>
          <h2 id="trust-title">AI의 제안과 사용자의 결정을 구분합니다.</h2>
          <div className="trust-grid">
            <article><h3>확정을 구분합니다</h3><p>AI 제안과 사용자가 확정한 사업안·가설·입력을 구분해 다음 분석의 기준을 명확히 합니다.</p></article>
            <article><h3>현재 유효한 결과를 연결합니다</h3><p>분석을 다시 실행하면 이전 결과와 현재 결과를 구분하고, 최종 문서에는 선택한 버전만 포함합니다.</p></article>
            <article><h3>결과의 한계를 표시합니다</h3><p>법률·재무 분석과 가상 인터뷰가 공식 자문이나 실제 고객 조사로 오해되지 않도록 안내합니다.</p></article>
          </div>
          <p className="trust-disclaimer">AI 결과는 의사결정 지원 자료이며 법률·재무·투자 자문이나 사업 성과를 보장하지 않습니다.</p>
        </div>
      </section>
      <section className="landing-section landing-outcome" aria-labelledby="outcome-title">
        <div className="landing-container">
          <p className="landing-eyebrow">아이디어에서 의사결정까지</p>
          <h2 id="outcome-title">막연한 아이디어를 검토 가능한 의사결정 자료로.</h2>
          <div className="outcome-grid">
            <article>
              <p>시작 전</p>
              <ul><li>정리되지 않은 아이디어와 사업안</li><li>따로 떨어진 시장·재무·운영 가정</li><li>버전과 출처가 불분명한 보고 자료</li></ul>
            </article>
            <span aria-hidden="true">→</span>
            <article className="is-after">
              <p>완료 후</p>
              <ul><li>법률 검토를 거쳐 확정한 사업안</li><li>시장·BM·출시·인터뷰·마케팅 결과</li><li>PDF·DOCX로 저장하는 최종 사업기획서</li></ul>
            </article>
          </div>
        </div>
      </section>
    </>
  );
}

function FaqSection() {
  const [open, setOpen] = useState(null);
  return (
    <section id="faq" className="landing-section landing-faq" aria-labelledby="faq-title">
      <div className="landing-container landing-container--narrow">
        <p className="landing-eyebrow">FAQ</p>
        <h2 id="faq-title">시작하기 전에 확인하세요.</h2>
        <div className="faq-list">
          {faqItems.map(([question, answer], index) => {
            const expanded = open === index;
            const panelId = `landing-faq-panel-${index}`;
            return (
              <article key={question}>
                <h3>
                  <button
                    type="button"
                    aria-controls={panelId}
                    aria-expanded={expanded}
                    onClick={() => setOpen(expanded ? null : index)}
                  >
                    {question}<span aria-hidden="true">{expanded ? '−' : '+'}</span>
                  </button>
                </h3>
                <div id={panelId} className={expanded ? 'is-open' : ''}><p>{answer}</p></div>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function DemoSection({ reducedMotion }) {
  return (
    <section id="demo" className="landing-section landing-demo" aria-labelledby="demo-title">
      <div className="landing-container">
        <p className="landing-eyebrow">JOURNEY PREVIEW</p>
        <h2 id="demo-title" tabIndex="-1">서비스 흐름을 먼저 살펴보세요.</h2>
        <p className="landing-section__lede">
          아래 화면은 현재 6단계 파이프라인을 축약한 가상 예시입니다. 실제 프로젝트에서는 각 단계의
          AI 분석과 사용자 확정, 버전별 결과가 저장됩니다.
        </p>
        <DemoSimulator reducedMotion={reducedMotion} />
        <p className="demo-disclaimer">예시 데이터는 실제 고객 반응, 법률 판단 또는 사업 성과 예측이 아닙니다.</p>
      </div>
    </section>
  );
}

function FinalCta() {
  return (
    <section className="landing-final-cta" aria-labelledby="cta-title">
      <div className="landing-container">
        <h2 id="cta-title">사업 아이디어를<br />실행 검토용 사업기획서로 만드세요.</h2>
        <p>프로젝트를 만들고 6단계를 진행하면, 현재 유효한 분석을 연결한 PDF·DOCX 사업기획서까지 완성할 수 있습니다.</p>
        <div className="landing-actions">
          <Link className="landing-button" to="/auth/signup" state={{ authTransition: true, source: 'landing', intent: 'signup' }}>프로젝트 시작하기</Link>
          <Link className="landing-button landing-button--ghost" to="/auth/login" state={{ authTransition: true, source: 'landing', intent: 'login' }}>로그인</Link>
        </div>
      </div>
    </section>
  );
}

export default function LandingPage() {
  const location = useLocation();
  const routerNavigate = useNavigate();
  const ids = useMemo(() => navItems.map(([id]) => id), []);
  const activeId = useScrollSpy(ids);
  const reducedMotion = useReducedMotion();
  const skipFromInternalRoute = location.state?.skipLandingIntro === true;
  const intro = useLandingIntro(reducedMotion, { skipFromInternalRoute });
  const navigate = useCallback(
    (id, options = {}) => scrollToSection(id, reducedMotion, options.focus),
    [reducedMotion],
  );

  useEffect(() => {
    document.documentElement.classList.toggle('landing-scroll-snap', !reducedMotion);
    return () => document.documentElement.classList.remove('landing-scroll-snap');
  }, [reducedMotion]);

  useEffect(() => {
    if (!skipFromInternalRoute) return;
    const nextState = { ...location.state };
    delete nextState.skipLandingIntro;
    routerNavigate(`${location.pathname}${location.hash}`, {
      replace: true,
      state: Object.keys(nextState).length ? nextState : null,
    });
  }, [location.hash, location.pathname, location.state, routerNavigate, skipFromInternalRoute]);

  const interactive = intro.state === 'settling' || intro.complete;
  useSectionScrollProgress({ enabled: interactive, reducedMotion });

  return (
    <div className="landing-page">
      <LandingBootIntro onSkip={intro.skip} reducedMotion={reducedMotion} state={intro.state} />
      <div className={`landing-page__content is-${intro.state}`} inert={interactive ? undefined : true}>
        <LandingHeader activeId={activeId} onNavigate={navigate} />
        <HeroSection introState={intro.state} reducedMotion={reducedMotion} onNavigate={navigate} />
        <IntroSection />
        <WorkflowSection onNavigate={navigate} />
        <FeatureSection />
        <TrustAndOutcome />
        <FaqSection />
        <DemoSection reducedMotion={reducedMotion} />
        <FinalCta />
        <LandingFooter onNavigate={navigate} />
      </div>
    </div>
  );
}
