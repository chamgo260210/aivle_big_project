/**
 * 시장 인터뷰 결과 정규화기.
 *
 * ⚠ **이 프로젝트에는 TypeScript 도 스키마 검증도 없다. 그래서 이 파일이 타입 시스템이다.**
 * 컴포넌트는 서버 JSON 을 직접 읽지 않고 여기를 거친다.
 *
 * 이 화면이 특히 위험한 이유: 「20명 중 7명」이 「35%」로, 다시 「시장의 35%」로 읽히기
 * 쉽다. 이 조사는 그것을 답하지 않는다. 그래서 두 가지를 여기서 강제한다 —
 *   ① **백분율을 만들지 않는다.** 뷰모델에 비율 칸이 없다. 막대 너비는 화면이 그 자리에서
 *      계산하고 값으로 쓰지 않는다
 *   ② `caveats` 가 비면 조용히 지나가지 않고 **큰 소리 나는 자리표시자**를 만든다
 */

/** 여섯 축 — 화면의 절 순서와 같다. 서버(`app/interview/models.py` 의 `AXES`)의 거울이다. */
export const AXIS_VIEW = Object.freeze([
  { axis: 'LIKE', title: '끌리는 점', tone: 'lead',
    empty: '끌린다고 말한 사람이 없다.' },
  { axis: 'CONCERN', title: '걸리는 점', tone: 'warn',
    empty: '걸린다고 말한 사람이 없다.' },
  { axis: 'DIFFERENTIATION', title: '무엇이 다른가', tone: 'neutral',
    empty: '다른 점을 말한 사람이 없다.' },
  { axis: 'USAGE_SCENE', title: '언제 쓸 것 같은가', tone: 'lead',
    empty: '쓸 장면을 말한 사람이 없다.' },
  { axis: 'BARRIER', title: '안 사는 이유', tone: 'trail',
    empty: '안 사겠다는 이유를 말한 사람이 없다.' },
  { axis: 'SUGGESTION', title: '바꿨으면 하는 것', tone: 'neutral',
    empty: '바꿨으면 하는 것을 말한 사람이 없다.' },
]);

/** 차별성 인식 3분류. **«비슷하다»가 다수인 것 자체가 핵심 경고다.** */
export const DIFFERENTIATION_VIEW = Object.freeze({
  different: { label: '다르다', tone: 'lead' },
  similar: { label: '비슷하다', tone: 'trail' },
  unclear: { label: '모르겠다', tone: 'warn' },
  unclassified: { label: '판정 못 함', tone: 'neutral' },
});

/**
 * 한 축에 한 번에 그릴 주제 수. 나머지는 접는다.
 *
 * ⚠ **잘라내는 것은 화면뿐이다** — 봉투와 계약은 36개를 그대로 담는다. 축이 6개로 늘어난
 * 뒤로 상한 없이 그리면 「나열식이라 정보가 없다」는 원래 문제로 되돌아간다.
 */
export const THEMES_VISIBLE = 5;

/** 이해도 3분류. **«오해»가 나쁜 결과가 아니라 «설명을 고치라»는 신호다.** */
export const COMPREHENSION_VIEW = Object.freeze({
  accurate: { label: '제대로 이해', tone: 'lead' },
  partial: { label: '반만 이해', tone: 'warn' },
  misunderstood: { label: '다른 물건으로 이해', tone: 'trail' },
  unclassified: { label: '판정 못 함', tone: 'neutral' },
});

/** 인터뷰 카드가 보여줄 9문항. 순서는 가이드 순서 그대로다 — 섞으면 답이 안 읽힌다. */
export const ANSWER_VIEW = Object.freeze([
  { key: 'firstImpression', label: '첫인상' },
  { key: 'restatement', label: '본인 말로' },
  { key: 'like', label: '끌리는 점' },
  { key: 'concern', label: '걸리는 점' },
  { key: 'differentiation', label: '무엇이 다른가' },
  { key: 'relevance', label: '필요성' },
  { key: 'usageScene', label: '언제 쓸까' },
  { key: 'barrier', label: '안 산다면' },
  { key: 'suggestion', label: '바꾼다면' },
]);

const CAVEATS_MISSING = Object.freeze([
  '⚠ 경계 문구가 결과에 실려오지 않았다. 이 결과를 인용하지 마라 — '
  + '값만 떼어 나가는 것을 막는 장치가 빠진 상태다.',
]);

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function asNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function text(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

/** 「20명 중 7명」. **여기서 백분율을 만들지 않는다** — 그 순간 크기 주장이 된다. */
export function mentionText(count, answered) {
  if (!Number.isFinite(count)) return '언급 수 없음';
  return answered > 0 ? `${answered}명 중 ${count}명` : `${count}명`;
}

/** 가격은 원 단위 정수이거나 «미정»이다. 화면이 값을 상상해 채우지 않는다. */
export function priceText(priceKrw) {
  return Number.isFinite(priceKrw) ? `${priceKrw.toLocaleString()}원` : '아직 정하지 않음';
}

/**
 * 컨셉보드를 응답자가 볼 문장으로. **`ai/app/interview/models.py` 의
 * `ConceptBoard.render()` 를 옮긴 것이다** — 실행 전에 「이렇게 보인다」를 보이려면
 * 화면이 같은 규칙을 알아야 한다.
 *
 * ⚠ **베낀 것은 갈라진다.** 그래서 골든 픽스처의 `conceptBoard.rendered` 와 이 함수의
 * 출력이 **바이트 동일**한지 `marketInterviewResult.test.js` 가 검사한다. 파이썬 쪽을
 * 고치고 여기를 안 고치면 그 테스트가 즉시 빨개진다.
 */
export function renderBoard(board) {
  const lines = [`이름: ${(board?.conceptName ?? '').trim()}`];
  const push = (label, value) => {
    const body = text(value);
    if (body) lines.push(`${label}: ${body}`);
  };
  push('누구를 위한 것인가', board?.targetUsers);
  push('어떤 상황의 문제인가', board?.problemScenario);
  const features = asArray(board?.featureSet).map(text).filter(Boolean);
  if (features.length > 0) {
    lines.push('하는 일:');
    features.forEach((feature) => lines.push(`  - ${feature}`));
  }
  push('다른 것과 다른 점', board?.differentiators);
  lines.push(Number.isFinite(board?.priceKrw)
    ? `가격: ${board.priceKrw.toLocaleString()}원`
    : '가격: 아직 정해지지 않았습니다');
  return lines.join('\n');
}

function normalizeBoard(raw) {
  return {
    conceptName: text(raw?.conceptName) ?? '이름 없는 사업안',
    targetUsers: text(raw?.targetUsers),
    problemScenario: text(raw?.problemScenario),
    featureSet: asArray(raw?.featureSet).map(text).filter(Boolean),
    differentiators: text(raw?.differentiators),
    priceKrw: asNumber(raw?.priceKrw),
    // 응답자가 실제로 본 문장. 이것을 못 보이면 답을 해석할 수 없다.
    rendered: text(raw?.rendered),
  };
}

function normalizeTheme(raw) {
  return {
    axis: raw?.axis ?? null,
    label: text(raw?.label) ?? '이름표 없음',
    mentionCount: asNumber(raw?.mentionCount) ?? 0,
    // 「그 걸림돌이 없어지면 사겠다」고 **말한** 사람 수. 추측이 아니라 발언이다.
    resolvedCount: asNumber(raw?.resolvedCount) ?? 0,
    quote: text(raw?.quote),
  };
}

function normalizeBucketed(raw) {
  return {
    axis: raw?.axis ?? null,
    label: text(raw?.label) ?? '이름표 없음',
    mentionCount: asNumber(raw?.mentionCount) ?? 0,
    breakdown: asArray(raw?.breakdown).map((dimension) => ({
      dimension: text(dimension?.dimension) ?? '축 없음',
      buckets: asArray(dimension?.buckets).map((bucket) => ({
        label: text(bucket?.label) ?? '이름표 없음',
        count: asNumber(bucket?.count) ?? 0,
      })),
    })),
  };
}

function normalizeInterview(raw, index) {
  const profile = raw?.profile ?? {};
  return {
    key: `${raw?.comprehension ?? 'unknown'}-${index}`,
    comprehension: COMPREHENSION_VIEW[raw?.comprehension] ? raw.comprehension : 'unclassified',
    profile: {
      age: asNumber(profile.age),
      gender: text(profile.gender),
      household: text(profile.household),
      region: text(profile.region),
      income: text(profile.income),
      job: text(profile.job),
    },
    answers: ANSWER_VIEW
      .map(({ key, label }) => ({ key, label, value: text(raw?.[key]) }))
      .filter((item) => item.value),
  };
}

/** 카드 머리 두 줄. 못 읽은 칸은 그냥 빠진다 — 「알 수 없음」으로 채우지 않는다. */
export function profileLines(profile) {
  const head = [
    profile?.age === null || profile?.age === undefined ? null : `${profile.age}세`,
    profile?.gender, profile?.household, profile?.region,
  ].filter(Boolean).join(' · ');
  const sub = [profile?.income, profile?.job].filter(Boolean).join(' · ');
  return { head, sub };
}

export function normalizeMarketInterview(raw) {
  if (!raw || typeof raw !== 'object') return null;

  const answered = asNumber(raw.telemetry?.answered) ?? 0;
  const themes = asArray(raw.themes).map(normalizeTheme);
  const caveats = asArray(raw.caveats).filter((note) => typeof note === 'string' && note.trim());
  const comprehension = raw.comprehension ?? {};
  const shortCells = Object.entries(raw.sampling?.shortCells ?? {})
    .map(([cell, detail]) => ({ cell, ...detail }));

  return {
    board: normalizeBoard(raw.conceptBoard),
    sampleSize: asNumber(raw.sampleSize) ?? 0,
    // ⚠ **분모는 sampleSize 가 아니라 answered 다.** 뽑은 사람과 답한 사람은 다르고,
    //    형식 위반·타임아웃으로 빠진 사람을 분모에 넣으면 언급 수가 조용히 작아 보인다.
    answered,
    sampling: {
      requested: asNumber(raw.sampling?.requested) ?? 0,
      drawn: asNumber(raw.sampling?.drawn) ?? 0,
      shortCells,
      hasShortCells: shortCells.length > 0,
    },
    comprehension: {
      accurate: asNumber(comprehension.accurate) ?? 0,
      partial: asNumber(comprehension.partial) ?? 0,
      misunderstood: asNumber(comprehension.misunderstood) ?? 0,
      unclassified: asNumber(comprehension.unclassified) ?? 0,
      misreadPoints: asArray(comprehension.misreadPoints).map(text).filter(Boolean),
    },
    differentiation: {
      different: asNumber(raw.differentiation?.different) ?? 0,
      similar: asNumber(raw.differentiation?.similar) ?? 0,
      unclear: asNumber(raw.differentiation?.unclear) ?? 0,
      unclassified: asNumber(raw.differentiation?.unclassified) ?? 0,
    },
    targeting: {
      criteriaText: text(raw.targeting?.criteriaText) ?? '조건을 읽지 못했다',
      targetDrawn: asNumber(raw.targeting?.targetDrawn) ?? 0,
      nonTargetDrawn: asNumber(raw.targeting?.nonTargetDrawn) ?? 0,
      shortfall: asNumber(raw.targeting?.shortfall) ?? 0,
    },
    sections: AXIS_VIEW.map((view) => {
      const rows = themes.filter((theme) => theme.axis === view.axis);
      // 상한은 화면에만 건다. 접힌 것도 개수를 밝혀 「다 보여줬다」로 읽히지 않게 한다.
      return { ...view, themes: rows.slice(0, THEMES_VISIBLE),
        hiddenThemes: rows.slice(THEMES_VISIBLE) };
    }),
    alternatives: asArray(raw.alternatives).map((item) => ({
      label: text(item?.label) ?? '이름표 없음',
      mentionCount: asNumber(item?.mentionCount) ?? 0,
    })),
    segments: asArray(raw.segments).map(normalizeBucketed),
    contrast: asArray(raw.contrast).map((row) => ({
      axis: row?.axis ?? null,
      label: text(row?.label) ?? '이름표 없음',
      targetCount: asNumber(row?.targetCount) ?? 0,
      nonTargetCount: asNumber(row?.nonTargetCount) ?? 0,
    })),
    suggestionLinks: asArray(raw.suggestionLinks).map((row) => ({
      label: text(row?.label) ?? '이름표 없음',
      mentionCount: asNumber(row?.mentionCount) ?? 0,
      links: asArray(row?.links).map((link) => ({
        axis: link?.axis ?? null,
        label: text(link?.label) ?? '이름표 없음',
        overlapCount: asNumber(link?.overlapCount) ?? 0,
      })),
    })),
    interviews: asArray(raw.interviews)
      .map(normalizeInterview)
      .filter((card) => card.answers.length > 0),
    transcripts: asArray(raw.transcripts)
      .map((row, index) => ({ ...normalizeInterview(row, index),
        id: text(row?.id) ?? `R${index + 1}`, target: row?.target === true }))
      .filter((row) => row.answers.length > 0),
    // 포화 — 「전원이 같은 말을 했다」. 자극이 한 속성에 쏠렸거나 분산이 소실된 것이고,
    // 어느 쪽이든 그 축은 읽으면 안 된다. 조용히 지나가지 않게 화면 위로 올린다.
    saturatedThemes: asArray(raw.telemetry?.homogeneity?.saturatedThemes)
      .map(text).filter(Boolean),
    telemetry: raw.telemetry ?? {},
    notes: asArray(raw.notes),
    // 비어 있으면 자리표시자를 넣는다. 빈 배열로 두면 화면에 아무것도 안 나오고,
    // 그러면 경계 없는 결과가 그대로 읽힌다 — 이 장치가 없애려던 실패 그 자체다.
    caveats: caveats.length > 0 ? caveats : CAVEATS_MISSING,
    caveatsMissing: caveats.length === 0,
  };
}
