import { existsSync, readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routerSource = readFileSync('src/app/routing/AppRouter.jsx', 'utf8');

describe('project route cutover', () => {
  it('uses one canonical Business Proposal Workspace for both compatible routes', () => {
    expect(routerSource).toContain('path="concepts" element={<BusinessProposalWorkspace />}');
    expect(routerSource).toContain('path="concepts/compare" element={<BusinessProposalWorkspace initialMode="compare" />}');
    expect(routerSource).toContain('path="concepts/legal-report" element={<LegalRegulatoryReportPage />}');
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

  it('출시 준비의 canonical 및 호환 경로를 하나의 화면으로 연결한다', () => {
    expect(routerSource).toContain('path="launch-readiness" element={<LaunchReadinessPage />}');
    expect(routerSource).toContain('path="launch-readiness/reports/:reportType" element={<LaunchReadinessReportPage />}');
    expect(routerSource).toContain('path="technology" element={<LaunchReadinessPage initialFocus="technology" />}');
    expect(routerSource).toContain('path="operations" element={<LaunchReadinessPage initialFocus="operations" />}');
    expect(routerSource).toContain('path="tech-ops" element={<TechOpsPage />}');
    expect(routerSource).toContain('path="finance" element={<FinancePage />}');
  });

  it('사업 검증의 세 화면을 각각 제 주소에 세운다', () => {
    expect(routerSource).toContain('path="market" element={<MarketResearchPage />}');
    expect(routerSource).toContain('path="business-model" element={<BmCanvasPage />}');
    expect(routerSource).toContain('path="concept-refinement" element={<ConceptRefinementPage />}');
  });
});
