import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { Button } from '../../../shared/ui/index.js';
import { createAdminApi } from '../api/adminApi.js';
import AdminAvailabilityNotice from '../components/AdminAvailabilityNotice.jsx';
import AdminErrorState from '../components/AdminErrorState.jsx';
import AdminMetricCard from '../components/AdminMetricCard.jsx';
import AdminPageHeader from '../components/AdminPageHeader.jsx';
import useAdminResource from '../hooks/useAdminResource.js';
import '../admin.css';

function percentage(value, total) {
  return total > 0 ? (value / total) * 100 : 0;
}

function date(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function overviewStatus(data) {
  if (!data.jobs.available) {
    return { tone: 'danger', title: 'AI 연동 확인이 필요합니다', detail: '연동 상태에서 설정과 내부 서비스 연결을 확인해 주세요.' };
  }
  if (data.jobs.failed > 0) {
    return { tone: 'warning', title: '확인할 AI 실패 작업이 있습니다', detail: `실패 또는 시간 초과 ${data.jobs.failed}건의 오류 코드를 확인해 주세요.` };
  }
  return { tone: 'positive', title: '주요 운영 상태가 정상입니다', detail: 'AI 연동이 사용 가능하고 확인할 실패 작업이 없습니다.' };
}

export default function AdminOverviewPage() {
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const request = useCallback((signal) => api.overview({ signal }), [api]);
  const { data, loading, refreshing, error, refresh } = useAdminResource(request);
  const status = data ? overviewStatus(data) : null;

  return (
    <div className="admin-page">
      <AdminPageHeader
        title="운영 개요"
        description="사용자, 프로젝트, AI 작업의 상태와 조치가 필요한 항목을 한눈에 확인합니다."
      />
      {loading && <section className="admin-panel" aria-busy="true">운영 지표를 불러오는 중입니다.</section>}
      {error && !data && <AdminErrorState error={error} onRetry={refresh} />}
      {refreshing && <p className="admin-query-progress" role="status">운영 지표를 갱신하고 있습니다.</p>}
      {data && (
        <>
          <section className={`admin-overview-hero admin-overview-hero--${status.tone}`}>
            <div>
              <span className="admin-overview-hero__eyebrow">서비스 운영 상태</span>
              <h2>{status.title}</h2>
              <p>{status.detail}</p>
              <small>기준 시각: {date(data.generatedAt)}</small>
            </div>
            <Button type="button" variant="outline" size="small" onClick={refresh}>새로고침</Button>
          </section>

          <section className="admin-overview-section" aria-labelledby="overview-actions">
            <h2 id="overview-actions">빠른 운영 확인</h2>
            <div className="admin-overview-actions">
              <Link to="/admin/users"><strong>사용자 계정</strong><span>활성 {data.users.active}명 · 제한 {data.users.locked + data.users.disabled}명</span></Link>
              <Link to="/admin/projects"><strong>프로젝트 진행</strong><span>진행 중 {data.projects.inProgress}개 · 완료 {data.projects.completed}개</span></Link>
              <Link to="/admin/jobs"><strong>AI 작업</strong><span>대기 {data.jobs.pending}건 · 실패 {data.jobs.failed}건</span></Link>
              <Link to="/admin/settings"><strong>서비스 정책</strong><span>회원가입과 점검 모드를 확인합니다</span></Link>
            </div>
          </section>

          <section className="admin-overview-section" aria-labelledby="overview-users">
            <h2 id="overview-users">사용자 현황</h2>
            <div className="admin-metrics">
              <AdminMetricCard label="전체 사용자" value={data.users.total} description={`관리자 ${data.users.admins}명 포함`} />
              <AdminMetricCard label="활성 사용자" value={data.users.active} description="현재 접근 가능한 계정" tone="positive" progress={percentage(data.users.active, data.users.total)} />
              <AdminMetricCard label="접근 제한" value={data.users.locked + data.users.disabled} description={`잠김 ${data.users.locked}명 · 비활성 ${data.users.disabled}명`} tone={data.users.locked + data.users.disabled > 0 ? 'warning' : 'neutral'} />
            </div>
          </section>

          <section className="admin-overview-section" aria-labelledby="overview-projects">
            <h2 id="overview-projects">프로젝트 현황</h2>
            <div className="admin-metrics">
              <AdminMetricCard label="전체 프로젝트" value={data.projects.total} description={`최근 7일 ${data.projects.createdLast7Days}개 생성`} />
              <AdminMetricCard label="진행 중" value={data.projects.inProgress} description="작성 중과 활성 상태" tone="positive" progress={percentage(data.projects.inProgress, data.projects.total)} />
              <AdminMetricCard label="완료" value={data.projects.completed} description={`일시 정지 ${data.projects.paused}개`} progress={percentage(data.projects.completed, data.projects.total)} />
            </div>
          </section>
          <AdminAvailabilityNotice title="AI 실행 연동" availability={data.jobs} />
        </>
      )}
    </div>
  );
}
