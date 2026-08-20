import { describe, expect, it } from 'vitest';

import { safeReturnToForRole } from './safeReturnTo.js';

describe('safeReturnToForRole', () => {
  it('sends a regular user home instead of returning to an admin route', () => {
    expect(safeReturnToForRole('/admin', 'USER')).toBe('/app');
    expect(safeReturnToForRole('/admin/users?page=0', 'USER')).toBe('/app');
  });

  it('keeps valid user and administrator destinations', () => {
    expect(safeReturnToForRole('/app/projects/2/overview', 'USER')).toBe('/app/projects/2/overview');
    expect(safeReturnToForRole('/admin/projects', 'ADMIN')).toBe('/admin/projects');
  });
});
