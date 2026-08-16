import { existsSync, readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routerSource = readFileSync('src/app/routing/AppRouter.jsx', 'utf8');

describe('project route cutover', () => {
  it('uses one canonical Business Proposal Workspace for both compatible routes', () => {
    expect(routerSource).toContain('path="concepts" element={<BusinessProposalWorkspace />}');
    expect(routerSource).toContain('path="concepts/compare" element={<BusinessProposalWorkspace initialMode="compare" />}');
    expect(routerSource).not.toContain('ConceptFactoryPage');
    expect(routerSource).not.toContain('ConceptComparisonPage');
  });

  it('routes journey 4 to the Market Interview page', () => {
    expect(routerSource).toContain('path="market-interview" element={<MarketInterviewPage />}');
    expect(routerSource).not.toContain('TwinSurveyPage');
  });

  it('keeps legacy source files without exposing them through official routes', () => {
    expect(existsSync('src/features/concept-factory/pages/ConceptFactoryPage.jsx')).toBe(true);
    expect(existsSync('src/features/concept-selection/pages/ConceptComparisonPage.jsx')).toBe(true);
    expect(existsSync('src/features/twin-survey/TwinSurveyPage.jsx')).toBe(true);
  });
});
