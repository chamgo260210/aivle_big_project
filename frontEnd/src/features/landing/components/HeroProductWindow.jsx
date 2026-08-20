const menus = ['사업 기획', '사업 검증', '출시 준비', '인터뷰', '마케팅', '보고서'];

function SceneContent({ mode, scene }) {
  if (scene === 0) {
    return <div className="hero-app__upload"><h4>사업 기획 <b>사업안 후보 비교</b></h4><div className="hero-app__file"><strong>아이디어 브리프</strong><span>확정</span></div><p>사업안 생성 <b>{mode === 'intro' ? '준비' : '3개 완료'}</b></p><i className="hero-app__progress"><span /></i><small>{mode === 'intro' ? '아이디어 정보를 연결하고 있습니다' : '✓ 법률 검토 완료 · 최종 사업안 선택 가능'}</small></div>;
  }
  if (scene === 1) {
    return <div className="hero-app__structure"><div><h4>2단계 사업 검증</h4>{[['시장 분석', '완료'], ['비즈니스 모델', '완료'], ['컨셉 다듬기', '확정'], ['최종 컨셉', '확정됨']].map(([name, state]) => <p key={name}><span>{name}</span><b>{state}</b></p>)}</div><aside><span>시장 근거 <b>8개</b></span><span>BM 요소 <b>9개</b></span><span>반영 제안 <b>4개</b></span></aside><small>다음 단계 <b>3단계 출시 준비</b></small></div>;
  }
  if (scene === 2) {
    return <div className="hero-app__review"><h4>출시 준비·시장 인터뷰</h4><div className="hero-app__review-grid"><section>{[['기술·운영 분석', '완료'], ['재무 분석', '완료'], ['출시 준비도', '완료'], ['시장 인터뷰', '완료']].map(([name, state]) => <p key={name}><span>{name}</span><b>{state}</b></p>)}</section><section><strong>반복 언급 주제</strong><p>가격 부담 <b>7명</b></p><p>전문성 신뢰 <b>5명</b></p><p>간편한 기록 <b>4명</b></p></section></div><small>가상 응답자의 발언 횟수이며 실제 구매 의향이 아닙니다.</small></div>;
  }
  return <div className="hero-app__summary"><h4>마케팅·최종 사업기획서</h4><div>{[['마케팅 전략', '완료'], ['채널별 콘텐츠', '생성'], ['광고 배너', '저장'], ['사업기획서', '버전 2']].map(([name, value]) => <span key={name}>{name}<b>{value}</b></span>)}</div><ol><li>의사결정 요약과 승인 요청사항</li><li>시장·BM·재무·인터뷰 요약</li><li>핵심 위험과 후속 조치</li></ol><button type="button">PDF·DOCX 저장</button></div>;
}

export default function HeroProductWindow({ mode = 'hero', scene }) {
  const activeMenu = ['사업 기획', '사업 검증', '출시 준비', '보고서'][scene];
  const stage = ['1단계 · 사업 기획', '2단계 · 사업 검증', '3–4단계 · 출시 준비와 인터뷰', '5–6단계 · 전략과 보고서'][scene];
  return <div className={`hero-app-window hero-app-window--${mode}`} aria-hidden="true"><header><span /><span /><span /><strong>Venture Verify</strong><em>6단계 프로젝트 · 진행 중</em></header><div className="hero-app-window__body"><nav>{menus.map((menu) => <span key={menu} className={menu === activeMenu ? 'is-active' : ''}>{menu}</span>)}</nav><main><div className="hero-app-window__stage">{stage}</div><div className="hero-app-window__content" key={scene}><SceneContent mode={mode} scene={scene} /></div><small>예시 프로젝트의 가상 데이터입니다.</small></main></div></div>;
}
