import { useEffect, useRef, useState } from 'react';

import { footerPolicies, footerPolicyByLabel } from '../data/footerPolicies.js';
import { navItems } from '../data/landingData.js';

export default function LandingFooter({ onNavigate }) {
  const [policyLabel, setPolicyLabel] = useState(null);
  const closeRef = useRef();
  const policyButtonRefs = useRef({});
  const policy = policyLabel ? footerPolicyByLabel[policyLabel] : null;

  const openPolicy = (label) => setPolicyLabel(label);
  const closePolicy = () => {
    const label = policyLabel;
    setPolicyLabel(null);
    window.requestAnimationFrame(() => policyButtonRefs.current[label]?.focus());
  };

  useEffect(() => {
    if (!policy) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();
    return () => { document.body.style.overflow = previousOverflow; };
  }, [policy]);

  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === 'Escape' && policyLabel) closePolicy(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  });

  return (
    <footer className="landing-footer">
      <div className="landing-container">
        <div className="landing-footer__brand"><strong>Venture Verify</strong><p>AI 기반 사업 아이디어 검토·의사결정 지원 플랫폼</p></div>
        <div className="landing-footer__groups">
          <nav aria-labelledby="footer-service-heading"><h3 id="footer-service-heading">서비스 둘러보기</h3>{navItems.map(([id, label]) => <button type="button" onClick={() => onNavigate(id)} key={id}>{label}</button>)}</nav>
          <nav aria-labelledby="footer-policy-heading"><h3 id="footer-policy-heading">정책 및 안내</h3>{footerPolicies.map((item) => <button ref={(node) => { policyButtonRefs.current[item.label] = node; }} type="button" onClick={() => openPolicy(item.label)} key={item.id}>{item.label}</button>)}</nav>
        </div>
        <small>본 서비스의 AI 분석 결과는 법률·재무·투자 자문을 대체하지 않습니다. · © 2026 Venture Verify</small>
      </div>
      {policy && (
        <div className="policy-dialog-backdrop" role="presentation" onMouseDown={closePolicy}>
          <section className="policy-dialog" role="dialog" aria-modal="true" aria-labelledby="policy-title" aria-describedby="policy-summary" onMouseDown={(event) => event.stopPropagation()}>
            <header className="policy-dialog__header">
              <button ref={closeRef} className="policy-dialog__close" type="button" aria-label="안내 닫기" onClick={closePolicy}>×</button>
              <p className="landing-eyebrow">{policy.eyebrow}</p>
              <h2 id="policy-title">{policy.label}</h2>
              <p className="policy-dialog__meta">시행일 및 최종 수정일 · {policy.effectiveDate}</p>
            </header>
            <div className="policy-dialog__body">
              <p id="policy-summary" className="policy-dialog__summary">{policy.summary}</p>
              {policy.sections.map((section) => (
                <section className="policy-dialog__section" key={section.title}>
                  <h3>{section.title}</h3>
                  {section.paragraphs?.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
                  {section.bullets && <ul>{section.bullets.map((bullet) => <li key={bullet}>{bullet}</li>)}</ul>}
                  {section.note && <p className="policy-dialog__note"><strong>확인해 주세요</strong>{section.note}</p>}
                </section>
              ))}
            </div>
            <footer className="policy-dialog__footer">
              <span>{policy.label} · {policy.effectiveDate} 시행</span>
              <button type="button" className="landing-button landing-button--small" onClick={closePolicy}>닫기</button>
            </footer>
          </section>
        </div>
      )}
    </footer>
  );
}
