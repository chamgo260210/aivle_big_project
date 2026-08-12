const base = (projectId) => `/api/v2/projects/${encodeURIComponent(projectId)}`;

export function createTwinSurveyApi(client, projectId) {
  const root = base(projectId);
  return {
    // 202 로 즉시 돌아오고 화면이 current 를 폴링한다. n=300 이면 분 단위라 동기로 받을
    // 방법이 없다. timeoutMs 는 **enqueue 응답**에만 걸린다 — 조사 자체의 예산이 아니다.
    async startSurvey(situation, pairs, sampleSize) {
      return (await client.post(`${root}/twin-survey`, { situation, pairs, sampleSize },
        { timeoutMs: 30000 })).data;
    },
    async currentSurvey() { return (await client.get(`${root}/twin-survey/current`)).data; },
    // 자극 초안은 **동기 200** 이다 — 프롬프트 1회라 폴링할 것이 없다.
    // 서버 예산이 90초라 그보다 넉넉히 기다린다.
    // 컨셉은 **서버가 정한다** — 확정된 사업안(Market Seed)이 유일한 입력이다.
    // 예전에는 견본 이름표를 보냈고, 확정 전에 누르면 서버가 그 견본으로 조용히 떨어졌다.
    async draftStimulus() {
      return (await client.post(`${root}/twin-survey/stimulus-draft`, {},
        { timeoutMs: 120000 })).data;
    },
  };
}
