import { personas, risks } from '../../data/demoExperienceData.js';

export function UploadReview({ sample, onStructure, onReset }) {
  return <section className="demo-review"><p className="demo-review__eyebrow">사업 기획 준비가 완료되었습니다</p><h3>{sample.title}</h3><dl><div><dt>연결한 아이디어 자료</dt><dd>{sample.fileName} · {sample.pages}</dd></div><div><dt>확인할 결과</dt><dd>Idea Brief, 사업안 후보, 법률 검토, 최종 사업안</dd></div></dl><div className="demo-action-row"><button className="landing-button" type="button" onClick={onStructure}>사업 검증 살펴보기</button><button className="landing-text-button" type="button" onClick={onReset}>다른 아이디어 선택</button></div></section>;
}

export function StructureReview({ showGaps, onToggleGaps, onReview }) {
  return <section className="demo-review"><p className="demo-review__eyebrow">사업 검증이 완료되었습니다</p><h3>시장 분석 · 비즈니스 모델 · 최종 컨셉 연결</h3><p>확정된 사업안을 기준으로 시장과 비즈니스 모델을 분석하고, 컨셉 다듬기를 거쳐 최종 컨셉을 확정합니다.</p><button className="demo-disclosure" type="button" aria-expanded={showGaps} onClick={onToggleGaps}>검증 결과 예시 <span>{showGaps ? '−' : '+'}</span></button>{showGaps && <ul className="demo-gap-list"><li>시장 분석 근거 8개를 현재 컨셉에 연결했습니다.</li><li>비즈니스 모델 핵심 요소 9개를 구체화했습니다.</li></ul>}<div className="demo-action-row"><button className="landing-button" type="button" onClick={onReview}>출시 준비 살펴보기</button></div></section>;
}

export function RiskSelection({ selected, onToggle, onPersonas }) {
  return <section className="demo-review"><p className="demo-review__eyebrow">출시 준비 결과</p><h3>기술·운영 분석과 재무 추정이 연결되었습니다</h3><p>데모 결과에서 이어서 확인할 보완 항목을 선택해 보세요.</p><div className="demo-selection-list">{risks.map((risk) => <label key={risk.id}><input type="checkbox" checked={selected.includes(risk.id)} onChange={() => onToggle(risk.id)} /><span><b>{risk.title}</b><small>{risk.status} · {risk.detail}</small></span></label>)}</div><div className="demo-action-row"><button className="landing-button" type="button" onClick={onPersonas}>시장 인터뷰 살펴보기</button></div></section>;
}

export function PersonaSelection({ selected, onToggle, onIntegrate }) {
  const canContinue = selected.length > 0;
  return <section className="demo-review"><p className="demo-review__eyebrow">가상 시장 인터뷰 결과</p><h3>비교할 가상 응답자 관점을 선택하세요</h3><p>이 데모는 응답의 언급 주제를 보여줍니다. 실제 조사나 구매 의향 통계가 아닙니다.</p><div className="demo-persona-options">{personas.map((persona) => <label className={selected.includes(persona.id) ? 'is-selected' : ''} key={persona.id}><input type="checkbox" checked={selected.includes(persona.id)} onChange={() => onToggle(persona.id)} /><span><b>{persona.title}</b><small>{persona.meta}</small><em>{persona.traits.join(' · ')}</em></span></label>)}</div>{!canContinue && <p className="demo-selection-help" role="status">최소 1개의 가상 응답자 관점을 선택해 주세요.</p>}<div className="demo-action-row"><button className="landing-button" type="button" disabled={!canContinue} onClick={onIntegrate}>마케팅·최종 보고서 만들기</button></div></section>;
}
