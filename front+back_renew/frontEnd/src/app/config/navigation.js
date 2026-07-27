export const globalNavigation = [
  { label: '대시보드', to: '/dashboard' },
  { label: '프로젝트', to: '/projects' },
  { label: '보고서', to: '/reports' },
  { label: '설정', to: '/settings' },
];

export function getProjectNavigation(projectId) {
  const root = `/projects/${projectId}`;
  return [
    { label: '개요', to: `${root}/overview` },
    { label: '문서', to: `${root}/documents` },
    { label: '구조화 결과', to: `${root}/structure` },
    { label: '법률 검토', to: `${root}/legal-review` },
    { label: '사업 타당성', to: `${root}/feasibility` },
    { label: '페르소나·고객 검증', to: `${root}/personas` },
    { label: '재무', to: `${root}/analyses/financial` },
    { label: 'AI 패널 조사', to: `${root}/panel-survey` },
    { label: 'AI 패널 토론', to: `${root}/panel-discussion` },
    { label: '통합 보고서', to: `${root}/report` },
    { label: '마케팅', to: `${root}/marketing` },
    { label: '프로젝트 설정', to: `${root}/settings` },
  ];
}
