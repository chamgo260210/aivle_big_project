import { NavLink, Outlet, useParams } from 'react-router-dom';

import { getProjectNavigation } from '../config/navigation.js';
import {
  Breadcrumb,
  ErrorState,
  LoadingState,
  StatusBadge,
} from '../../shared/ui/index.js';
import {
  ProjectProvider,
  useProjectContext,
} from '../../features/projects/ProjectContext.jsx';

function ProjectLayoutContent() {
  const { projectId } = useParams();
  const { status, project, retry } = useProjectContext();
  const navigation = getProjectNavigation(projectId);

  if (status === 'loading') {
    return <LoadingState label="프로젝트를 불러오는 중입니다" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="프로젝트를 찾을 수 없습니다"
        description="프로젝트가 없거나 접근 권한이 없습니다."
        onRetry={retry}
      />
    );
  }

  return (
    <div className="project-layout">
      <header className="project-context">
        <Breadcrumb items={[
          { label: '프로젝트', to: '/projects' },
          { label: project.name },
        ]} />
        <div className="project-context__title">
          <div>
            <p>{project.stageLabel}</p>
            <strong>{project.name}</strong>
          </div>
          <StatusBadge status={project.status} />
        </div>
      </header>
      <nav className="project-nav" aria-label="프로젝트 단계">
        {navigation.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => (isActive ? 'is-active' : undefined)}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className="project-content">
        <Outlet />
      </div>
    </div>
  );
}

export default function ProjectLayout() {
  const { projectId } = useParams();
  return (
    <ProjectProvider projectId={projectId}>
      <ProjectLayoutContent />
    </ProjectProvider>
  );
}
