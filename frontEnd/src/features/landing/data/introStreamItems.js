export const introStreamLanes = [
  [{ type: 'file', label: 'Idea Brief', meta: '사업 기획' }, { type: 'tag', label: '사업안 후보' }, { type: 'tag', label: '법률 검토', absorb: true }, { type: 'tag', label: '최종 사업안' }],
  [{ type: 'tag', label: '시장 분석', absorb: true }, { type: 'tag', label: '비즈니스 모델' }, { type: 'tag', label: '컨셉 다듬기', absorb: true }, { type: 'tag', label: '출시 준비' }],
  [{ type: 'tag', label: '시장 인터뷰' }, { type: 'tag', label: '마케팅 전략', absorb: true }, { type: 'tag', label: '광고 콘텐츠' }, { type: 'tag', label: '최종 사업기획서', absorb: true }],
];

export const introClassifications = [
  { id: 'planning', label: '사업 기획', value: '사업안 · 법률 검토' },
  { id: 'validation', label: '사업 검증 · 출시', value: '시장 · BM · 기술 · 재무' },
  { id: 'execution', label: '실행 자료', value: '인터뷰 · 마케팅 · 보고서' },
];
