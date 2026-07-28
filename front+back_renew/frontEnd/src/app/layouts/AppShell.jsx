import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';

import { useAuth } from '../../features/auth/AuthProvider.jsx';
import { appRoutes, projectRoutes } from '../../features/projects/routing/projectRoutes.js';
import { useProjects } from '../../features/projects/hooks/useProjects.js';
import { AppIcon, Button, Drawer, ToastRegion } from '../../shared/ui/index.js';
import './layouts.css';

function userLabel(user) {
  return user?.displayName || user?.username || '계정';
}

function initialFor(user) {
  return Array.from(userLabel(user).trim())[0]?.toUpperCase() || 'V';
}

export function ProfileAvatar({ user, size = 'default' }) {
  return <span className={`profile-avatar profile-avatar--${size}`} aria-hidden="true">{initialFor(user)}</span>;
}

function AccountMenu({ user, onLogout, onClose }) {
  return (
    <div className="app-account-menu" role="menu" aria-label="계정 메뉴">
      <div className="app-account-menu__identity">
        <ProfileAvatar user={user} size="large" />
        <div><strong>{userLabel(user)}</strong><span>@{user?.username}</span>{user?.email && <small>{user.email}</small>}</div>
      </div>
      <div className="app-account-menu__links">
        <Link to={appRoutes.profileSettings} role="menuitem" onClick={onClose}><AppIcon name="settings" />계정 설정</Link>
      </div>
      <Button variant="outline" size="small" role="menuitem" onClick={onLogout}>로그아웃</Button>
    </div>
  );
}

function ProjectSearch({ onChoose }) {
  const { status, projects } = useProjects();
  const [query, setQuery] = useState('');
  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    if (!normalized || status !== 'success') return [];
    return projects.filter((project) => [project.name, project.industryCategory, project.description]
      .filter(Boolean).join(' ').toLocaleLowerCase().includes(normalized)).slice(0, 6);
  }, [projects, query, status]);

  return (
    <label className="app-project-search">
      <span className="visually-hidden">프로젝트 검색</span>
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="프로젝트 검색" />
      {visible.length > 0 && <ul role="listbox">{visible.map((project) => <li key={project.projectId}><Link to={projectRoutes.overview(project.projectId)} onClick={() => { setQuery(''); onChoose?.(); }}><strong>{project.name}</strong><span>{project.industryCategory || '사업 분야 미입력'}</span></Link></li>)}</ul>}
    </label>
  );
}

function GlobalNavigation({ onNavigate }) {
  return <nav className="app-global-nav" aria-label="주요 메뉴"><NavLink to={appRoutes.home} end onClick={onNavigate}>Home</NavLink><NavLink to={appRoutes.projects} onClick={onNavigate}>Projects</NavLink></nav>;
}

export default function AppShell() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const triggerRef = useRef(null);
  const accountMenuRef = useRef(null);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const closeAccount = () => {
    setAccountOpen(false);
    requestAnimationFrame(() => triggerRef.current?.focus());
  };

  useEffect(() => {
    if (!accountOpen) return undefined;
    const onPointerDown = (event) => {
      if (!accountMenuRef.current?.contains(event.target) && !triggerRef.current?.contains(event.target)) setAccountOpen(false);
    };
    const onKeyDown = (event) => { if (event.key === 'Escape') closeAccount(); };
    window.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('keydown', onKeyDown);
    return () => { window.removeEventListener('pointerdown', onPointerDown); window.removeEventListener('keydown', onKeyDown); };
  }, [accountOpen]);

  async function handleLogout() {
    await logout();
    navigate('/auth/login', { replace: true, state: { authTransition: true, source: 'logout', intent: 'login' } });
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">본문으로 바로가기</a>
      <header className="app-topbar">
        <Link className="app-brand" to={appRoutes.home}><span aria-hidden="true">V</span>Venture Verify</Link>
        <GlobalNavigation />
        <div className="app-topbar__actions">
          <ProjectSearch />
          <button ref={triggerRef} type="button" className="app-account-trigger" aria-label="계정 메뉴" aria-haspopup="menu" aria-expanded={accountOpen} onClick={() => setAccountOpen((open) => !open)}>
            <ProfileAvatar user={user} /><span><strong>{userLabel(user)}</strong><small>개인 계정</small></span><AppIcon name="chevronRight" className="app-account-trigger__chevron" />
          </button>
          {accountOpen && <div ref={accountMenuRef}><AccountMenu user={user} onLogout={handleLogout} onClose={closeAccount} /></div>}
        </div>
        <button type="button" className="app-mobile-menu" aria-label="메뉴 열기" aria-expanded={drawerOpen} onClick={() => setDrawerOpen(true)}><AppIcon name="more" /></button>
      </header>
      <main id="main-content" className="app-main" tabIndex="-1"><Outlet /></main>
      <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} title="메뉴">
        <GlobalNavigation onNavigate={() => setDrawerOpen(false)} />
        <ProjectSearch onChoose={() => setDrawerOpen(false)} />
        <Link className="app-drawer-new" to={appRoutes.newProject} onClick={() => setDrawerOpen(false)}>새 프로젝트</Link>
        <AccountMenu user={user} onLogout={handleLogout} onClose={() => setDrawerOpen(false)} />
      </Drawer>
      <ToastRegion />
    </div>
  );
}
