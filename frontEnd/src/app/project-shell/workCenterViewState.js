/**
 * 작업 센터 시트의 다음 표시 상태를 만든다.
 *
 * ⚠ 컴포넌트 파일 밖에 둔다 — `ProjectLayout.jsx` 안에 두면 컴포넌트가 아닌 것을 내보내게 되어
 *   Fast Refresh 가 그 파일 전체를 잃는다(`react-refresh/only-export-components`).
 */
export function workCenterViewState(current, focusJobId = null) {
  const view = focusJobId ? 'detail' : 'list';
  if (current.mounted) return { ...current, view, focusJobId,
    direction: view === 'detail' ? 'forward' : 'backward' };
  return { mounted: true, phase: 'opening', view, focusJobId, direction: 'forward' };
}
