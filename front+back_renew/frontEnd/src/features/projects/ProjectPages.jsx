import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import {
  Alert,
  Button,
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  Textarea,
  TextInput,
} from '../../shared/ui/index.js';
import { createProjectApi } from './api/projectApi.js';
import { useProjectContext } from './ProjectContext.jsx';
import { formatProjectDate } from './model/projectViewModel.js';
import { useProjects } from './hooks/useProjects.js';
import ProjectDashboard from '../report/ProjectDashboard.jsx';
import './projects.css';

export function ProjectListPage() {
  const { status, projects, retry } = useProjects();
  if (status === 'loading') return <LoadingState label="프로젝트를 불러오는 중입니다" />;
  if (status === 'error') {
    return (
      <ErrorState
        title="프로젝트를 불러오지 못했습니다"
        description="네트워크 연결을 확인한 뒤 다시 시도해 주세요."
        onRetry={retry}
      />
    );
  }
  return (
    <>
      <PageHeader
        title="프로젝트"
        description="최근 수정된 사업 검증 프로젝트부터 표시합니다."
        actions={<Link className="primary-link" to="/projects/new">새 프로젝트</Link>}
      />
      {projects.length === 0 ? (
        <EmptyState
          title="아직 프로젝트가 없습니다"
          description="첫 사업 검증 프로젝트를 만들어 시작하세요."
          action={<Link className="primary-link" to="/projects/new">프로젝트 만들기</Link>}
        />
      ) : (
        <div className="project-grid">
          {projects.map((project) => (
            <Card key={project.projectId} className="project-card">
              <div className="project-card__status">
                <StatusBadge status={project.status} />
                <span>{project.stageLabel}</span>
              </div>
              <h2><Link to={`/projects/${project.projectId}/overview`}>{project.name}</Link></h2>
              <p>{project.industryCategory || '사업 분야 미입력'}</p>
              <small>최근 수정 {formatProjectDate(project.updatedAt)}</small>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}

export function ProjectCreatePage() {
  const client = useApiClient();
  const navigate = useNavigate();
  const errorRef = useRef(null);
  const [values, setValues] = useState({
    title: '',
    description: '',
    industryCategory: '',
  });
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (event) => {
      setValues((current) => ({ ...current, [field]: event.target.value }));
      setErrors((current) => ({ ...current, [field]: undefined }));
    };
  }

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
      const created = await createProjectApi(client).create({
        title: values.title.trim(),
        description: values.description.trim() || null,
        industryCategory: values.industryCategory.trim() || null,
      });
      navigate(`/projects/${created.id}/overview`, { replace: true });
    } catch (error) {
      setErrors(Object.fromEntries(
        (error.fieldErrors ?? []).map((item) => [item.field, item.message]),
      ));
      setGlobalError(getUserErrorMessage(error));
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader
        title="새 프로젝트"
        description="현재 검증하려는 사업의 기본 정보를 입력하세요."
      />
      {globalError && (
        <div ref={errorRef} tabIndex="-1">
          <Alert tone="danger" title="프로젝트를 만들지 못했습니다">{globalError}</Alert>
        </div>
      )}
      <form className="project-form" onSubmit={handleSubmit} noValidate>
        <TextInput
          id="project-title"
          label="프로젝트 이름"
          value={values.title}
          error={errors.title}
          maxLength="150"
          onChange={update('title')}
          required
        />
        <TextInput
          id="project-category"
          label="사업 분야"
          description="선택 입력입니다."
          value={values.industryCategory}
          error={errors.industryCategory}
          maxLength="100"
          onChange={update('industryCategory')}
        />
        <Textarea
          id="project-description"
          label="설명"
          description="선택 입력이며 최대 10,000자입니다."
          value={values.description}
          error={errors.description}
          maxLength="10000"
          onChange={update('description')}
        />
        <div className="project-form__actions">
          <Button type="submit" loading={submitting}>프로젝트 만들기</Button>
          <Button type="button" variant="outline" disabled={submitting} onClick={() => navigate('/projects')}>
            취소
          </Button>
        </div>
      </form>
    </>
  );
}

export function ProjectOverviewPage() {
  const { project } = useProjectContext();
  return <ProjectDashboard project={project} />;
}
