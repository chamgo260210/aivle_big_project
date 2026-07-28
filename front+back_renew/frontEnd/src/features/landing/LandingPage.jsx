import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import DemoSimulator from './components/DemoSimulator.jsx';
import HeroSection from './components/HeroSection.jsx';
import LandingBootIntro from './components/LandingBootIntro.jsx';
import LandingFooter from './components/LandingFooter.jsx';
import LandingHeader from './components/LandingHeader.jsx';
import WorkflowSection from './components/WorkflowSection.jsx';
import { faqItems, featureItems, navItems } from './data/landingData.js';
import useReducedMotion from './hooks/useReducedMotion.js';
import useLandingIntro from './hooks/useLandingIntro.js';
import useSectionScrollProgress from './hooks/useSectionScrollProgress.js';
import useScrollSpy from './hooks/useScrollSpy.js';
import './landing.css';
import './intro.css';
import './validationIntro.css';
import './validationTransition.css';

function scrollToSection(id, reducedMotion, focus = false) {
  document.getElementById(id)?.scrollIntoView?.({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'start' });
  window.history.replaceState(null, '', `#${id}`);
  if (focus) window.requestAnimationFrame(() => document.getElementById(`${id}-title`)?.focus({ preventScroll: true }));
}

function IntroSection() {
  const problems = [['문서는 있지만 핵심 정보가 흩어져 있습니다', '시장, 고객, 수익 구조, 운영 계획이 여러 페이지에 나뉘어 있어 빠르게 검토하기 어렵습니다.'], ['사실과 가정이 구분되지 않습니다', '근거가 확인된 내용과 아직 검증이 필요한 주장이 섞이면 의사결정의 신뢰도가 낮아집니다.'], ['다음 행동이 보이지 않습니다', '문제를 발견해도 무엇을 보완하고 누구에게 검증해야 하는지 연결되지 않는 경우가 많습니다.']];
  return <section id="intro" className="landing-section landing-intro" aria-labelledby="intro-title"><div className="landing-container"><p className="landing-eyebrow">계획서를 작성한 뒤가 더 어렵습니다</p><h2 id="intro-title">아이디어는 있지만,<br />무엇부터 검증해야 할지는 불명확합니다.</h2><p className="landing-section__lede">초기 사업계획에는 확인된 사실, 아직 검증되지 않은 가정, 누락된 정보가 함께 섞여 있습니다. 이 상태에서는 법률 위험이나 시장 가정, 실행 조건을 놓치기 쉽습니다.</p><div className="problem-grid">{problems.map(([title, description], index) => <article key={title}><span>0{index + 1}</span><h3>{title}</h3><p>{description}</p></article>)}</div><p className="landing-resolution">이 플랫폼은 문서를 단순히 요약하지 않습니다. <strong>현재 상태, 근거, 위험과 다음 행동</strong>을 하나의 검증 흐름으로 연결합니다.</p></div></section>;
}

function FeatureSection() { return <section id="features" className="landing-section landing-features" aria-labelledby="features-title"><div className="landing-container"><p className="landing-eyebrow">CORE FEATURES</p><h2 id="features-title">검증에 필요한 정보를,<br />한 프로젝트 안에서 관리하세요.</h2><div className="feature-grid">{featureItems.map(([title, description, size], index) => <div className="feature-card-motion" key={title}><article className={`feature-card ${size}`}><span className="feature-card__number">0{index + 1}</span><h3>{title}</h3><p>{description}</p>{index === 0 && <div className="feature-visual feature-visual--document"><span>Before<br /><b>25페이지 사업계획서</b></span><i aria-hidden="true">→</i><span>After<br /><b>핵심 검토 항목</b></span></div>}{index === 1 && <div className="feature-visual"><b>근거 있음</b><b>추가 확인 필요</b><b>판단 보류</b></div>}</article></div>)}</div></div></section>; }

function TrustAndOutcome() { const before = ['25페이지 사업계획서', '시장 근거와 가정 혼재', '누락 항목 확인 어려움', '검토 결과가 문서별로 분산', '무엇을 먼저 검증할지 불명확']; const after = ['핵심 항목 구조화', '근거·가정·추론 구분', '법률·사업성 위험 정리', '고객 검증 과제 도출', '프로젝트 진행 상태 통합']; return <><section className="landing-trust" aria-labelledby="trust-title"><div className="landing-container"><p className="landing-eyebrow">OUR PRINCIPLES</p><h2 id="trust-title">그럴듯한 답보다,<br />확인 가능한 근거를 우선합니다.</h2><div className="trust-grid">{[['현재 상태', '완료되지 않은 분석을 완료된 결과처럼 보여주지 않습니다.'], ['근거 추적', '분석 결과가 어떤 입력과 자료를 바탕으로 만들어졌는지 함께 확인할 수 있도록 구성합니다.'], ['다음 행동', '점수만 보여주는 대신, 무엇을 보완하고 무엇을 검증해야 하는지 제안합니다.']].map(([title, text]) => <article key={title}><h3>{title}</h3><p>{text}</p></article>)}</div><p className="trust-disclaimer">AI 분석 결과는 법률·재무·투자 또는 기타 전문가의 최종 판단을 대체하지 않습니다.</p></div></section><section className="landing-section landing-outcome" aria-labelledby="outcome-title"><div className="landing-container"><h2 id="outcome-title">사업계획서가,<br />의사결정 자료가 되기까지</h2><div className="outcome-grid"><article><p>BEFORE</p><ul>{before.map((item) => <li key={item}>{item}</li>)}</ul></article><span aria-hidden="true">→</span><article className="is-after"><p>AFTER</p><ul>{after.map((item) => <li key={item}>{item}</li>)}</ul></article></div><p className="landing-resolution">문서를 더 길게 만드는 것이 아니라, <strong>의사결정에 필요한 정보를 더 선명하게</strong> 만듭니다.</p></div></section></>; }

function FaqSection() { const [open, setOpen] = useState(null); return <section id="faq" className="landing-section landing-faq" aria-labelledby="faq-title"><div className="landing-container landing-container--narrow"><p className="landing-eyebrow">FAQ</p><h2 id="faq-title">자주 묻는 질문</h2><div className="faq-list">{faqItems.map(([question, answer], index) => { const expanded = open === index; return <article key={question}><h3><button type="button" aria-expanded={expanded} aria-controls={`faq-panel-${index}`} onClick={() => setOpen(expanded ? null : index)}>{question}<span aria-hidden="true">{expanded ? '−' : '+'}</span></button></h3><div id={`faq-panel-${index}`} className={expanded ? 'is-open' : ''} aria-hidden={!expanded}><p>{answer}</p></div></article>; })}</div></div></section>; }

function DemoSection({ reducedMotion }) { return <section id="demo" className="landing-section landing-demo" aria-labelledby="demo-title"><div className="landing-container"><p className="landing-eyebrow">INTERACTIVE DEMO</p><h2 id="demo-title" tabIndex="-1">설명보다 빠르게,<br />서비스 흐름을 직접 확인하세요.</h2><p className="landing-section__lede">샘플 사업계획서가 구조화되고 검토 결과로 이어지는 과정을 가상 데모로 체험할 수 있습니다.</p><DemoSimulator reducedMotion={reducedMotion} /><p className="demo-disclaimer">이 데모는 제품 흐름을 설명하기 위한 시뮬레이션입니다. 실제 파일이 업로드되거나 AI 분석이 실행되지 않습니다.</p></div></section>; }

function FinalCta({ reducedMotion }) { return <section className="landing-final-cta" aria-labelledby="cta-title"><div className="landing-container"><h2 id="cta-title">사업 아이디어를<br />검증 가능한 프로젝트로 전환하세요.</h2><p>문서 등록부터 구조화, 위험 검토와 고객 검증 계획까지 하나의 흐름에서 시작할 수 있습니다.</p><div className="landing-actions"><Link className="landing-button" to="/auth/signup" state={{ authTransition: true, source: 'landing', intent: 'signup' }}>무료로 프로젝트 시작하기</Link><Link className="landing-button landing-button--ghost" to="/auth/login" state={{ authTransition: true, source: 'landing', intent: 'login' }}>로그인</Link></div><button className="visually-hidden" type="button" onClick={() => scrollToSection('top', reducedMotion)}>맨 위로</button></div></section>; }

export default function LandingPage() {
  const location = useLocation();
  const routerNavigate = useNavigate();
  const ids = useMemo(() => navItems.map(([id]) => id), []);
  const activeId = useScrollSpy(ids);
  const reducedMotion = useReducedMotion();
  const skipFromInternalRoute = location.state?.skipLandingIntro === true;
  const intro = useLandingIntro(reducedMotion, { skipFromInternalRoute });
  const navigate = useCallback((id, options = {}) => scrollToSection(id, reducedMotion, options.focus), [reducedMotion]);
  useEffect(() => { document.documentElement.classList.toggle('landing-scroll-snap', !reducedMotion); return () => document.documentElement.classList.remove('landing-scroll-snap'); }, [reducedMotion]);
  useEffect(() => {
    if (!skipFromInternalRoute) return;
    const nextState = { ...location.state };
    delete nextState.skipLandingIntro;
    routerNavigate(`${location.pathname}${location.hash}`, { replace: true, state: Object.keys(nextState).length ? nextState : null });
  }, [location.hash, location.pathname, location.state, routerNavigate, skipFromInternalRoute]);
  const introCompleted = intro.complete;
  const interactive = intro.state === 'settling' || introCompleted;
  useSectionScrollProgress({ enabled: interactive, reducedMotion });
  return <div className="landing-page"><LandingBootIntro onSkip={intro.skip} reducedMotion={reducedMotion} state={intro.state} /><div className={`landing-page__content is-${intro.state}`} inert={interactive ? undefined : true}><LandingHeader activeId={activeId} onNavigate={navigate} /><HeroSection introState={intro.state} reducedMotion={reducedMotion} onNavigate={navigate} /><IntroSection /><WorkflowSection onNavigate={navigate} /><FeatureSection /><TrustAndOutcome /><FaqSection /><DemoSection reducedMotion={reducedMotion} /><FinalCta reducedMotion={reducedMotion} /><LandingFooter onNavigate={navigate} /></div></div>;
}
