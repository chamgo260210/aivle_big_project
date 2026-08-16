import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from '../../market/marketApi.js';
import { draftFrom, emptyCellNames, emptyDraft, toPayload } from '../bmPlan.js';

/**
 * BM 실행 계획 초안 — <b>컨셉 단계에서</b> 받는다.
 *
 * <p>저장소는 그대로다. `PATCH /business-model/plan` 도 `bm_plan_preparations` 도 안 바꿨다.
 * 바뀐 것은 <b>누가 언제 채우느냐</b>뿐이다 — 예전에는 BM 화면 앞에서 물었고 지금은
 * 가설을 굳히는 자리에서 같이 묻는다.
 *
 * <p>초안을 실행 요청 바디에 싣지 않고 <b>따로 저장</b>한다. 그래야 새로고침에 안 사라지고
 * 「어느 계획으로 돌렸나」가 감사 기록에 남는다.
 */
export function useBmPlanDraft(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);
  const [draft, setDraft] = useState(emptyDraft);

  useEffect(() => {
    let alive = true;
    api.currentBmPlan()
      .then((payload) => { if (alive) setDraft(draftFrom(payload)); })
      // 초안을 못 읽는 것은 가설 확정을 막을 일이 아니다 — 빈 폼으로 연다.
      .catch(() => {});
    return () => { alive = false; };
  }, [api]);

  const change = useCallback((key, value) => {
    setDraft((prev) => ({ ...prev, [key]: value }));
  }, []);

  const save = useCallback(async () => {
    const { plan, constraints } = toPayload(draft);
    await api.saveBmPlan(plan, constraints);
  }, [api, draft]);

  return { draft, change, save, emptyCells: emptyCellNames(draft) };
}
