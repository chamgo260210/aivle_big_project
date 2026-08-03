import { Link, NavLink, Outlet, useLocation, useParams } from 'react-router-dom';

import { Breadcrumb, ErrorState, LoadingState } from '../../shared/ui/index.js';
import { ProjectProvider, useProjectContext } from '../../features/projects/ProjectContext.jsx';
import { appRoutes, projectRoutes } from '../../features/projects/routing/projectRoutes.js';

const JOURNEY_STEPS = [
  ['아이디어', ''], ['법률 검토', 'legal'], ['콘셉트 생성', 'journey/concept'],
  ['콘셉트 분석', 'journey/concept-analysis'], ['콘셉트 선택', 'journey/concept-selection'],
  ['페르소나', 'journey/persona'], ['인터뷰', 'journey/interview'],
  ['마케팅', 'journey/marketing'], ['최종 보고서', 'journey/final-report'],
];

function ProjectLayoutContent() {
  const { projectId } = useParams();
  const location = useLocation();
  const { status, project, retry } = useProjectContext();
  if (status === 'loading') return <LoadingState label="프로젝트를 불러오고 있습니다" />;
  if (status === 'error') return <ErrorState title="프로젝트를 찾을 수 없습니다" description="프로젝트가 없거나 접근 권한이 없습니다." onRetry={retry} />;

  const base = `/app/projects/${projectId}`;
  const relative = location.pathname.slice(base.length).replace(/^\//, '');
  const currentIndex = Math.max(0, JOURNEY_STEPS.findIndex(([, route]) => route === relative));
  const previous = JOURNEY_STEPS[currentIndex - 1];
  const next = JOURNEY_STEPS[currentIndex + 1];
  const stepUrl = (step) => step?.[1] ? `${base}/${step[1]}` : base;
  const savedAt = project.updatedAt ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(project.updatedAt)) : '저장 시각 없음';
  return <div className="journey-shell">
    <header className="journey-shell__header">
      <Breadcrumb items={[{ label: 'Projects', to: appRoutes.projects }, { label: project.name }]} />
      <div className="journey-shell__title"><div><p>{project.industryCategory || '사업 분야 미입력'}</p><h1>{project.name}</h1></div><div className="journey-shell__meta"><span><b>현재 단계</b>{JOURNEY_STEPS[currentIndex][0]}</span><span><b>마지막 저장</b>{savedAt}</span><span><b>AI 작업</b>{project.status === 'PAUSED' ? '확인 필요' : '대기 없음'}</span><Link to={projectRoutes.settings(projectId)} state={{ backgroundLocation: location }}>프로젝트 설정</Link></div></div>
      <nav className="journey-shell__pager" aria-label="Journey 이전 및 다음 단계">{previous ? <Link to={stepUrl(previous)}>← {previous[0]}</Link> : <span />}{next ? <Link to={stepUrl(next)}>{next[0]} →</Link> : <span>여정 마지막 단계</span>}</nav>
    </header>
    <div className="journey-shell__body">
      <aside className="journey-stepper"><p>Business Journey</p><nav aria-label="프로젝트 여정 단계"><ol>{JOURNEY_STEPS.map(([label, route], index) => {
        const stepStatus = index === currentIndex ? '현재' : index < currentIndex ? '완료' : '잠김';
        return <li key={label} className={index === currentIndex ? 'is-active' : index < currentIndex ? 'is-complete' : 'is-locked'}><NavLink to={route ? `${base}/${route}` : base} end={!route}><span>{index + 1}</span><strong>{label}</strong><small>{stepStatus}</small></NavLink></li>;
      })}</ol></nav></aside>
      <main className="journey-shell__main"><Outlet /></main>
    </div>
  </div>;
}

export default function ProjectLayout() {
  const { projectId } = useParams();
  return <ProjectProvider projectId={projectId}><ProjectLayoutContent /></ProjectProvider>;
}
