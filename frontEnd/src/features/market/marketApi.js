const base = (projectId) => `/api/v2/projects/${encodeURIComponent(projectId)}`;

export function createMarketApi(client, projectId) {
  const root = base(projectId);
  return {
    // 시장조사(1단계) · BM 캔버스(2단계) 둘 다 **202 로 즉시 돌아오고** 화면이 current 를 폴링한다.
    // 1단계는 90~266초라 동기로 받을 방법이 없다. timeoutMs 는 **enqueue 응답**에만 걸린다.
    async startMarketResearch(conceptId, asOf, concept) {
      return (await client.post(`${root}/market-research`, { conceptId, asOf, concept }, { timeoutMs: 30000 })).data;
    },
    async currentMarketResearch() { return (await client.get(`${root}/market-research/current`)).data; },
    async startBusinessModel(conceptId, asOf) {
      return (await client.post(`${root}/business-model`, { conceptId, asOf }, { timeoutMs: 30000 })).data;
    },
    async currentBusinessModel() { return (await client.get(`${root}/business-model/current`)).data; },

    // 실행 계획 — BM 앞 단계에서 사용자가 채우는 칸. **실행과 따로 저장한다**:
    // 요청 바디에 실어 보내면 새로고침에 사라지고 감사 기록도 안 남는다.
    async currentBmPlan() { return (await client.get(`${root}/business-model/plan`)).data; },
    async saveBmPlan(plan, constraints) {
      return (await client.patch(`${root}/business-model/plan`, { plan, constraints })).data;
    },

    // 경쟁 씨앗 — 슬롯 하네스가 F_COMP 슬롯의 subject 를 여기서 가져온다.
    // 비워 두면 모델이 실명을 지어내거나 자리표시자를 만든다(2026-08-08 실측).
    // ⚠ **통째로 갈아 끼운다.** 순서가 값이라 한 줄씩 고치는 길을 만들지 않는다.
    async currentCompetitorSeeds() { return (await client.get(`${root}/competitor-seeds`)).data; },
    async saveCompetitorSeeds(seeds) {
      return (await client.put(`${root}/competitor-seeds`, seeds)).data;
    },
  };
}
