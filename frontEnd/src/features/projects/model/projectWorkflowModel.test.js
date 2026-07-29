import { describe, expect, it } from 'vitest';

import {
  PROJECT_AREAS,
  getAreaSummary,
  getProjectArea,
  getProjectNextAction,
  getProjectProgress,
  getProjectStatusView,
} from './projectWorkflowModel.js';

const project = {
  projectId: '12',
  status: 'ACTIVE',
  stage: 'LEGAL_REVIEW',
};

describe('project workflow model', () => {
  it('maps the durable project stage to one product area and a canonical next route', () => {
    expect(getProjectArea(project)).toBe(PROJECT_AREAS.REVIEW);
    expect(getProjectNextAction(project).route).toBe('/app/projects/12/review/legal');
  });

  it('keeps project status separate from unavailable task-level status', () => {
    expect(getAreaSummary(project)[1].taskStatus).toBe('UNKNOWN');
    expect(getProjectStatusView('ACTIVE').label).toBe('진행 중');
  });

  it('provides safe unknown fallbacks and bounded progress', () => {
    expect(getProjectStatusView('UNRECOGNIZED').label).toBe('상태 확인 필요');
    expect(getProjectProgress({ stage: 'UNRECOGNIZED' })).toBe(25);
  });
});
