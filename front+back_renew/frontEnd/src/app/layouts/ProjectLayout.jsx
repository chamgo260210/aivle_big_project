import { NavLink, Outlet, useLocation, useParams } from 'react-router-dom';

import { AppIcon, Breadcrumb, ErrorState, LoadingState, StatusBadge } from '../../shared/ui/index.js';
import { ProjectProvider, useProjectContext } from '../../features/projects/ProjectContext.jsx';
import {
  PROJECT_AREAS,
  PROJECT_AREA_DEFINITIONS,
  getProjectArea,
  getProjectBasePath,
} from '../../features/projects/model/projectWorkflowModel.js';
import { appRoutes, projectRoutes } from '../../features/projects/routing/projectRoutes.js';

const SUBNAVIGATION = {
  [PROJECT_AREAS.PLAN]: [
    ['Documents', 'plan/documents'], ['Structured Plan', 'plan/structure'],
  ],
  [PROJECT_AREAS.REVIEW]: [
    ['Legal', 'review/legal'], ['Feasibility', 'review/market'],
  ],
  [PROJECT_AREAS.REPORT]: [['Integrated Report', 'report']],
};

function getActiveArea(pathname, basePath) {
  if (pathname.startsWith(`${basePath}/settings`)) return null;
  const relativePath = pathname.slice(basePath.length).replace(/^\//, '');
  if (relativePath.startsWith('plan')) return PROJECT_AREAS.PLAN;
  if (relativePath.startsWith('review')) return PROJECT_AREAS.REVIEW;
  if (relativePath.startsWith('validate')) return PROJECT_AREAS.VALIDATE;
  if (relativePath.startsWith('report')) return PROJECT_AREAS.REPORT;
  return PROJECT_AREAS.OVERVIEW;
}

function ProjectLayoutContent() {
  const { projectId } = useParams();
  const location = useLocation();
  const { status, project, retry } = useProjectContext();

  if (status === 'loading') return <LoadingState label="프로젝트를 불러오고 있습니다" />;
  if (status === 'error') {
    return <ErrorState title="프로젝트를 찾을 수 없습니다" description="프로젝트가 없거나 접근 권한이 없습니다." onRetry={retry} />;
  }

  const basePath = getProjectBasePath(projectId);
  const activeArea = getActiveArea(location.pathname, basePath);
  const subnavigation = SUBNAVIGATION[activeArea] ?? [];

  return (
    <div className="project-shell">
      <header className="project-shell__header">
        <Breadcrumb items={[{ label: 'Projects', to: appRoutes.projects }, { label: project.name }]} />
        <div className="project-shell__meta">
          <div>
            <p>{project.industryCategory || '사업 분야 미입력'} · 최근 수정 {new Date(project.updatedAt).toLocaleDateString('ko-KR')}</p>
            <h1>{project.name}</h1>
          </div>
          <div className="project-shell__actions"><StatusBadge status={project.status} /><NavLink className="project-shell__settings" to={projectRoutes.settings(projectId)}><AppIcon name="settings" />Settings</NavLink></div>
        </div>
      </header>
      <nav className="project-area-nav" aria-label="프로젝트 영역">
        {PROJECT_AREA_DEFINITIONS.map((area) => (
          <NavLink key={area.id} to={area.id === PROJECT_AREAS.OVERVIEW ? basePath : `${basePath}/${area.path}`} end={area.id === PROJECT_AREAS.OVERVIEW}>
            {area.label}
          </NavLink>
        ))}
      </nav>
      {subnavigation.length > 1 && (
        <nav className="project-subnav" aria-label={`${PROJECT_AREA_DEFINITIONS.find((area) => area.id === activeArea)?.label} 세부 메뉴`}>
          {subnavigation.map(([label, route]) => <NavLink key={route} to={`${basePath}/${route}`} end={route === activeArea.toLowerCase()}>{label}</NavLink>)}
        </nav>
      )}
      <div className="project-shell__content"><Outlet context={{ activeArea, currentArea: getProjectArea(project) }} /></div>
    </div>
  );
}

export default function ProjectLayout() {
  const { projectId } = useParams();
  return <ProjectProvider projectId={projectId}><ProjectLayoutContent /></ProjectProvider>;
}
