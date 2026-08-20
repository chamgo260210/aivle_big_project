import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import LandingPage from '../LandingPage.jsx';
import DemoSimulator from '../components/DemoSimulator.jsx';
import HeroSection from '../components/HeroSection.jsx';
import { resetLandingIntroForTests } from '../hooks/useLandingIntro.js';

function renderLanding() { return render(<MemoryRouter><LandingPage /></MemoryRouter>); }

async function finishAutomaticPhase() {
  await act(async () => { vi.advanceTimersByTime(4800); });
}

describe('LandingPage', () => {
  beforeEach(() => resetLandingIntroForTests());
  afterEach(() => vi.useRealTimers());

  it('renders its primary content, anchors, and auth links', () => {
    renderLanding();
    expect(screen.getByRole('heading', { level: 1, name: /아이디어에서, 결재·공유용 사업기획서까지/ })).toBeInTheDocument();
    ['intro', 'workflow', 'features', 'faq', 'demo'].forEach((id) => expect(document.getElementById(id)).toBeInTheDocument());
    expect(screen.getAllByRole('link', { name: '로그인' })[0]).toHaveAttribute('href', '/auth/login');
    expect(screen.getAllByRole('link', { name: /무료로 시작하기/ })[0]).toHaveAttribute('href', '/auth/signup');
  });

  it('keeps one hero product window while scene content and active menu change', () => {
    renderLanding();
    const frame = document.querySelector('.hero-story .hero-app-window');
    expect(frame).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /2번째 장면/ }));
    expect(document.querySelectorAll('.hero-story .hero-app-window')).toHaveLength(1);
    expect(frame).toHaveTextContent('2단계 사업 검증');
    expect(frame.querySelector('.is-active')).toHaveTextContent('사업 검증');
    fireEvent.click(screen.getByRole('button', { name: /3번째 장면/ }));
    expect(frame).toHaveTextContent('출시 준비·시장 인터뷰');
    expect(frame.querySelector('.is-active')).toHaveTextContent('출시 준비');
    fireEvent.click(screen.getByRole('button', { name: /4번째 장면/ }));
    expect(frame).toHaveTextContent('마케팅·최종 사업기획서');
    expect(frame.querySelector('.is-active')).toHaveTextContent('보고서');
  });

  it('provides the hero top anchor and reduced-motion settled state', () => {
    render(<MemoryRouter><HeroSection reducedMotion onNavigate={vi.fn()} /></MemoryRouter>);
    expect(document.getElementById('top')).toHaveClass('landing-hero', 'is-entered');
  });

  it('uses explicit header action classes and semantic footer group headings', () => {
    renderLanding();
    expect(document.querySelector('.landing-header__login-action')).toHaveAttribute('href', '/auth/login');
    expect(document.querySelector('.landing-header__primary-action')).toHaveAttribute('href', '/auth/signup');
    expect(screen.getByRole('heading', { level: 3, name: '서비스 둘러보기' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: '정책 및 안내' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '서비스 둘러보기' })).not.toBeInTheDocument();
  });

  it('renders the enhanced demo CTA and only runs its attention state without reduced motion', async () => {
    vi.useFakeTimers();
    render(<MemoryRouter><HeroSection introState="completed" reducedMotion={false} onNavigate={vi.fn()} /></MemoryRouter>);
    const cta = screen.getByRole('button', { name: '파이프라인 미리보기' });
    expect(cta).toHaveClass('landing-demo-cta');
    expect(cta.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(30); });
    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(cta).toHaveClass('is-attention');
    const { unmount } = render(<MemoryRouter><HeroSection introState="completed" reducedMotion onNavigate={vi.fn()} /></MemoryRouter>);
    await act(async () => { vi.advanceTimersByTime(1100); });
    expect(document.querySelectorAll('.landing-demo-cta.is-attention')).toHaveLength(1);
    unmount();
  });

  it('shows the brand boot sequence, blocks background interaction, and reveals the hero afterward', async () => {
    vi.useFakeTimers();
    renderLanding();
    const intro = document.querySelector('.landing-validation-intro');
    expect(intro).toHaveTextContent('Venture Verify');
    expect(intro.querySelectorAll('.validation-stream__lane')).toHaveLength(3);
    expect(intro).toHaveTextContent('Idea Brief');
    expect(intro).toHaveClass('phase-entering');
    expect(document.querySelector('.landing-page__content')).toHaveAttribute('inert');
    expect(document.getElementById('top')).not.toHaveClass('is-entered');
    await act(async () => { vi.advanceTimersByTime(400); });
    expect(intro).toHaveClass('phase-streaming');
    await act(async () => { vi.advanceTimersByTime(700); });
    expect(intro).toHaveClass('phase-classifying');
    expect(intro).toHaveTextContent('사업 검증 · 출시');
    expect(intro).toHaveTextContent('가상 예시 데이터');
    await act(async () => { vi.advanceTimersByTime(600); });
    expect(intro).toHaveClass('phase-assembling');
    expect(intro.querySelector('.intro-product-window .hero-app-window')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(600); });
    expect(document.querySelector('.landing-validation-intro')).toHaveClass('phase-collapsing');
    expect(intro.querySelector('.validation-collapse-core')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(500); });
    expect(document.querySelector('.landing-validation-intro')).toHaveClass('phase-unfolding');
    expect(intro.querySelector('.validation-reveal-layer')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(500); });
    expect(document.querySelector('.landing-validation-intro')).toHaveClass('phase-settling');
    expect(document.querySelector('.landing-page__content')).not.toHaveAttribute('inert');
    await act(async () => { vi.advanceTimersByTime(30); });
    expect(document.getElementById('top')).toHaveClass('is-entered');
    await act(async () => { vi.advanceTimersByTime(300); });
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
  });

  it('lets a visitor skip the boot intro and does not restart it for a top-anchor click', async () => {
    vi.useFakeTimers();
    renderLanding();
    fireEvent.click(screen.getByRole('button', { name: '건너뛰기' }));
    await act(async () => { vi.advanceTimersByTime(350); });
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Venture Verify' }));
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
  });

  it('plays on reload and skips the boot intro for internal route state and browser history restoration', () => {
    const { unmount } = render(<MemoryRouter initialEntries={[{ pathname: '/', state: { skipLandingIntro: true, source: 'auth' } }]}><LandingPage /></MemoryRouter>);
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    unmount();
    const original = performance.getEntriesByType;
    resetLandingIntroForTests();
    performance.getEntriesByType = vi.fn(() => [{ type: 'reload' }]);
    const reloaded = renderLanding();
    expect(document.querySelector('.landing-validation-intro')).toBeInTheDocument();
    reloaded.unmount();
    resetLandingIntroForTests();
    performance.getEntriesByType = vi.fn(() => [{ type: 'back_forward' }]);
    renderLanding();
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    performance.getEntriesByType = original;
  });

  it('shortens the boot intro when reduced motion is requested', async () => {
    vi.useFakeTimers();
    const original = window.matchMedia;
    window.matchMedia = vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    renderLanding();
    await act(async () => { vi.advanceTimersByTime(600); });
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    window.matchMedia = original;
  });

  it('cleans boot intro timers when the landing page unmounts', () => {
    vi.useFakeTimers();
    const clearTimeout = vi.spyOn(window, 'clearTimeout');
    const { unmount } = renderLanding();
    unmount();
    expect(clearTimeout).toHaveBeenCalled();
  });

  it('opens and closes an FAQ answer', () => {
    renderLanding();
    const button = screen.getByRole('button', { name: '어떤 입력으로 시작하나요?' });
    expect(button).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(button); expect(button).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(button); expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('exposes and closes the mobile navigation with Escape', () => {
    renderLanding();
    const menu = document.querySelector('.landing-menu-button');
    expect(menu).toHaveAttribute('aria-controls', 'landing-navigation');
    fireEvent.click(menu); expect(menu).toHaveAttribute('aria-expanded', 'true');
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(menu).toHaveAttribute('aria-expanded', 'false');
  });

  it('updates the header state from an IntersectionObserver entry', () => {
    const original = window.IntersectionObserver;
    const observers = [];
    class Observer {
      constructor(callback) { this.callback = callback; this.targets = []; observers.push(this); }
      observe(target) { this.targets.push(target); }
      disconnect() {}
    }
    window.IntersectionObserver = Observer;
    renderLanding();
    const observer = observers.find((item) => item.targets.some((target) => target.id === 'intro'));
    act(() => observer.callback([{ target: document.getElementById('demo'), isIntersecting: true, intersectionRatio: 1 }]));
    expect(screen.getAllByRole('button', { name: '미리보기' })[0]).toHaveAttribute('aria-current', 'true');
    window.IntersectionObserver = original;
  });

  it('changes the workflow slide from keyboard input', () => {
    renderLanding();
    const workflow = document.querySelector('.workflow-desktop');
    fireEvent.keyDown(workflow, { key: 'ArrowDown' });
    expect(screen.getByRole('button', { name: '02' })).toHaveAttribute('aria-current', 'step');
  });

  it('uses a fixed morph stage with only the current and incoming workflow slides', () => {
    renderLanding();
    expect(document.querySelector('.workflow-copy-track')).not.toBeInTheDocument();
    expect(document.querySelector('.workflow-preview-frame')).toBeInTheDocument();
    expect(document.querySelectorAll('.workflow-copy-stack .workflow-slide')).toHaveLength(1);
  });

  it('opens the complete policy documents and restores the page after closing', () => {
    renderLanding();
    fireEvent.click(screen.getByRole('button', { name: '이용 안내' }));
    expect(screen.getByRole('dialog', { name: '이용 안내' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '1. 서비스의 목적과 범위' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '6. 법률·재무·마케팅 결과의 한계' })).toBeInTheDocument();
    expect(document.querySelector('.policy-dialog__body')).toBeInTheDocument();
    expect(document.body).toHaveStyle({ overflow: 'hidden' });
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.body).not.toHaveStyle({ overflow: 'hidden' });

    fireEvent.click(screen.getByRole('button', { name: '개인정보처리방침' }));
    expect(screen.getByRole('heading', { name: '2. 처리하는 개인정보 항목' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '6. 외부 AI 제공자와 처리 위탁' })).toBeInTheDocument();
    expect(screen.getByText(/개인정보분쟁조정위원회: 1833-6972/)).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'AI 결과 이용 안내' }));
    expect(screen.getByRole('dialog', { name: 'AI 결과 이용 안내' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '1. 생성형 AI 사용 사실' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '6. 가상 시장 인터뷰에 대한 고지' })).toBeInTheDocument();
    expect(screen.getByText(/고객 70%가 가격을 거부함/)).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('requires approvals and selections before completing the interactive demo', async () => {
    vi.useFakeTimers(); renderLanding();
    const sample = screen.getByRole('button', { name: /반려동물_건강관리_아이디어.docx/ });
    fireEvent.click(sample);
    expect(screen.getByRole('button', { name: '이 아이디어로 데모 시작' })).toBeInTheDocument();
    expect(screen.queryByText('사업 기획 결과를 준비하고 있습니다')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '이 아이디어로 데모 시작' }));
    expect(screen.getByText('사업 기획 결과를 준비하고 있습니다')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: '데모 처리 진행률' })).toHaveAttribute('aria-valuenow', '0');
    await finishAutomaticPhase();
    expect(screen.getByText('사업 기획 준비가 완료되었습니다')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(3000); });
    expect(screen.getByText('사업 기획 준비가 완료되었습니다')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '사업 검증 살펴보기' }));
    await finishAutomaticPhase();
    expect(screen.getByText(/시장 분석 · 비즈니스 모델 · 최종 컨셉 연결/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /검증 결과 예시/ }));
    expect(screen.getByText(/시장 분석 근거 8개/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '출시 준비 살펴보기' }));
    await finishAutomaticPhase();
    expect(screen.getByText('출시 준비 결과')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox', { name: /초기 현금흐름/ }));
    fireEvent.click(screen.getByRole('button', { name: '시장 인터뷰 살펴보기' }));
    await finishAutomaticPhase();
    expect(screen.getByRole('button', { name: '마케팅·최종 보고서 만들기' })).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: /디지털 서비스 적극 이용자/ }));
    fireEvent.click(screen.getByRole('button', { name: '마케팅·최종 보고서 만들기' }));
    await finishAutomaticPhase();
    expect(screen.getByText('가상 6단계 프로젝트 결과')).toBeInTheDocument();
    expect(screen.getByText(/선택한 출시 보완 항목:.*초기 현금흐름/)).toBeInTheDocument();
    expect(screen.getByText('디지털 서비스 적극 이용자')).toBeInTheDocument();
    expect(screen.getByText('마케팅·실행 연결')).toBeInTheDocument();
    expect(screen.getByText('가상 인터뷰 질문 예시')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '내 아이디어로 시작하기' })).toHaveAttribute('href', '/auth/signup');
    fireEvent.click(screen.getByRole('button', { name: '다른 샘플 체험하기' }));
    expect(screen.getByRole('button', { name: /반려동물_건강관리_아이디어.docx/ })).toBeInTheDocument();
  }, 10000);

  it('cleans the active demo timer when the simulator unmounts', () => {
    vi.useFakeTimers();
    const clearInterval = vi.spyOn(window, 'clearInterval');
    const { unmount } = render(<DemoSimulator reducedMotion={false} />);
    fireEvent.click(screen.getByRole('button', { name: /반려동물_건강관리_아이디어.docx/ }));
    fireEvent.click(screen.getByRole('button', { name: '이 아이디어로 데모 시작' }));
    unmount();
    expect(clearInterval).toHaveBeenCalled();
  });
});
