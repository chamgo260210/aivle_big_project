import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Button } from '../../shared/ui/index.js';
import { useAuth } from '../../features/auth/AuthProvider.jsx';
import './admin-layout.css';

const navigation = [['/admin', 'Overview'], ['/admin/users', 'Users'], ['/admin/projects', 'Projects'], ['/admin/operations', 'Operations'], ['/admin/jobs', 'AI Jobs'], ['/admin/audit', 'Audit'], ['/admin/settings', 'Settings']];

export default function AdminShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  async function signOut() { await logout(); navigate('/auth/login', { replace: true }); }
  return <div className="admin-shell"><a className="skip-link" href="#admin-content">본문으로 바로가기</a><header className="admin-topbar"><Link to="/admin" className="admin-brand">Venture Verify <b>Admin</b></Link><span className="admin-environment">{import.meta.env.VITE_APP_ENVIRONMENT || 'LOCAL'}</span><div className="admin-topbar__actions"><Link to="/app">사용자 워크스페이스 보기</Link><span>{user?.displayName || user?.username}</span><Button size="small" variant="outline" onClick={signOut}>로그아웃</Button></div></header><aside className="admin-sidebar"><nav aria-label="관리자 메뉴">{navigation.map(([to, label]) => <NavLink key={to} to={to} end={to === '/admin'}>{label}</NavLink>)}</nav></aside><main id="admin-content" className="admin-content"><Outlet /></main></div>;
}
