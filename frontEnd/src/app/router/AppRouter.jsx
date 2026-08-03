import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';

import AppShell from '../layouts/AppShell.jsx';
import ProjectLayout from '../layouts/ProjectLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import { LoginPage, SignupPage } from '../../features/auth/AuthPages.jsx';
import ProtectedRoute from '../../features/auth/ProtectedRoute.jsx';
import AdminRoute from '../../features/auth/AdminRoute.jsx';
import PublicOnlyRoute from '../../features/auth/PublicOnlyRoute.jsx';
import {
  ProjectCreatePage,
  ProjectListPage,
} from '../../features/projects/ProjectPages.jsx';
import ProjectGetStartedPage from '../../features/projects/ProjectGetStartedPage.jsx';
import { ProjectOverviewPage } from '../../features/projects/ProjectAreaPages.jsx';
import WorkspaceHomePage from '../../features/projects/WorkspaceHomePage.jsx';
import { AccountSettingsLayout, AccountSettingsRedirect, ProfileSettingsPage, SecuritySettingsPage } from '../../features/settings/AccountSettingsPages.jsx';
import ProjectSettingsSheet from '../../features/projects/ProjectSettingsSheet.jsx';
import { ProjectProvider } from '../../features/projects/ProjectContext.jsx';
import { DocumentUploadPage, StructuredPlanPage } from '../../features/documents/DocumentPages.jsx';
import { AuthPlaceholderPage, NotFoundPage } from '../../pages/FoundationPages.jsx';
import LegalReviewPage from '../../features/legal-review/LegalReviewPage.jsx';
import FeasibilityPage from '../../features/feasibility/FeasibilityPage.jsx';
import PersonaPage from '../../features/personas/PersonaPage.jsx';
import ReportPage from '../../features/report/ReportPage.jsx';
import LandingPage from '../../features/landing/LandingPage.jsx';
import AdminShell from '../layouts/AdminShell.jsx';
import { AdminAuditPage, AdminJobsPage, AdminOperationsPage, AdminOverviewPage, AdminProjectsPage, AdminSettingsPage, AdminUsersPage } from '../../features/admin/pages/AdminPages.jsx';
import InterimReportTestPage from '../../features/report/InterimReportTestPage.jsx';

function LegacyProjectRedirect({ suffix = '' }) {
  const { projectId } = useParams();
  return <Navigate to={`/app/projects/${projectId}${suffix}`} replace />;
}

function ProjectSettingsOverlay() {
  const { projectId } = useParams();
  return <ProjectProvider projectId={projectId}><ProjectSettingsSheet /></ProjectProvider>;
}

export default function AppRouter() {
  const location = useLocation();
  const backgroundLocation = location.state?.backgroundLocation;
  return (
    <>
    <Routes location={backgroundLocation || location}>
      <Route element={<PublicLayout />}>
        <Route index element={<LandingPage />} />
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
          <Route path="app" element={<WorkspaceHomePage />} />
          <Route path="app/projects" element={<ProjectListPage />} />
          <Route path="app/projects/new" element={<ProjectCreatePage />} />
          <Route path="app/settings" element={<AccountSettingsLayout />}>
            <Route index element={<AccountSettingsRedirect />} />
            <Route path="profile" element={<ProfileSettingsPage />} />
            <Route path="security" element={<SecuritySettingsPage />} />
          </Route>
          <Route path="app/projects/:projectId" element={<ProjectLayout />}>
            <Route index element={<ProjectOverviewPage />} />
            <Route path="get-started" element={<ProjectGetStartedPage />} />
            <Route path="plan" element={<Navigate to="documents" replace />} />
            <Route path="plan/brief" element={<Navigate to="../settings" replace />} />
            <Route path="plan/documents" element={<DocumentUploadPage />} />
            <Route path="plan/structure" element={<StructuredPlanPage />} />
            <Route path="review" element={<Navigate to="legal" replace />} />
            <Route path="review/legal" element={<LegalReviewPage />} />
            <Route path="review/market" element={<FeasibilityPage />} />
            <Route path="validate" element={<Navigate to="personas" replace />} />
            <Route path="validate/personas" element={<PersonaPage />} />
            <Route path="report" element={<ReportPage />} />
            <Route path="settings" element={<ProjectSettingsSheet />} />
            <Route path="settings/general" element={<Navigate to="../settings" replace />} />
            <Route path="settings/danger" element={<Navigate to="../settings" replace />} />
          </Route>

          {/* 테스트용 임시 경로 */}
          <Route path="app/test/interim-report/:projectId" element={<InterimReportTestPage />} />

          <Route path="dashboard" element={<Navigate to="/app" replace />} />
          <Route path="projects" element={<Navigate to="/app/projects" replace />} />
          <Route path="projects/new" element={<Navigate to="/app/projects/new" replace />} />
          <Route path="reports" element={<Navigate to="/app/projects" replace />} />
          <Route path="settings" element={<Navigate to="/app/settings/profile" replace />} />
          <Route path="projects/:projectId" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/overview" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/input" element={<LegacyProjectRedirect suffix="/settings" />} />
          <Route path="projects/:projectId/documents" element={<LegacyProjectRedirect suffix="/plan/documents" />} />
          <Route path="projects/:projectId/structure" element={<LegacyProjectRedirect suffix="/plan/structure" />} />
          <Route path="projects/:projectId/structured-plan" element={<LegacyProjectRedirect suffix="/plan/structure" />} />
          <Route path="projects/:projectId/structured-plan/missing-fields" element={<LegacyProjectRedirect suffix="/plan/structure" />} />
          <Route path="projects/:projectId/legal-review" element={<LegacyProjectRedirect suffix="/review/legal" />} />
          <Route path="projects/:projectId/feasibility" element={<LegacyProjectRedirect suffix="/review/market" />} />
          <Route path="projects/:projectId/analyses/:analysis" element={<LegacyProjectRedirect suffix="/review/market" />} />
          <Route path="projects/:projectId/personas" element={<LegacyProjectRedirect suffix="/validate/personas" />} />
          <Route path="projects/:projectId/panel-survey" element={<LegacyProjectRedirect suffix="/validate" />} />
          <Route path="projects/:projectId/panel-discussion" element={<LegacyProjectRedirect suffix="/validate" />} />
          <Route path="projects/:projectId/market-validation" element={<LegacyProjectRedirect suffix="/validate" />} />
          <Route path="projects/:projectId/report" element={<LegacyProjectRedirect suffix="/report" />} />
          <Route path="projects/:projectId/reports/*" element={<LegacyProjectRedirect suffix="/report" />} />
          <Route path="projects/:projectId/marketing" element={<LegacyProjectRedirect suffix="/report" />} />
          <Route path="projects/:projectId/settings" element={<LegacyProjectRedirect suffix="/settings" />} />
        </Route>

        <Route element={<AdminRoute />}>
          <Route element={<AdminShell />}>
            <Route path="admin" element={<AdminOverviewPage />} />
            <Route path="admin/users" element={<AdminUsersPage />} />
            <Route path="admin/projects" element={<AdminProjectsPage />} />
            <Route path="admin/operations" element={<AdminOperationsPage />} />
            <Route path="admin/jobs" element={<AdminJobsPage />} />
            <Route path="admin/audit" element={<AdminAuditPage />} />
            <Route path="admin/settings" element={<AdminSettingsPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
    {backgroundLocation && <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="app/projects/new" element={<ProjectCreatePage />} />
        <Route path="app/projects/:projectId/settings" element={<ProjectSettingsOverlay />} />
      </Route>
    </Routes>}
    </>
  );
}
