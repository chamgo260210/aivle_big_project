import { Navigate, Route, Routes } from 'react-router-dom';

import AppShell from '../layouts/AppShell.jsx';
import ProjectLayout from '../layouts/ProjectLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import { LoginPage, SignupPage } from '../../features/auth/AuthPages.jsx';
import ProtectedRoute from '../../features/auth/ProtectedRoute.jsx';
import PublicOnlyRoute from '../../features/auth/PublicOnlyRoute.jsx';
import {
  ProjectCreatePage,
  ProjectListPage,
  ProjectOverviewPage,
} from '../../features/projects/ProjectPages.jsx';
import {
  DocumentUploadPage,
  StructuredPlanPage,
} from '../../features/documents/DocumentPages.jsx';
import {
  AuthPlaceholderPage,
  DashboardPage,
  HomePage,
  NotFoundPage,
  ProjectPlaceholderPage,
  SimplePlaceholderPage,
} from '../../pages/FoundationPages.jsx';
import LegalReviewPage from '../../features/legal-review/LegalReviewPage.jsx';
import FeasibilityPage from '../../features/feasibility/FeasibilityPage.jsx';
import PersonaPage from '../../features/personas/PersonaPage.jsx';
import ReportPage from '../../features/report/ReportPage.jsx';

export default function AppRouter() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route element={<PublicOnlyRoute />}>
          <Route path="auth/login" element={<LoginPage />} />
          <Route path="auth/signup" element={<SignupPage />} />
          <Route path="auth/password-reset" element={<AuthPlaceholderPage mode="reset" />} />
        </Route>
        <Route path="login" element={<Navigate to="/auth/login" replace />} />
        <Route path="signup" element={<Navigate to="/auth/signup" replace />} />
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="projects" element={<ProjectListPage />} />
          <Route path="projects/new" element={<ProjectCreatePage />} />
          <Route path="reports" element={<SimplePlaceholderPage title="보고서" description="전체 프로젝트 보고서를 확인합니다." />} />
          <Route path="settings" element={<SimplePlaceholderPage title="설정" description="사용자 프로필과 환경을 관리합니다." />} />
          <Route path="projects/:projectId" element={<ProjectLayout />}>
            <Route index element={<Navigate to="overview" replace />} />
            <Route path="overview" element={<ProjectOverviewPage />} />
            <Route path="documents" element={<DocumentUploadPage />} />
            <Route path="structure" element={<StructuredPlanPage />} />
            <Route path="structured-plan" element={<Navigate to="../structure" replace />} />
            <Route path="structured-plan/missing-fields" element={<ProjectPlaceholderPage page="missing-fields" />} />
            <Route path="legal-review" element={<LegalReviewPage />} />
            <Route path="feasibility" element={<FeasibilityPage />} />
            <Route path="analyses/market" element={<Navigate to="../../feasibility" relative="path" replace />} />
            <Route path="analyses/business-model" element={<Navigate to="../../feasibility" relative="path" replace />} />
            <Route path="analyses/technology-operation" element={<Navigate to="../../feasibility" relative="path" replace />} />
            <Route path="analyses/financial" element={<ProjectPlaceholderPage page="financial" />} />
            <Route path="personas" element={<PersonaPage />} />
            <Route path="report" element={<ReportPage />} />
            <Route path="panel-survey" element={<ProjectPlaceholderPage page="panel-survey" />} />
            <Route path="panel-discussion" element={<ProjectPlaceholderPage page="panel-discussion" />} />
            <Route path="market-validation" element={<ProjectPlaceholderPage page="market-validation" />} />
            <Route path="reports" element={<Navigate to="../report" replace />} />
            <Route path="reports/:reportId" element={<Navigate to="../../report" relative="path" replace />} />
            <Route path="marketing" element={<ProjectPlaceholderPage page="marketing" />} />
            <Route path="settings" element={<ProjectPlaceholderPage page="settings" />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
