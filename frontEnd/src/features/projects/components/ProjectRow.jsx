import { Link } from 'react-router-dom';

import { StatusBadge } from '../../../shared/ui/index.js';
import { getProjectArea } from '../model/projectWorkflowModel.js';
import { projectRoutes } from '../routing/projectRoutes.js';
import { ProjectActionMenu } from '../ProjectSettingsSheet.jsx';

function formatProjectDate(value) {
  if (!value) return '날짜 정보 없음';
  return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(new Date(value));
}

export default function ProjectRow({ project, density = 'default', showNextAction = true, menuOpen = false, onMenuOpenChange, onDelete }) {
  const areaLabel = getProjectArea(project).label;
  const compact = density === 'compact';
  return <article className={`project-row project-row--${density} ${menuOpen ? 'project-row--menu-open' : ''}`} role="listitem">
    <Link className="project-row__main-link" to={projectRoutes.overview(project.projectId)} aria-labelledby={`project-row-title-${project.projectId}`}>
      <div className="project-row__project"><span className="project-row__initial" aria-hidden="true">{Array.from(project.name)[0]}</span><div><h2 id={`project-row-title-${project.projectId}`}>{project.name}</h2><p>{project.industryCategory || '사업 분야 미입력'}</p></div></div>
      <span className="project-row__area">{areaLabel}</span>
      <StatusBadge status={project.status} />
      {!compact && (showNextAction ? <span className="project-row__next">{project.nextAction.label}</span> : <span className="project-row__next">최근 수정</span>)}
      <time dateTime={project.updatedAt}>{formatProjectDate(project.updatedAt)}</time>
    </Link>
    <ProjectActionMenu project={project} onOpenChange={onMenuOpenChange} onDelete={onDelete} />
  </article>;
}
