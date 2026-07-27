import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/apiError.js';
import { AuthProvider } from './AuthProvider.jsx';
import { LoginPage, SignupPage } from './AuthPages.jsx';
import { AUTH_STATUS } from './authSession.js';

function renderAuthPage(path, session) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider
        session={session}
        initialSnapshot={{ status: AUTH_STATUS.UNAUTHENTICATED, user: null }}
      >
        <Routes>
          <Route path="/auth/login" element={<LoginPage />} />
          <Route path="/auth/signup" element={<SignupPage />} />
          <Route path="/projects" element={<h1>프로젝트 도착</h1>} />
          <Route path="/projects/:id/overview" element={<h1>원래 화면 도착</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function fillLogin() {
  fireEvent.change(document.getElementById('login-email'), {
    target: { value: 'user@example.com' },
  });
  fireEvent.change(document.getElementById('login-password'), {
    target: { value: 'safe-password' },
  });
}

describe('auth pages', () => {
  it('submits login with accessible fields and returns to an internal route', async () => {
    const session = {
      login: vi.fn(async () => ({ id: 1, displayName: 'User' })),
      subscribe: vi.fn(),
    };
    renderAuthPage({
      pathname: '/auth/login',
      state: { returnTo: '/projects/3/overview' },
    }, session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    expect(await screen.findByRole('heading', { name: '원래 화면 도착' })).toBeInTheDocument();
    expect(session.login).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'safe-password',
    });
  });

  it('blocks an external return route', async () => {
    const session = {
      login: vi.fn(async () => ({ id: 1 })),
      subscribe: vi.fn(),
    };
    renderAuthPage({
      pathname: '/auth/login',
      state: { returnTo: '//evil.example/path' },
    }, session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    expect(await screen.findByRole('heading', { name: '프로젝트 도착' })).toBeInTheDocument();
  });

  it('shows a non-enumerating login error and moves focus to it', async () => {
    const session = {
      login: vi.fn(async () => {
        throw new ApiError({ status: 401, code: 'INVALID_CREDENTIALS' });
      }),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/login', session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다.');
    await waitFor(() => expect(alert.parentElement).toHaveFocus());
  });

  it('prevents duplicate login submission while pending', () => {
    const session = {
      login: vi.fn(() => new Promise(() => {})),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/login', session);
    fillLogin();
    const form = screen.getByRole('button', { name: '로그인' }).closest('form');
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(session.login).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: /로그인/ })).toBeDisabled();
  });

  it('validates signup password confirmation without calling the API', () => {
    const session = { signup: vi.fn(), subscribe: vi.fn() };
    renderAuthPage('/auth/signup', session);
    fireEvent.change(document.getElementById('signup-email'), { target: { value: 'new@example.com' } });
    fireEvent.change(document.getElementById('signup-display-name'), { target: { value: '새 사용자' } });
    fireEvent.change(document.getElementById('signup-password'), { target: { value: 'password-1' } });
    fireEvent.change(document.getElementById('signup-password-confirm'), { target: { value: 'password-2' } });
    fireEvent.submit(screen.getByRole('button', { name: '회원가입' }).closest('form'));
    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument();
    expect(session.signup).not.toHaveBeenCalled();
  });

  it('signs up without sending password confirmation', async () => {
    const session = {
      signup: vi.fn(async () => ({ id: 2 })),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/signup', session);
    fireEvent.change(document.getElementById('signup-email'), { target: { value: 'new@example.com' } });
    fireEvent.change(document.getElementById('signup-display-name'), { target: { value: '새 사용자' } });
    fireEvent.change(document.getElementById('signup-password'), { target: { value: 'password-1' } });
    fireEvent.change(document.getElementById('signup-password-confirm'), { target: { value: 'password-1' } });
    fireEvent.submit(screen.getByRole('button', { name: '회원가입' }).closest('form'));
    expect(await screen.findByRole('heading', { name: '프로젝트 도착' })).toBeInTheDocument();
    expect(session.signup).toHaveBeenCalledWith({
      email: 'new@example.com',
      displayName: '새 사용자',
      password: 'password-1',
    });
  });
});
