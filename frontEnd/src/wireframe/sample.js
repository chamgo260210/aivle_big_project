/**
 * 와이어프레임 예시 데이터 — **창작이다. 실제 조사 결과가 아니다.**
 *
 * <p>주제를 하나로 묶으려고 지어냈다: 「1인 가구 프리미엄 냉동 간편식」.
 * 골든 픽스처(미용실 노쇼 견본)를 쓰면 화면 1의 숫자와 화면 2의 컨셉 문장이 서로 다른
 * 사업 이야기를 해서 배치를 못 읽는다.
 *
 * <p>모양은 서버 봉투 그대로다 — `normalizeMarketResult()` 를 그대로 통과하므로
 * 화면 부품은 제품과 똑같이 동작한다. <b>값만</b> 가짜다.
 */

/** 화면 1 — 사업 검증. 서버 result 봉투 모양. */
export const SAMPLE_RESULT = {
  runId: 'wireframe-sample',
  conceptId: 'hmr-solo',
  asOf: '2026-06-12',
  mode: 'BM',
  evidence: [
    {
      id: 'C-F001', section: 'DEMAND', kind: '관측', subject: '1인 가구', metric: '가구 수',
      value: 7830000, unit: '가구', period: '2024', grade: '확정',
      sourceUrl: 'https://kosis.kr/example', sourceKind: 'gov_stat',
      quote: '2024년 1인 가구는 783만 가구로, 전체 가구의 35.5%예요.',
      caveats: [],
    },
    {
      id: 'C-F002', section: 'MARKET_SIZE', kind: '관측', subject: '가정간편식(HMR)', metric: '시장 규모',
      value: 5200000000000, unit: 'KRW', period: '2024', grade: '실무 신뢰',
      sourceUrl: 'https://atfis.or.kr/example', sourceKind: 'public_filing',
      caveats: ['**냉동 정찬만이 아니라 즉석밥·컵밥까지 포함한 전체 HMR 수치예요.**'],
    },
    {
      id: 'C-F003', section: 'GROWTH', kind: '관측', subject: '냉동 간편식', metric: '연 성장률',
      value: 7.1, unit: 'PERCENT_PER_YEAR', period: '2023→2024', grade: '확정',
      sourceUrl: 'https://kosis.kr/example-growth', sourceKind: 'gov_stat',
      caveats: ['단순 증감률이에요. 연평균 성장률(CAGR)이 아니에요.'],
    },
    {
      id: 'C-F004', section: 'PRICE', kind: '관측', subject: 'A사 냉동 도시락', metric: '표시가격',
      value: 8900, unit: 'KRW', period: '2026-06', grade: '확정',
      sourceUrl: 'https://example-a.co.kr/menu', sourceKind: 'official_page',
      caveats: [],
    },
    {
      id: 'C-F005', section: 'PRICE', kind: '관측', subject: 'B사 프리미엄 밀키트', metric: '표시가격',
      value: 12000, unit: 'KRW', period: '2026-06', grade: '확정',
      sourceUrl: 'https://example-b.co.kr/menu', sourceKind: 'official_page',
      caveats: [],
    },
    {
      id: 'C-F006', section: 'DEMAND', kind: '관측', subject: '1인 가구', metric: '「간편식」 검색량 증가율',
      value: 34, unit: '%', period: '2025→2026', grade: '추정',
      sourceUrl: 'https://example-news.co.kr/article', sourceKind: 'news',
      quote: '1인 가구의 냉동 간편식 검색이 1년 새 34% 늘었어요.',
      caveats: ['**언론이 인용한 민간 조사예요 — 원자료는 확인하지 못했어요.**'],
    },
    // ── 판 ㊸ — 절 체인이 채우는 세 과목. **표를 안 찢는 것**을 보이려고
    //    채널 4행을 같은 `tableKey` 로 묶고, 합이 100%가 아니게 뒀다(경고가 뜬다).
    {
      id: 'sec-0001', section: 'CHANNEL', kind: '관측', subject: '대형마트 매출액 — 비중',
      metric: '매출처별 판매비중', value: 31.05, unit: '%', period: '2025', grade: '확정',
      raw: '31.05%', issuer: '오뚜기', placement: 'COMPETITOR_FIRM',
      tableKey: 'T-01|매출처별 판매비중|2025',
      sourceUrl: 'https://kind.krx.co.kr/example', sourceKind: 'public_filing',
      caveats: ['⚠ 시장 전체가 아니라 **오뚜기 한 회사**의 매출처 구성비예요.'],
    },
    {
      id: 'sec-0002', section: 'CHANNEL', kind: '관측', subject: '특약점 매출액 — 비중',
      metric: '매출처별 판매비중', value: 29.65, unit: '%', period: '2025', grade: '확정',
      raw: '29.65%', issuer: '오뚜기', placement: 'COMPETITOR_FIRM',
      tableKey: 'T-01|매출처별 판매비중|2025',
      sourceUrl: 'https://kind.krx.co.kr/example', sourceKind: 'public_filing',
      caveats: [],
    },
    {
      id: 'sec-0003', section: 'CHANNEL', kind: '관측', subject: '대리점 매출액 — 비중',
      metric: '매출처별 판매비중', value: 10.21, unit: '%', period: '2025', grade: '확정',
      raw: '10.21%', issuer: '오뚜기', placement: 'COMPETITOR_FIRM',
      tableKey: 'T-01|매출처별 판매비중|2025',
      sourceUrl: 'https://kind.krx.co.kr/example', sourceKind: 'public_filing',
      caveats: [],
    },
    {
      id: 'sec-0004', section: 'CHANNEL', kind: '관측', subject: '편의점 매출액 — 비중',
      metric: '매출처별 판매비중', value: 5.99, unit: '%', period: '2025', grade: '확정',
      raw: '5.99%', issuer: '오뚜기', placement: 'COMPETITOR_FIRM',
      tableKey: 'T-01|매출처별 판매비중|2025',
      sourceUrl: 'https://kind.krx.co.kr/example', sourceKind: 'public_filing',
      caveats: [],
    },
    {
      id: 'sec-0005', section: 'UNIT_ECONOMICS', kind: '관측', subject: '냉동식품 판매단가',
      metric: '판매단가', value: 6513, unit: '원', period: '2025', grade: '확정',
      raw: '6,513원', issuer: '오뚜기', placement: 'COMPETITOR_FIRM', tableKey: null,
      sourceUrl: 'https://kind.krx.co.kr/example-price', sourceKind: 'public_filing',
      quote: '냉동식품 판매단가는 6,513원이에요.',
      caveats: ['⚠ 시장 전체가 아니라 **오뚜기 한 회사**의 수예요.'],
    },
    {
      id: 'C-F007', section: 'CALCULATION', kind: '계산', subject: '1인 가구 냉동 정찬', metric: 'SAM',
      value: 380000000000, unit: 'KRW', period: '2024', grade: '추정',
      formula: '1인 가구 수 × 연간 구매 빈도 × 객단가',
      inputs: { '1인 가구 수': 7830000, '연간 구매 빈도(회)': 8, '객단가(원)': 9500 },
      materialIds: ['C-F001'],
      assumptions: ['구매 빈도 8회와 객단가 9,500원은 **조사한 값이 아니라 가정이에요.**'],
      caveats: [],
    },
  ],
  market: {
    tam: {
      value: 5200000000000, unit: 'KRW', grade: '실무 신뢰',
      formula: 'HMR 전체 시장', evidenceIds: ['C-F002'],
      assumptions: [], caveats: [],
    },
    sam: {
      value: 380000000000, unit: 'KRW', grade: '추정',
      formula: '1인 가구 수 × 연간 구매 빈도 × 객단가', evidenceIds: ['C-F007'],
      assumptions: ['구매 빈도와 객단가가 가정이라 **하나의 숫자가 아니라 범위로 읽어야 해요.**'], caveats: [],
    },
    growth: {
      value: 7.1, unit: 'PERCENT_PER_YEAR', grade: '확정',
      formula: '2023 → 2024', evidenceIds: ['C-F003'],
      assumptions: ['단순 증감률이에요. 연평균 성장률(CAGR)이 아니에요.'], caveats: [],
    },
    price: {
      min: 8900, base: 9500, max: 12000, currency: 'KRW',
      baseKind: 'median', baseNote: '표시가격 2건의 중앙값이라 **아직은 잠정 대표값이에요.**',
      grade: '실무 신뢰', evidenceIds: ['C-F004', 'C-F005'],
      caveats: ['**15,000원 이상 프리미엄 구간은 표시가격을 한 건도 찾지 못했어요.**'],
    },
    notFound: [
      {
        item: '수요 근거 미확보',
        detail: '프리미엄(15,000원↑) 냉동식 재구매율\n1인 가구의 냉동식 지출액 직접 관측',
      },
    ],
    coverageCaveat: '국내 B2C 만 셈했어요 · 2025년 환율 기준이에요 · 편의점 채널은 뺐어요.',
  },
  scorecard: [
    { subject: 'MARKET_SIZE', state: 'FILLED', detail: 'HMR 전체 5.2조원 · 노릴 수 있는 시장 3,800억원 (가정 2개 포함)' },
    { subject: 'GROWTH', state: 'FILLED', detail: '+7.1% (2023→2024) · 단순 증감률이에요' },
    { subject: 'COMPETITOR', state: 'FILLED', detail: '실명 2곳을 찾았고 표시가격 2건을 확인했어요' },
    { subject: 'PRICE', state: 'PARTIAL', detail: '8,900~12,000원을 확인했어요 · 프리미엄 구간은 못 찾았어요' },
    { subject: 'DEMAND', state: 'FILLED', detail: '검색량 증가율 1건이에요 (언론 인용)' },
    { subject: 'CALCULATION', state: 'FILLED', detail: '계산 1건이에요 · 가정 2개를 밝혀 뒀어요' },
    { subject: 'CHANNEL', state: 'FILLED', detail: '어디서 팔리나 — 채널별 비중 — 실린 사실 4건' },
    { subject: 'UNIT_ECONOMICS', state: 'PARTIAL', detail: '한 개 팔면 얼마가 남나 — 실린 사실 1건' },
    { subject: 'REGULATION', state: 'MISSING', detail: '무엇을 지켜야 하나 — **한 건도 못 구했어요.** 아래 처방을 보세요' },
    { subject: 'NOT_FOUND', state: 'REPORTED', detail: '채우지 못한 항목이 2건 있어요' },
  ],
  canvas: {
    cells: [
      {
        canvasCell: 'CUSTOMER_SEGMENTS', status: 'UNVERIFIED',
        content: ['바쁜 직장인 누구나 — 조리 시간을 못 내는 사람'],
        reason: '컨셉에 적은 설명뿐이고, 이 정의를 뒷받침할 조사 근거가 없어요.',
        sourceLabels: ['concept_snapshot'], marketEvidenceIds: [],
        missingEvidence: ['직장인 전체의 냉동식 지출 관측'], caveats: [],
      },
      {
        canvasCell: 'VALUE_PROPOSITIONS', status: 'VERIFIED',
        content: ['조리 10분 안에 끝나는 정찬급 냉동식', '기존 대안보다 나트륨이 낮은 레시피'],
        reason: '수요 신호와 시장 규모를 근거로 붙였어요.',
        sourceLabels: ['demand_evidence', 'market_size'],
        marketEvidenceIds: ['C-F006', 'C-F002'],
        missingEvidence: [], caveats: ['**언론이 인용한 민간 조사예요 — 원자료는 확인하지 못했어요.**'],
      },
      {
        canvasCell: 'CHANNELS', status: 'PARTIAL',
        content: ['자사몰', '쿠팡'],
        reason: '경쟁사 판매 채널은 확인했지만, 우리 채널이 팔린다는 근거는 아직 없어요.',
        sourceLabels: ['competitor_analysis'], marketEvidenceIds: ['C-F004'],
        missingEvidence: ['자사몰 전환율'], caveats: [],
      },
      {
        canvasCell: 'CUSTOMER_RELATIONSHIPS', status: 'PLAN',
        content: ['주간 구독 관리 · 리뷰 응대'],
        reason: '직접 적어 주신 실행 계획이에요 — 조사로 확인할 항목이 아니에요.',
        sourceLabels: ['concept_snapshot'], marketEvidenceIds: [],
        missingEvidence: [], caveats: [],
      },
      {
        canvasCell: 'KEY_ACTIVITIES', status: 'PLAN',
        content: ['레시피 개발', '생산 위탁 관리'],
        reason: '직접 적어 주신 실행 계획이에요 — 조사로 확인할 항목이 아니에요.',
        sourceLabels: ['concept_snapshot'], marketEvidenceIds: [],
        missingEvidence: [], caveats: [],
      },
      {
        canvasCell: 'KEY_RESOURCES', status: 'PLAN',
        content: ['레시피 IP', '콜드체인 계약'],
        reason: '직접 적어 주신 실행 계획이에요 — 조사로 확인할 항목이 아니에요.',
        sourceLabels: ['concept_snapshot'], marketEvidenceIds: [],
        missingEvidence: [], caveats: [],
      },
      {
        canvasCell: 'KEY_PARTNERS', status: 'PLAN',
        content: ['냉동식품 OEM 공장', '물류 대행사'],
        reason: '직접 적어 주신 실행 계획이에요 — 계약한 상대가 아니라 필요한 유형이에요.',
        sourceLabels: ['concept_snapshot'], marketEvidenceIds: [],
        missingEvidence: [], caveats: [],
      },
      {
        canvasCell: 'REVENUE_STREAMS', status: 'PARTIAL',
        content: ['단품 판매 9,500원', '주간 구독'],
        reason: '표시가격은 확인했지만, 그 값을 낼 사람이 있다는 근거가 없어요.',
        sourceLabels: ['price_analysis'], marketEvidenceIds: ['C-F004', 'C-F005'],
        missingEvidence: ['프리미엄 구간 재구매율'],
        caveats: ['**15,000원 이상 프리미엄 구간은 표시가격을 한 건도 찾지 못했어요.**'],
      },
      {
        canvasCell: 'COST_STRUCTURE', status: 'PLAN',
        content: ['생산 원가 · 물류비 · 마케팅비'],
        reason: '적어 주신 예산·기간·인원에서 가져왔어요.',
        sourceLabels: ['execution_constraints'], marketEvidenceIds: [],
        missingEvidence: [], caveats: [],
      },
    ],
  },
  bm: {
    decision: 'REVISION_REQUIRED',
    confidence: 'MEDIUM',
    marketFitStatus: 'PARTIAL',
    marketFitSummary: '시장은 자라고 있지만, 고객 정의가 근거와 이어지지 않았어요.',
    consistencyStatus: 'PARTIAL',
    consistencySummary: '가격은 확인된 범위 안에 있지만, 그 값을 낼 사람이 있다는 근거가 없어요.',
    summary: '시장은 확인됐지만 고객 정의와 지불 근거가 아직 비어 있어요.',
    strengths: ['연 7.1%씩 자라는 시장이에요', '정한 가격이 시장에서 확인된 범위 안에 있어요'],
    weaknesses: ['고객 세그먼트를 뒷받침할 근거가 없어요', '프리미엄 가격대는 한 건도 못 찾았어요'],
    risks: ['HMR 전체 규모를 냉동 정찬 시장으로 잘못 읽을 수 있어요'],
    gateReasons: [
      {
        code: 'G1', cell: 'CUSTOMER_SEGMENTS',
        message: '컨셉에 적은 설명이 전부예요 — 조사 근거가 0건이에요. 타깃을 좁히거나 추가 조사가 필요해요.',
        evidenceIds: [],
        // 성적표의 「시장 크기」는 채워졌는데 이 칸이 인용을 못 했다 → B급
        cause: 'UNCITED',
      },
      {
        code: 'G5', cell: 'REVENUE_STREAMS',
        message: '수요 근거를 한 건도 인용하지 못했어요 — 「받을 수 있는 값」과 「살 사람이 있다」는 다른 얘기예요.',
        evidenceIds: [],
        // 성적표의 「가격」이 PARTIAL — 프리미엄 구간은 애초에 못 찾았다 → A급
        cause: 'UNCOLLECTED',
      },
    ],
    legal: null,
  },
  summary: null,
  stages: [], degradations: [], notes: [],
};

/**
 * 과목 → 그 과목이 쓴 근거 id.
 *
 * <p>⚠ <b>봉투에는 이 표가 없다.</b> 지금은 화면이 `bucketEvidence()` 로 되짚는데,
 * 그 함수 주석이 스스로 「임시 분류다 · 서버가 과목을 실어 주면 통째로 없어진다」고 적고 있다.
 * 과목을 눌러 근거를 펴려면 이 대응이 <b>봉투에</b> 있어야 한다 — 와이어프레임은 그것을
 * 미리 놓고 그린다(핸드오프 §11-6 에 숙제로 적어 뒀다).
 */
export const SUBJECT_EVIDENCE = {
  MARKET_SIZE: ['C-F002', 'C-F007', 'C-F001'],
  GROWTH: ['C-F003'],
  COMPETITOR: ['C-F004', 'C-F005'],
  PRICE: ['C-F004', 'C-F005'],
  DEMAND: ['C-F006'],
  CALCULATION: ['C-F007'],
  NOT_FOUND: [],
};

/**
 * 화면 2 — 다듬어진 컨셉. **3판이 낼 산출물의 자리표시다.**
 * 아직 그 계약이 없어서 모양만 세워 뒀다(계획서 3-5).
 */
export const SAMPLE_REVISION = {
  /** 본문. `ref` 가 있는 조각이 바뀐 곳이다. */
  parts: [
    { text: '바쁜 ' },
    { text: '1인 가구 직장인', ref: 1 },
    { text: '에게, 조리 10분 안에 끝나는 정찬급 냉동식을 ' },
    { text: '9,500원대', ref: 2 },
    { text: ' 단품과 주간 구독으로 판매해요. ' },
    { text: '기존 대안보다 나트륨을 30% 낮춘', ref: 3 },
    { text: ' 레시피가 핵심이에요. 판매는 자사몰과 쿠팡에서 시작해요.' },
  ],
  changes: [
    {
      ref: 1, title: '타깃을 좁혔어요',
      before: '바쁜 직장인 누구나', after: '바쁜 1인 가구 직장인',
      why: '수요 신호가 1인 가구에서만 뚜렷하게 잡혔어요. 타깃을 넓게 잡으면 근거 없는 주장이 돼요.',
      from: { label: '시장 분석 5. 수요 근거', subject: 'DEMAND', evidenceIds: ['C-F006'] },
    },
    {
      ref: 2, title: '가격을 시장 안으로 옮겼어요',
      before: '15,000원 프리미엄 라인', after: '9,500원대 주력 + 구독 할인',
      why: '조사에서 확인된 가격대는 8,900~12,000원이에요. 15,000원 이상 구간은 한 건도 못 찾아서 '
        + '「비싸게 받을 수 있다」를 뒷받침할 게 없어요.',
      from: { label: '시장 분석 4. 가격', subject: 'PRICE', evidenceIds: ['C-F004', 'C-F005'] },
    },
    {
      ref: 3, title: '표현을 법에 맞게 고쳤어요',
      before: '저나트륨 건강식', after: '기존 대안보다 나트륨을 30% 낮춘',
      why: '「저나트륨」은 법으로 정한 함량 기준을 충족해야 쓸 수 있어요. 지금 레시피로는 '
        + '기준에 못 미쳐서 비교 표현으로 바꿨어요.',
      from: { label: '법률 검토 1', law: 'L1' },
    },
  ],
  laws: [
    {
      id: 'L1', name: '식품표시광고법', clause: '「저나트륨」 표시 기준',
      status: '컨셉에 반영했어요', tone: 'success',
      note: '「저나트륨」은 100g당 나트륨 함량 기준을 충족해야 쓸 수 있어요. 지금 레시피는 '
        + '기준에 못 미쳐서 표현을 고쳤어요.',
    },
    {
      id: 'L2', name: '축산물 위생관리법', clause: '냉동식품 제조·유통 허가',
      status: '직접 확인이 필요해요', tone: 'warning',
      note: '육류가 들어간 냉동식품은 별도 허가가 필요해요. 계약 전에 OEM 공장이 허가를 '
        + '갖고 있는지 확인해 주세요. 컨셉은 바뀌지 않았어요 — 실행 조건으로 기록해 뒀어요.',
    },
    {
      id: 'L3', name: '전자상거래법', clause: '구독 해지·환불 고지',
      status: '문제 없어요', tone: 'neutral',
      note: '주간 구독은 해지·환불 조건만 안내하면 돼요. 컨셉을 바꿀 필요는 없어요.',
    },
  ],
};

/** 2절 — 가격 판단. **기계가 계산한 문장이고 모델이 쓴 것이 아니다.** */
export const SAMPLE_JUDGMENT = {
  price: 8900,
  lines: [
    {
      what: '같은 진열대의 한 개 값',
      sentence: '컨셉 가격 8,900원은 냉동식품 판매단가 6,513원의 1.37배예요.',
      formula: '8,900 ÷ 6,513 = 1.37',
      silentBecause: null,
      sources: [{ raw: '6,513원', subject: '냉동식품 판매단가', url: 'https://kind.krx.co.kr/example-price' }],
    },
    {
      // **못 쓴 갈래도 세운다** — 침묵을 「해당 없음」으로 읽히게 두지 않는다.
      what: '배달로 대체될 때',
      sentence: null, formula: null,
      silentBecause: '배달 한 끼는 음식값과 배달비가 **둘 다** 있어야 셈이 돼요. 지금 실린 것은 음식값뿐이에요.',
      sources: [],
    },
  ],
  conclusion: '**같은 진열대에서는 값이 위예요.** 값이 아닌 이유(정량·조리 시간·보존)가 서지 '
    + '않으면 이 가격은 지탱되지 않아요. 어느 쪽으로 팔지는 **이 조사가 정하지 못해요** — 시장 인터뷰에서 물을 것.',
};

/** 8절 — 처방. **셋째 열(「어디서」)이 이 표의 값어치다.** */
export const SAMPLE_PRESCRIPTIONS = [
  {
    section: 'REGULATION', kind: 'REACHABLE', kindLabel: '공개 자료에 있는데 이 조사가 못 닿았어요',
    what: '이름은 29건 잡혔는데 **그중 29건이 값 자리에 숫자가 없어요** — 지켜야 할 기준치가 하나도 안 잡혔어요',
    why: '공개돼 있으나 이 조사의 검색이 닿지 못했어요. 어디를 볼지 적어 드려요.',
    where: '식품공전 「즉석섭취·편의식품류」 규격의 세균수·대장균군 기준을 보세요',
  },
  {
    section: 'CHANNEL', kind: 'USER_INPUT', kindLabel: '사용자가 직접 넣으면 풀려요',
    what: '채널별 비중은 잡혔으나 **입점 조건·수수료가 없어요** — 「어디서 팔리나」에는 답하고 「**어디부터 열까**」에는 못 답해요',
    why: '이 값은 웹에 없어요. 사업가 본인이 답을 갖고 있어요(자기 원가·설비·팀·계약 조건).',
    where: '입점 조건·수수료율은 채널과의 협상 결과라 회사마다 달라요. 실제 제안서를 받아 넣어야 해요',
  },
  {
    section: 'DEMAND', kind: 'INTERVIEW', kindLabel: '시장 인터뷰에서 물어야 풀려요',
    what: '침투율은 **컨셉이 스스로 「관측 근거가 없는 순수 가정」이라 적어 둔 값**이에요',
    why: '사람의 선호·지불 의사·고르는 이유는 문서에 없어요. 다음 단계(시장 인터뷰)의 질문이 돼요.',
    where: '구매 의향을 직접 물어요. ⚠ 의향은 실제 구매가 아니에요 — 언급 수로만 읽어요',
  },
];

/** 9절 — 지지/흔듦. **갈래와 근거는 기계가 정하고 모델은 문장만 쓴다.** */
export const SAMPLE_SYNTHESIS = [
  {
    key: '시장이_자란다', stance: '지지',
    sentence: '5.2조원 시장이 연 7.1% 자라고 있어 새로 들어갈 자리가 남아 있어요.',
    what: '시장이 자라고 있나',
    sources: [{ raw: '5,200,000,000,000원', subject: '가정간편식(HMR) 시장 규모' },
      { raw: '7.1%', subject: '냉동 간편식 연 성장률' }],
  },
  {
    key: '주_채널이_안_보인다', stance: '흔듦',
    sentence: '컨셉이 든 편의점은 5.99%뿐이고 같은 표의 최대는 대형마트 31.05% 로 주 채널이 아니에요.',
    what: '⚠ 이것은 시장 전체가 아니라 **오뚜기 한 회사의 2025년 매출처 구성비**예요',
    sources: [{ raw: '5.99%', subject: '편의점 매출액 — 비중' },
      { raw: '31.05%', subject: '대형마트 매출액 — 비중' }],
  },
  {
    key: '값이_위에_있다', stance: '흔듦',
    sentence: '8,900원은 냉동식품 판매단가 6,513원보다 위여서 값만으로는 고를 이유가 없어요.',
    what: '우리 가격이 대체 수단보다 위인가',
    sources: [{ raw: '6,513원', subject: '냉동식품 판매단가' }],
  },
];

// 봉투에서 이 셋은 **`result` 안**에 있다. 와이어프레임도 같은 자리에 둔다 —
// 자리가 다르면 화면 부품이 제품과 다르게 돌고, 그러면 와이어프레임이 거짓말이 된다.
SAMPLE_RESULT.judgment = SAMPLE_JUDGMENT;
SAMPLE_RESULT.prescriptions = SAMPLE_PRESCRIPTIONS;
SAMPLE_RESULT.synthesis = SAMPLE_SYNTHESIS;
