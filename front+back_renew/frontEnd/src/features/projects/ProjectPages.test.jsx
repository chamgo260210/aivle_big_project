import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/apiError.js';
import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import { ProjectCreatePage, ProjectListPage } from './ProjectPages.jsx';

function renderProject(element, client, path = '/projects') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ApiClientProvider client={client}>
        <Routes>
          <Route path="/projects" element={element} />
          <Route path="/projects/new" element={element} />
          <Route path="/projects/:id/overview" element={<h1>프로젝트 상세 도착</h1>} />
        </Routes>
      </ApiClientProvider>
    </MemoryRouter>,
  );
}

const project = {
  id: 5,
  title: '실제 프로젝트',
  industryCategory: 'SaaS',
  stage: 'DOCUMENT',
  status: 'DRAFT',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-03T00:00:00Z',
};

describe('project pages', () => {
  it('renders a real empty state', async () => {
    renderProject(<ProjectListPage />, {
      get: vi.fn(async () => ({ data: [] })),
    });
    expect(await screen.findByRole('heading', { name: '아직 프로젝트가 없습니다' }))
      .toBeInTheDocument();
  });

  it('renders project data and user-facing state labels', async () => {
    renderProject(<ProjectListPage />, {
      get: vi.fn(async () => ({ data: [project] })),
    });
    expect(await screen.findByRole('link', { name: '실제 프로젝트' })).toBeInTheDocument();
    expect(screen.getByText('작성 중')).toBeInTheDocument();
    expect(screen.getByText('문서 등록')).toBeInTheDocument();
  });

  it('renders a retryable project load error', async () => {
    renderProject(<ProjectListPage />, {
      get: vi.fn(async () => { throw new ApiError({ code: 'NETWORK_ERROR' }); }),
    });
    expect(await screen.findByRole('heading', { name: '프로젝트를 불러오지 못했습니다' }))
      .toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });

  it('validates project title before create', () => {
    const client = { post: vi.fn() };
    renderProject(<ProjectCreatePage />, client, '/projects/new');
    fireEvent.submit(screen.getByRole('button', { name: '프로젝트 만들기' }).closest('form'));
    expect(screen.getByText('프로젝트 이름을 입력해 주세요.')).toBeInTheDocument();
    expect(client.post).not.toHaveBeenCalled();
  });

  it('creates a project and navigates to its URL identity', async () => {
    const client = { post: vi.fn(async () => ({ data: project })) };
    renderProject(<ProjectCreatePage />, client, '/projects/new');
    fireEvent.change(document.getElementById('project-title'), {
      target: { value: '실제 프로젝트' },
    });
    fireEvent.submit(screen.getByRole('button', { name: '프로젝트 만들기' }).closest('form'));
    expect(await screen.findByRole('heading', { name: '프로젝트 상세 도착' })).toBeInTheDocument();
    expect(client.post).toHaveBeenCalledWith('/projects', {
      title: '실제 프로젝트',
      description: null,
      industryCategory: null,
    });
  });
});
