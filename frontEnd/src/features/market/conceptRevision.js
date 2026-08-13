/**
 * 화면 2 「다듬어진 컨셉」이 읽는 <b>정규화층</b>.
 *
 * <p>세 곳에서 온 봉투를 화면이 직접 파헤치지 않게 여기서 한 모양으로 만든다:
 * 다듬기 결과(`/concept-refinement`) · 법률 보고서(`legal-regulatory-report/current`) ·
 * 컨셉 원문(`market-seed/current`).
 *
 * <p>⚠ <b>없는 것을 만들지 않는다.</b> 배열은 없을 때 `[]` 로 떨어뜨리고, 데이터에 없는
 * 판정은 <b>배지를 달지 않는다</b>. 이 화면은 「무엇이 왜 바뀌었나」를 말하는 자리라
 * 지어낸 한 칸이 곧 지어낸 근거가 된다.
 */

/**
 * 다듬기가 건드릴 수 있는 칸 → 사람이 읽는 이름.
 *
 * <p>정본은 AI 쪽 `app/validation/drift.py` 의 `REFINABLE_FIELDS` ·
 * `FREE_WITH_EVIDENCE_FIELDS` · `FREE_BM_FIELDS` 다. 모르는 키는 <b>키 그대로</b> 보인다 —
 * 숨기면 화면이 「무엇이 바뀌었는지」를 잃는다.
 */
export const REVISION_FIELD_LABEL = {
  price: '가격',
  channels: '판매 채널',
  differentiators: '차별점',
  targetRegion: '대상 지역',
  targetUsers: '대상 고객',
  featureSet: '핵심 기능',
  revenueModel: '수익 모델',
  preMarketSomShare: '목표 점유율',
  preMarketSom: '목표 매출',
  keyActivities: '핵심 활동',
  keyResources: '핵심 자원',
  keyPartners: '핵심 파트너',
  customerRelationships: '고객 관계',
};

/**
 * 컨셉 원문으로 그릴 칸 — `marketSeed.snapshot.selectedConcept` 의 실제 필드다.
 *
 * <p>⚠ <b>문장을 이어 붙이지 않는다.</b> 목업은 한 문단으로 흐르지만 우리 데이터는
 * 이름 붙은 칸이다. 칸을 접착제 문장으로 이으면 그 접착제는 <b>아무도 쓴 적 없는 말</b>이 된다.
 */
const CONCEPT_DOC_FIELDS = [
  ['identity', 'conceptDefinition', '한 줄 정의'],
  ['identity', 'introduction', '소개'],
  ['identity', 'coreValue', '핵심 가치'],
  ['identity', 'targetUsers', '대상 고객'],
  ['solution', 'problemScenario', '해결할 문제'],
  ['solution', 'solutionMechanism', '제공 방식'],
  ['solution', 'featureSet', '핵심 기능'],
];

const list = (value) => (Array.isArray(value) ? value : []);

/** 목록은 ` · ` 로 잇는다. 값을 바꾸지 않는 표기일 뿐이다. */
function paragraph(value) {
  if (Array.isArray(value)) return value.filter(Boolean).map(String).join(' · ');
  return typeof value === 'string' ? value : '';
}

/**
 * 컨셉 원문 문단 목록. 스냅샷이 없거나 칸이 비면 <b>그 칸을 안 그린다</b> —
 * 빈 문단을 세우면 「조사가 비었다」로 읽힌다.
 */
export function conceptDocument(snapshot) {
  const selected = snapshot?.selectedConcept;
  if (!selected || typeof selected !== 'object') return [];
  const name = selected.identity?.conceptName;
  const blocks = CONCEPT_DOC_FIELDS
    .map(([group, key, label]) => ({ key, label, text: paragraph(selected[group]?.[key]) }))
    .filter((block) => block.text);
  return typeof name === 'string' && name ? [{ key: 'conceptName', label: '사업안 이름', text: name }, ...blocks] : blocks;
}

/**
 * 바뀐 구간에 <b>초록 표시를 입힐 자리</b>를 찾는다 — 순수 함수다.
 *
 * <p>`changes[i].after` 문자열을 원문에서 그대로 찾아 감싼다. <b>못 찾는 것이 정상</b>이다:
 * 다듬기가 건드리는 것은 가설(가격·채널 …)이라 컨셉 서술문에 그 문자열이 없을 수 있다.
 * 그때는 조각을 만들지 않고 원문만 그대로 돌려준다 — <b>원문을 짜깁기하지 않는다.</b>
 *
 * <p>겹치는 자리는 앞선 것만 남긴다. 한 글자짜리 값은 아무 데나 걸리므로 버린다.
 *
 * @returns `[{ text, ref }]` — `ref` 는 1부터 세는 변경 번호이고, 안 바뀐 구간은 `null`.
 */
export function highlightChanges(text, changes) {
  if (typeof text !== 'string' || !text) return [];
  const marks = [];
  list(changes).forEach((change, index) => {
    const needle = typeof change?.after === 'string' ? change.after.trim() : '';
    if (needle.length < 2) return;
    const at = text.indexOf(needle);
    if (at < 0) return;
    marks.push({ at, end: at + needle.length, ref: index + 1 });
  });

  marks.sort((left, right) => left.at - right.at);
  const parts = [];
  let cursor = 0;
  for (const mark of marks) {
    if (mark.at < cursor) continue;
    if (mark.at > cursor) parts.push({ text: text.slice(cursor, mark.at), ref: null });
    parts.push({ text: text.slice(mark.at, mark.end), ref: mark.ref });
    cursor = mark.end;
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor), ref: null });
  return parts;
}

/**
 * 최종 컨셉 <b>서술문</b> 조각. 없으면 `null` 이고, 화면은 {@link conceptDocument} 로 폴백한다.
 *
 * <p>⚠ <b>없는 것을 짓지 않는다.</b> 서버는 검증을 통과한 서술문만 준다 — 못 통과하면 아예
 * 안 준다. 여기서 「조각이 하나뿐이면 대충 이어 붙이자」 같은 보정을 하면 그 보정이 곧
 * 아무도 쓴 적 없는 문장이 된다.
 *
 * @returns `[{ text, ref }]` — `highlightChanges` 와 <b>같은 모양</b>이라 화면이 한 갈래로 그린다.
 */
export function narrativeParts(narrative) {
  const parts = list(narrative)
    .map((segment) => ({
      text: typeof segment?.text === 'string' ? segment.text : '',
      ref: Number.isInteger(segment?.changeRef) ? segment.changeRef : null,
    }))
    .filter((part) => part.text);
  return parts.length > 0 ? parts : null;
}

/**
 * 조항이 이 컨셉에 어떤 상태인가 — <b>세 갈래뿐</b>이다.
 *
 * <p>`REFLECTED` 는 AI 가 아니라 서버가 찍는다(제안의 `legalRef` 와 대조). 나머지 둘은
 * 검토 결과에서 결정론으로 나온다. <b>모르는 값에는 배지를 안 단다</b> — 없는 판정을 그리면
 * 그것이 곧 근거 없는 「확인됨」이다.
 */
export const CLAUSE_STATUS_VIEW = {
  REFLECTED: { label: '컨셉에 반영했어요', tone: 'success' },
  NEEDS_CHECK: { label: '직접 확인이 필요해요', tone: 'warning' },
  OK: { label: '문제 없어요', tone: 'neutral' },
};

/** 법률 판정 → 사람이 읽는 말. 정본은 AI 쪽 `concept_portfolio_v2/adapters.py` 의 productionStatus. */
export const LEGAL_STATUS_VIEW = {
  IMPLEMENTABLE: { label: '추가 통제 없이 팔 수 있어요', tone: 'success' },
  IMPLEMENTABLE_WITH_CONTROLS: { label: '통제를 지키면 팔 수 있어요', tone: 'success' },
  NEEDS_FACTS: { label: '직접 확인이 필요해요', tone: 'warning' },
  REDESIGNABLE: { label: '구조를 고쳐야 해요', tone: 'warning' },
  REJECTED: { label: '이대로는 팔 수 없어요', tone: 'danger' },
};

/** 가설 종류 → 사람이 읽는 말. 「무엇이 바뀌어서 다시 봤나」를 이 말로 적는다. */
const HYPOTHESIS_LABEL = {
  PRICE: '가격', CHANNELS: '판매 채널', TARGET_USERS: '대상 고객',
  FEATURE_SET: '핵심 기능', REVENUE_MODEL: '수익 모델',
};

/**
 * <b>델타 법률 검토</b>를 화면 모양으로 — 다듬기가 바꾼 것에 걸리는 법만.
 *
 * <p>⚠ <b>전체 법률보고서를 여기에 쓰지 마라.</b> 그것은 컨셉 전체를 훑은 결과라 조항이
 * 8건씩 늘어서 「이번에 무엇이 걸렸나」가 묻힌다(2026-08-13 실측: 사용자가 「왤케 많아」로
 * 반려했다). 다듬기는 바뀐 가설(1~5종)만 다시 태우고, 서버는 그 결과만
 * {@code /concept-refinement} 의 `deltaLegal` 로 준다.
 *
 * <p>⚠ <b>조항의 법 조문 해설(`title`·`boundedProvisionSummary`)은 싣지 않는다.</b> 그것은
 * 「법이 무엇을 정하는가」이지 「이 컨셉이 왜 걸리는가」가 아니다 — 사용자가 그것을 두고
 * 「뭐 법 설명하냐」고 반려했다. 대신 검토가 조항마다 이어 둔 <b>소견</b>(`findings`)을 싣는다.
 * 그것이 「이 컨셉이 왜 걸리는가」다.
 *
 * <p>⚠ 조항에 안 이어진 갈래 목록(「지켜야 할 통제」 …)은 여전히 싣지 않는다. 어느 법에
 * 걸리는지 모르는 채로 나열하면 같은 이유로 반려된다.
 */
export function normalizeDeltaLegal(deltaLegal) {
  const review = deltaLegal?.legalReview;
  if (!review || typeof review !== 'object') return null;

  const clauses = list(review.officialEvidenceReferences).map((item, index) => ({
    key: `${item?.officialIdentifier ?? item?.lawName ?? 'law'}-${index}`,
    lawName: item?.lawName ?? '법령 이름 없음',
    article: item?.articleReference ?? null,
    url: typeof item?.officialSourceUri === 'string' ? item.officialSourceUri : null,
    // 모르는 값이면 배지를 안 단다 — 없는 판정을 그리는 것이 곧 지어낸 근거다.
    status: CLAUSE_STATUS_VIEW[item?.conceptStatus] ?? null,
    findings: list(item?.findings)
      .map((finding) => ({
        topic: typeof finding?.topic === 'string' ? finding.topic : '',
        text: typeof finding?.text === 'string' ? finding.text : '',
      }))
      .filter((finding) => finding.text),
  }));

  return {
    status: LEGAL_STATUS_VIEW[review.productionStatus] ?? null,
    approved: deltaLegal.approved === true,
    // 무엇이 바뀌어서 다시 봤나 — 이것이 「부분 검사」임을 화면이 말하는 유일한 자리다.
    changed: list(deltaLegal.hypothesisTypes)
      .map((type) => HYPOTHESIS_LABEL[type] ?? type)
      .filter(Boolean),
    clauses,
  };
}
