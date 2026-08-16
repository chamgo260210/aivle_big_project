const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
// ⚠ **컨셉 다듬기만 v2 다.** `ConceptRefinementController:24` 가 `/api/v2` 에 매핑돼 있고,
//    전역 prefix 설정은 없다(실측). 여기가 v3 이던 동안 이 넷은 **전부 404 였다** —
//    부르는 화면이 라우트에 안 붙어 있어서 아무도 못 봤다. 서버 계약을 바꾸는 대신
//    부르는 쪽을 맞춘다. 이 넷을 부르는 곳은 프론트 한 군데뿐이다.
const refinementBase = (projectId) => `/api/v2/projects/${encodeURIComponent(projectId)}`;
const key = () => globalThis.crypto?.randomUUID?.() ?? `market-${Date.now()}-${Math.random()}`;

export function createMarketApi(client, projectId) {
  const root = base(projectId);
  const refineRoot = refinementBase(projectId);
  return {
    // 시장조사(1단계) · BM 캔버스(2단계) 둘 다 **202 로 즉시 돌아오고**
    // Project SSE가 canonical current 재조회를 유도한다.
    // 1단계는 90~266초라 동기로 받을 방법이 없다. timeoutMs 는 **enqueue 응답**에만 걸린다.
    async startMarketResearch(asOf) {
      return (await client.post(`${root}/market-research`, { asOf },
        { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentMarketResearch() { return (await client.get(`${root}/market-research/current`)).data; },
    async recollectMarketResearch(sourceMarketResearchVersionId, options = {}) {
      return (await client.post(`${root}/market-research/recollect`, {
        sourceMarketResearchVersionId, asOf: options.asOf,
        slots: options.slots ?? '', from: options.from ?? 'a4',
        slotsFrom: options.slotsFrom ?? 'source',
      }, { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentCompetitorSeeds() { return (await client.get(`${root}/market-research/competitor-seeds`)).data; },
    async saveCompetitorSeeds(seeds) { return (await client.put(`${root}/market-research/competitor-seeds`, seeds)).data; },
    async startBusinessModel() {
      return (await client.post(`${root}/business-model`, {},
        { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentBusinessModel() { return (await client.get(`${root}/business-model/current`)).data; },

    // 사업 검증 — **한 번 눌러 두 걸음**(조사 → 캔버스). 실측 23분+ 이라 옛 둘보다도 길다.
    // 옛 두 실행도 남는다 — 걸음 하나만 다시 돌릴 길이 있어야 한다.
    async startBusinessValidation(asOf) {
      return (await client.post(`${root}/business-validation`, { asOf }, { timeoutMs: 30000 })).data;
    },
    async currentBusinessValidation() { return (await client.get(`${root}/business-validation/current`)).data; },

    // 다듬기 결과 — 변경 표와 「못 푼 것」. 라운드 이력 자체는 DB 에만 있다.
    // ⚠ 질의 파라미터는 **경로에 직접 붙인다.** `apiClient.request` 는 `params` 옵션이
    //    없어서 객체로 넘기면 조용히 버려지고, 서버가 400/500 으로 답한다(2026-08-13 실측:
    //    `MissingServletRequestParameterException: selectionId`). 아래 finalize 와 같은 모양이다.
    async currentRefinement(selectionId) {
      return (await client.get(
        `${refineRoot}/concept-refinement?selectionId=${encodeURIComponent(selectionId)}`)).data;
    },
    // **실패한 다듬기 라운드를 다시 건다.** 사용자가 눌러야만 돈다 — 자동 재시도는 없다.
    // 돌고 있거나·이미 됐거나·시도 상한(3)을 다 썼으면 서버가 409 로 거절한다.
    // ⚠ 위 currentRefinement 와 같은 이유로 selectionId 는 **쿼리 문자열**이다.
    async retryRefinement(selectionId) {
      return (await client.post(
        `${refineRoot}/concept-refinement/retry?selectionId=${encodeURIComponent(selectionId)}`,
        {}, { timeoutMs: 30000 })).data;
    },
    // **사람이 고른 것만 반영한다.** 이 단계의 정의 그 자체다 — 이 문이 생기기 전에는
    // AI 제안이 «전량 자동» 적용됐다(실측: 근거 0건 제안이 가격을 8,900 → 9,500원으로).
    // ⚠ `fieldKeys` 가 빈 배열이면 **「전부 넘김」**이다. 컨셉은 그대로 두고 루프가 끝난다.
    // ⚠ 한 라운드는 **한 번만** 받는다 — 두 번째는 서버가 거절한다.
    async decideRefinement(selectionId, round, fieldKeys, idempotencyKey) {
      return (await client.post(
        `${refineRoot}/concept-refinement/decide?selectionId=${encodeURIComponent(selectionId)}`,
        { round, fieldKeys, idempotencyKey }, { timeoutMs: 30000 })).data;
    },
    // 시장 검증 후 **최종 확정**. 법률보고서 재확정 → 시드 재발급을 순서대로 태운다.
    async finalizeRefinedConcept(selectionId, idempotencyKey) {
      return (await client.post(`${refineRoot}/concept-refinement/finalize?selectionId=${encodeURIComponent(selectionId)}`,
        { idempotencyKey }, { timeoutMs: 30000 })).data;
    },

    // 실행 계획 — BM 앞 단계에서 사용자가 채우는 칸. **실행과 따로 저장한다**:
    // 요청 바디에 실어 보내면 새로고침에 사라지고 감사 기록도 안 남는다.
    async currentBmPlan() { return (await client.get(`${root}/business-model/plan`)).data; },
    async saveBmPlan(plan, constraints) {
      return (await client.patch(`${root}/business-model/plan`, { plan, constraints })).data;
    },
  };
}
