const previews = {
  planning: {
    title: '사업 기획',
    rows: [['아이디어 브리프', '확정'], ['사업안 후보', '3개'], ['법률 사전 검토', '완료'], ['최종 사업안', '선택됨']],
    note: '아이디어 · 사업안 비교 · 법률 검토',
  },
  validation: {
    title: '사업 검증',
    rows: [['시장 분석', '완료'], ['비즈니스 모델', '완료'], ['컨셉 다듬기', '확정'], ['현재 컨셉', '최종 확정']],
    note: '시장 → BM → 컨셉 다듬기',
  },
  launch: {
    title: '출시 준비',
    rows: [['기술·운영 분석', '완료'], ['재무 추정', '완료'], ['스트레스 시나리오', '확인'], ['출시 준비도', '분석 완료']],
    note: '사용자 입력 확정 · 분석 보고서',
  },
  interview: {
    title: '시장 인터뷰',
    rows: [['가격 부담 언급', '7명'], ['전문성 신뢰 언급', '5명'], ['간편한 기록 언급', '4명'], ['바꾸길 원한 내용', '6건']],
    note: '가상 응답의 발언 횟수 · 백분율 아님',
  },
  marketing: {
    title: '마케팅 전략',
    rows: [['타깃·포지셔닝', '완료'], ['채널 전략·KPI', '완료'], ['채널별 콘텐츠', '생성'], ['광고 배너', '저장 가능']],
    note: '전략 보고서 · 콘텐츠 · 이미지',
  },
  report: {
    title: '최종 사업기획서',
    rows: [['의사결정 요약', '완료'], ['시장·BM·재무', '포함'], ['핵심 위험·후속 조치', '포함'], ['문서 버전', 'v2']],
    note: 'PDF 저장 · DOCX 다운로드',
  },
};

export default function ProductPreview({ kind = 'report', label = '예시 프로젝트 화면' }) {
  const preview = previews[kind] || previews.report;
  return <div className="product-preview" aria-label={label}>
    <div className="product-preview__bar"><span /><span /><span /><strong>{preview.title}</strong></div>
    <div className="product-preview__body">
      {preview.rows.map(([name, state]) => <div className="product-preview__row" key={name}><span>{name}</span><b>{state}</b></div>)}
      <p className="product-preview__note">{preview.note}</p>
      {kind === 'report' && <button type="button" className="product-preview__cta">사업기획서 보기</button>}
    </div>
    <small>예시 프로젝트의 가상 데이터입니다.</small>
  </div>;
}
