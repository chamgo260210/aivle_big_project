import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../shared/ui/index.js';
import { appRoutes, projectRoutes } from './routing/projectRoutes.js';
import ProjectRow from './components/ProjectRow.jsx';
import ProjectDeleteDialog from './components/ProjectDeleteDialog.jsx';
import { useProjects } from './hooks/useProjects.js';
import { ResourceDownload } from './BusinessPlanResources.jsx';
import { BUSINESS_PLAN_RESOURCES } from './businessPlanResources.js';
import './projects.css';

function overlayState(location) {
  return { backgroundLocation: location, returnTo: `${location.pathname}${location.search}`, source: 'home' };
}

function displayName(user) {
  return user?.displayName || user?.username || '사용자';
}

function GettingStartedRail({ projects, newest, location }) {
  const steps = [
    { label: '프로젝트 만들기', done: projects.length > 0, route: appRoutes.newProject },
    { label: '작성 가이드 확인', done: false, href: BUSINESS_PLAN_RESOURCES[0].href },
    { label: '사업계획서 업로드', done: newest?.stage && newest.stage !== 'DOCUMENT', route: newest ? projectRoutes.documents(newest.projectId) : null },
    { label: '구조화 결과 확인', done: newest?.stage && !['DOCUMENT', 'STRUCTURING'].includes(newest.stage), route: newest ? projectRoutes.structure(newest.projectId) : null },
    { label: '검토 시작', done: newest?.stage && ['FEASIBILITY', 'FINANCIAL', 'PERSONA_CONFIGURATION', 'REPORT', 'COMPLETED'].includes(newest.stage), route: newest ? projectRoutes.review(newest.projectId) : null },
  ];
  const current = steps.findIndex((step) => !step.done);
  return <aside className="getting-started-rail" aria-labelledby="workspace-getting-started-title"><p>Getting started</p><h2 id="workspace-getting-started-title">사업 검증 시작 순서</h2><ol>{steps.map((step, index) => {
    const className = step.done ? 'is-done' : index === current ? 'is-current' : '';
    const content = <><span>{step.done ? '✓' : index + 1}</span><strong>{step.label}</strong></>;
    return <li key={step.label} className={className}>{step.route ? <Link to={step.route} state={step.route === appRoutes.newProject ? overlayState(location) : undefined}>{content}</Link> : step.href ? <a href={step.href} download={BUSINESS_PLAN_RESOURCES[0].download}>{content}</a> : content}</li>;
  })}</ol></aside>;
}

export default function WorkspaceHomePage() {
  const { user } = useAuth();
  const location = useLocation();
  const { status, projects, retry } = useProjects();
  const [deleteTarget, setDeleteTarget] = useState(null);
  if (status === 'loading') return <LoadingState label="워크스페이스를 불러오고 있습니다" />;
  if (status === 'error') return <ErrorState title="워크스페이스를 불러오지 못했습니다" onRetry={retry} />;
  const recent = [...projects]
    .sort((left, right) => new Date(right.updatedAt) - new Date(left.updatedAt))
    .slice(0, 3);
  const newest = recent[0];
  const showGettingStarted = projects.length === 0 || projects.every((project) => project.stage === 'DOCUMENT');
  return <div className="workspace-home"><PageHeader eyebrow="Personal workspace" title={`안녕하세요, ${displayName(user)}님`} description="사업 검증 프로젝트를 관리하고 다음 분석을 이어가세요." />
    <div className="workspace-home__layout">
      {showGettingStarted && <GettingStartedRail projects={projects} newest={newest} location={location} />}
      <div className="workspace-home__content">
        <section className="workspace-home__quick" aria-labelledby="workspace-quick-title"><div><h2 id="workspace-quick-title">빠른 시작</h2><p>프로젝트를 만들고 사업계획서 DOCX를 업로드해 검증을 시작하세요.</p></div><div className="workspace-home__quick-actions"><Link to={appRoutes.newProject} state={overlayState(location)}>새 프로젝트 만들기</Link>{BUSINESS_PLAN_RESOURCES.map((resource) => <ResourceDownload key={resource.id} resource={resource} compact />)}</div></section>
        {recent.length > 0 ? <section className="workspace-home__recent" aria-labelledby="workspace-recent-title"><div className="section-heading"><div><p>Recent projects</p><h2 id="workspace-recent-title">최근 프로젝트</h2></div><Link to={appRoutes.projects}>모든 프로젝트 보기</Link></div><div className="workspace-home__recent-list">{recent.map((project) => <ProjectRow key={project.projectId} project={project} density="compact" showNextAction={false} onDelete={() => setDeleteTarget(project)} />)}</div></section> : <EmptyState title="첫 사업 검증 프로젝트를 만들어 보세요" description="프로젝트를 만든 뒤 사업계획서 DOCX를 업로드하면 문서 분석과 구조화를 시작할 수 있습니다." action={<Link className="primary-link" to={appRoutes.newProject} state={overlayState(location)}>프로젝트 만들기</Link>} />}
      </div>
    </div><ProjectDeleteDialog project={deleteTarget} open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onDeleted={async () => { setDeleteTarget(null); await retry(); }} />
  </div>;
}
