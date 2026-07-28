import { useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import {
  Alert,
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  SideSheet,
  Textarea,
  TextInput,
} from '../../shared/ui/index.js';
import { createProjectApi } from './api/projectApi.js';
import { useProjectContext } from './ProjectContext.jsx';
import { useProjects } from './hooks/useProjects.js';
import { formatProjectDate } from './model/projectViewModel.js';
import { PROJECT_AREA_DEFINITIONS } from './model/projectWorkflowModel.js';
import { appRoutes, projectRoutes } from './routing/projectRoutes.js';
import { ProjectActionMenu } from './ProjectSettingsSheet.jsx';
import './projects.css';

function filterMatches(project, filter) {
  if (filter === 'all') return true;
  return project.status === filter;
}

export function ProjectListPage() {
  const client = useApiClient();
  const { status, projects, retry } = useProjects();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('all');
  const [sort, setSort] = useState('updated');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteName, setDeleteName] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const visible = useMemo(() => projects
    .filter((project) => filterMatches(project, filter)
      && `${project.name} ${project.industryCategory}`.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => {
      if (sort === 'name') return a.name.localeCompare(b.name, 'ko');
      if (sort === 'created') return new Date(b.createdAt) - new Date(a.createdAt);
      return new Date(b.updatedAt) - new Date(a.updatedAt);
    }), [projects, query, filter, sort]);

  if (status === 'loading') return <LoadingState label="프로젝트를 불러오고 있습니다" />;
  if (status === 'error') {
    return <ErrorState title="프로젝트를 불러오지 못했습니다" description="네트워크 연결을 확인한 뒤 다시 시도해 주세요." onRetry={retry} />;
  }

  return (
    <div className="project-hub">
      <PageHeader
        eyebrow="내 워크스페이스"
        title="프로젝트"
        description="사업 검증의 입력, 실행, 결과를 프로젝트 단위로 관리합니다."
        actions={<Link className="primary-link" to={appRoutes.newProject}>새 프로젝트</Link>}
      />
      {!projects.length ? (
        <EmptyState
          title="아직 프로젝트가 없습니다"
          description="첫 사업 검증 프로젝트를 만들어 시작하세요."
          action={<Link className="primary-link" to={appRoutes.newProject}>프로젝트 만들기</Link>}
        />
      ) : (
        <>
          <div className="project-toolbar">
            <label>
              <span className="visually-hidden">프로젝트 검색</span>
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="프로젝트 또는 사업 분야 검색" />
            </label>
            <div role="group" aria-label="프로젝트 상태 필터">
              {[['all', '전체'], ['ACTIVE', '진행 중'], ['DRAFT', '초안'], ['PAUSED', '일시 중지'], ['COMPLETED', '완료']].map(([value, label]) => (
                <button key={value} type="button" className={filter === value ? 'is-active' : ''} onClick={() => setFilter(value)}>{label}</button>
              ))}
            </div>
            <select aria-label="프로젝트 정렬" value={sort} onChange={(event) => setSort(event.target.value)}>
              <option value="updated">최근 수정순</option>
              <option value="created">최근 생성순</option>
              <option value="name">이름순</option>
            </select>
          </div>
          <div className="project-row-list" role="list" aria-label="프로젝트 목록">
            <div className="project-row-list__header" aria-hidden="true"><span>Name</span><span>Area</span><span>Status</span><span>Next action</span><span>Updated</span></div>
            {visible.map((project) => {
              const areaLabel = PROJECT_AREA_DEFINITIONS.find((area) => area.id === project.area)?.label ?? 'Plan';
              return (
                <article key={project.projectId} className="project-row" role="listitem">
                  <Link className="project-row__main-link" to={projectRoutes.overview(project.projectId)} aria-labelledby={`project-row-title-${project.projectId}`}>
                    <div className="project-row__project"><span className="project-row__initial" aria-hidden="true">{Array.from(project.name)[0]}</span><div><h2 id={`project-row-title-${project.projectId}`}>{project.name}</h2><p>{project.industryCategory || '사업 분야 미입력'}</p></div></div>
                    <span>{areaLabel}</span>
                    <StatusBadge status={project.status} />
                    <span className="project-row__next">{project.nextAction.label}</span>
                    <time dateTime={project.updatedAt}>{formatProjectDate(project.updatedAt)}</time>
                  </Link>
                  <ProjectActionMenu project={project} onDelete={() => { setDeleteTarget(project); setDeleteName(''); setDeleteError(''); }} />
                </article>
              );
            })}
          </div>
          {!visible.length && <p className="project-search-empty">조건에 맞는 프로젝트가 없습니다.</p>}
        </>
      )}
      <Dialog open={Boolean(deleteTarget)} onClose={() => !deleting && setDeleteTarget(null)} title="프로젝트를 삭제할까요?"><p>이 작업은 되돌릴 수 없습니다. 확인을 위해 <strong>{deleteTarget?.name}</strong>을(를) 입력하세요.</p>{deleteError && <Alert tone="danger" title="삭제하지 못했습니다.">{deleteError}</Alert>}<TextInput id="project-row-delete-confirmation" label="프로젝트 이름" value={deleteName} onChange={(event) => setDeleteName(event.target.value)} /><div className="dialog-actions"><Button variant="outline" disabled={deleting} onClick={() => setDeleteTarget(null)}>취소</Button><Button variant="danger" loading={deleting} disabled={!deleteTarget || deleteName !== deleteTarget.name || deleting} onClick={async () => { if (!deleteTarget) return; setDeleting(true); try { await createProjectApi(client).remove(deleteTarget.projectId); setDeleteTarget(null); await retry(); } catch (error) { setDeleteError(getUserErrorMessage(error)); setDeleting(false); } }}>영구 삭제</Button></div></Dialog>
    </div>
  );
}

export function ProjectCreatePage() {
  const client = useApiClient();
  const navigate = useNavigate();
  const errorRef = useRef(null);
  const [values, setValues] = useState({ title: '', description: '', industryCategory: '' });
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const update = (field) => (event) => {
    setValues((current) => ({ ...current, [field]: event.target.value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    if (!values.title.trim()) {
      setErrors({ title: '프로젝트 이름을 입력해 주세요.' });
      return;
    }
    setSubmitting(true);
    setGlobalError('');
    try {
      const nextProject = await createProjectApi(client).create({
        title: values.title.trim(),
        description: values.description.trim() || null,
        industryCategory: values.industryCategory.trim() || null,
      });
      navigate(projectRoutes.getStarted(nextProject.id), { replace: true });
    } catch (error) {
      setErrors(Object.fromEntries((error.fieldErrors ?? []).map((item) => [item.field, item.message])));
      setGlobalError(getUserErrorMessage(error));
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <ProjectListPage />
      <SideSheet open title="New Project" label="새 프로젝트" onClose={() => navigate(appRoutes.projects)}>
    <div className="project-create project-create--sheet">
      <PageHeader eyebrow="새 프로젝트" title="검증할 사업 아이디어를 만드세요" description="지금은 최소 정보만 필요합니다. 세부 자료와 분석 실행은 프로젝트 안에서 직접 시작합니다." />
      {globalError && <div ref={errorRef} tabIndex="-1"><Alert tone="danger" title="프로젝트를 만들지 못했습니다">{globalError}</Alert></div>}
      <form className="project-form" onSubmit={handleSubmit} noValidate>
        <TextInput id="project-title" label="프로젝트 이름" value={values.title} error={errors.title} maxLength="150" onChange={update('title')} required />
        <TextInput id="project-category" label="사업 분야" description="선택 입력입니다." value={values.industryCategory} error={errors.industryCategory} maxLength="100" onChange={update('industryCategory')} />
        <Textarea id="project-description" label="간단한 설명" description="선택 입력입니다." value={values.description} error={errors.description} maxLength="10000" onChange={update('description')} />
        <div className="project-form__actions">
          <Button type="submit" loading={submitting} disabled={submitting}>프로젝트 만들기</Button>
          <Button type="button" variant="outline" disabled={submitting} onClick={() => navigate(appRoutes.projects)}>취소</Button>
        </div>
      </form>
    </div>
      </SideSheet>
    </>
  );
}

export function ProjectBriefInputPage() {
  const client = useApiClient();
  const navigate = useNavigate();
  const { project } = useProjectContext();
  const [values, setValues] = useState({
    title: project.name,
    industryCategory: project.industryCategory || '',
    description: project.description || '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const update = (field) => (event) => setValues((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    if (saving || !values.title.trim()) return;
    setSaving(true);
    setError('');
    try {
      await createProjectApi(client).update(project.projectId, {
        title: values.title.trim(),
        industryCategory: values.industryCategory.trim() || null,
        description: values.description.trim() || null,
      });
      navigate(`/app/projects/${project.projectId}`);
    } catch (requestError) {
      setError(getUserErrorMessage(requestError));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="project-create project-brief-input">
      <PageHeader
        eyebrow="사업계획 입력"
        title="사업 개요를 직접 작성하세요"
        description="현재 단계에서는 프로젝트 기본 정보와 사업 설명을 저장합니다. 상세 계획 문서는 별도로 업로드할 수 있습니다."
        actions={<Link to={`/app/projects/${project.projectId}/plan/documents`}>문서 업로드로 시작</Link>}
      />
      {error && <Alert tone="danger" title="사업 개요를 저장하지 못했습니다">{error}</Alert>}
      <form className="project-form" onSubmit={handleSubmit} noValidate>
        <TextInput id="project-brief-title" label="프로젝트 이름" value={values.title} maxLength="150" onChange={update('title')} required />
        <TextInput id="project-brief-category" label="사업 분야" value={values.industryCategory} maxLength="100" onChange={update('industryCategory')} />
        <Textarea id="project-brief-description" label="사업 개요" description="누구의 어떤 문제를 어떻게 해결하는지 자유롭게 작성해 주세요." value={values.description} maxLength="10000" onChange={update('description')} />
        <div className="project-form__actions">
          <Button type="submit" loading={saving} disabled={saving}>저장하고 개요로 이동</Button>
          <Button type="button" variant="outline" disabled={saving} onClick={() => navigate(`/app/projects/${project.projectId}`)}>나중에 작성</Button>
        </div>
      </form>
    </div>
  );
}
