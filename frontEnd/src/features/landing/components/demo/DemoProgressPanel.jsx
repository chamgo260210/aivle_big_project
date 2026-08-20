const uploadTasks = ['Idea Brief 정리', '사업안 후보 생성', '법률 검토 연결', '최종 사업안 확정'];
const phaseTasks = {
  structuring: ['시장 분석', '비즈니스 모델', '컨셉 다듬기', '최종 컨셉 확정'],
  reviewing: ['기술·운영 입력 확인', '기술·운영 분석', '재무 추정', '출시 준비 결과 연결'],
  personas: ['가상 응답자 구성', '응답 주제 분류', '언급 횟수 집계', '개선 의견 정리'],
  integrating: ['마케팅 전략 생성', '채널·KPI 정리', '콘텐츠·광고 이미지 연결', '사업기획서 PDF·DOCX 준비'],
};

export default function DemoProgressPanel({ state, progress, sample }) {
  const tasks = state === 'uploading' ? uploadTasks : phaseTasks[state];
  const title = state === 'uploading' ? '사업 기획 결과를 준비하고 있습니다' : state === 'structuring' ? '사업 검증을 진행하고 있습니다' : state === 'reviewing' ? '출시 준비 항목을 분석하고 있습니다' : state === 'personas' ? '가상 시장 인터뷰를 정리하고 있습니다' : '마케팅과 최종 사업기획서를 연결하고 있습니다';
  const overall = state === 'uploading' ? progress : state === 'structuring' ? 36 + Math.round(progress * .12) : state === 'reviewing' ? 48 + Math.round(progress * .12) : state === 'personas' ? 66 + Math.round(progress * .14) : 84 + Math.round(progress * .16);
  return <section className="demo-progress-panel" aria-live="polite"><p>전체 진행률 {overall}%</p><h3>{title}</h3><span>{sample.fileName}</span><div className="demo-overall-progress" role="progressbar" aria-label="데모 처리 진행률" aria-valuemin="0" aria-valuemax="100" aria-valuenow={overall}><i style={{ width: `${overall}%` }} /></div><strong>현재 단계 진행률 {progress}%</strong><ul>{tasks.map((task, index) => { const done = progress >= (index + 1) * (100 / tasks.length); const working = !done && progress >= index * (100 / tasks.length); return <li className={done ? 'is-done' : working ? 'is-working' : ''} key={task}>{done ? '✓' : working ? '…' : '○'} {task}{done && ' 완료'}{working && ' 중'}</li>; })}</ul>{state === 'structuring' && progress >= 45 && <div className="demo-keyword-report"><b>가상 사업 검증 요약</b><p>{sample.overview}</p><span>{sample.keywords.map((item) => <i key={item}>{item}</i>)}</span></div>}{state === 'reviewing' && progress >= 45 && <div className="demo-keyword-report"><b>가상 출시 준비 요약</b><span>{['기술 구성', '운영 체계', '개인정보', '수익·비용', '현금흐름'].map((item) => <i key={item}>{item}</i>)}</span></div>}</section>;
}
