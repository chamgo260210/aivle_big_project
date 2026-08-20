import { describe, expect, it } from 'vitest';

import {
  adminModuleLabel,
  adminStatusLabel,
  adminTargetTypeLabel,
  adminTaskTypeLabel,
  projectStatusLabel,
} from './adminLabels.js';
import { getAuditActionLabel } from './auditLabels.js';

describe('admin Korean labels', () => {
  it('translates account, project and pipeline status codes', () => {
    expect(adminStatusLabel('ADMIN')).toBe('관리자');
    expect(projectStatusLabel('ACTIVE')).toBe('진행 중');
    expect(adminModuleLabel('FINAL_REPORT')).toBe('최종 사업기획서');
  });

  it('translates AI task, audit and target codes', () => {
    expect(adminTaskTypeLabel('FINAL_BUSINESS_PROPOSAL_GENERATION')).toBe('최종 사업기획서 생성');
    expect(getAuditActionLabel('LOGIN_SUCCEEDED')).toBe('로그인 성공');
    expect(adminTargetTypeLabel('REFRESH_TOKEN')).toBe('로그인 세션');
  });

  it('keeps unknown operational codes visible for diagnosis', () => {
    expect(adminTaskTypeLabel('NEW_TASK_TYPE')).toBe('NEW_TASK_TYPE');
    expect(getAuditActionLabel('NEW_AUDIT_EVENT')).toBe('NEW_AUDIT_EVENT');
  });
});
