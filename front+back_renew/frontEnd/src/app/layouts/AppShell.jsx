import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';

import { globalNavigation } from '../config/navigation.js';
import { Button, Drawer, ToastRegion } from '../../shared/ui/index.js';
import { useAuth } from '../../features/auth/AuthProvider.jsx';
import './layouts.css';

function Navigation({ onNavigate }) {
  return (
    <nav className="app-nav" aria-label="주요 메뉴">
      {globalNavigation.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          onClick={onNavigate}
          className={({ isActive }) => (isActive ? 'is-active' : undefined)}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

export default function AppShell() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/auth/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">본문으로 바로가기</a>
      <header className="app-header">
        <div className="app-header__inner">
          <button
            type="button"
            className="app-header__menu"
            aria-label="메뉴 열기"
            aria-expanded={drawerOpen}
            onClick={() => setDrawerOpen(true)}
          >
            <span aria-hidden="true">☰</span>
          </button>
          <NavLink className="app-brand" to="/dashboard">
            <span className="app-brand__mark" aria-hidden="true">V</span>
            <span>사업검증 플랫폼</span>
          </NavLink>
          <div className="account-menu">
            <Button
              variant="ghost"
              size="small"
              aria-expanded={accountOpen}
              aria-controls="account-menu-panel"
              onClick={() => setAccountOpen((current) => !current)}
            >
              {user?.displayName || '내 계정'}
            </Button>
            {accountOpen && (
              <div className="account-menu__panel" id="account-menu-panel">
                <strong>{user?.displayName}</strong>
                <span>{user?.email}</span>
                <Button variant="outline" size="small" onClick={handleLogout}>
                  로그아웃
                </Button>
              </div>
            )}
          </div>
        </div>
      </header>
      <aside className="app-sidebar">
        <Navigation />
      </aside>
      <main id="main-content" className="app-main" tabIndex="-1">
        <div className="app-main__inner">
          <Outlet />
        </div>
      </main>
      <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} title="메뉴">
        <Navigation onNavigate={() => setDrawerOpen(false)} />
      </Drawer>
      <ToastRegion />
    </div>
  );
}
