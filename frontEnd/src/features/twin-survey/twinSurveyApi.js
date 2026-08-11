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
    // conceptId 는 **견본 컨셉 이름표**다. 확정된 컨셉이 있으면 서버가 그것을 쓴다.
    async draftStimulus(conceptId) {
      return (await client.post(`${root}/twin-survey/stimulus-draft`, { conceptId },
        { timeoutMs: 120000 })).data;
    },
  };
}
