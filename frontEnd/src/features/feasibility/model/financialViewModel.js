/**
 * 재무 지표 계산 — 백엔드 `FinancialCalculationPolicy`와 **같은 공식**이다.
 * 한쪽만 바꾸면 확정 결과와 슬라이더 결과가 어긋난다. 두 구현의 일치는
 * 같은 기준 케이스를 쓰는 단위 테스트가 못박는다.
 *
 * 저장되는 값은 확정 단계에서만 바뀐다. 여기 계산은 화면용이며 아무것도 저장하지 않는다.
 */

export const HORIZON_MONTHS = 36;
const SAFETY_MARGIN_WARN = 0.2;
const IRR_ITERATIONS = 200;
const IRR_LOWER = -0.99;
const IRR_UPPER = 100;

export const REQUIRED_KEYS = [
  'UNIT_PRICE', 'VARIABLE_COST_RATE', 'MONTHLY_VOLUME',
  'MONTHLY_FIXED_COST', 'INITIAL_INVESTMENT', 'DISCOUNT_RATE',
];

export const ASSUMPTION_LABELS = {
  UNIT_PRICE: '객단가',
  VARIABLE_COST_RATE: '변동원가율',
  MONTHLY_VOLUME: '월 판매량',
  MONTHLY_FIXED_COST: '월 고정비',
  INITIAL_INVESTMENT: '초기 투자',
  DISCOUNT_RATE: '할인율(연)',
};

export const SOURCE_LABELS = {
  PLAN: '기획서 인용',
  DEFAULT: '기본값 적용',
  USER: '사용자 입력',
};

/** 지표를 내지 못한 이유를 사람 말로. 숨기지 않고 "확인 필요"로 드러낸다. */
export const UNAVAILABLE_LABELS = {
  MISSING_ASSUMPTION: '가정이 부족해 계산하지 못했습니다',
  NON_POSITIVE_CONTRIBUTION: '건당 공헌이익이 0 이하라 손익분기가 존재하지 않습니다',
  NON_POSITIVE_MONTHLY_PROFIT: '월 영업이익이 0 이하라 초기 투자를 회수할 수 없습니다',
  NO_SIGN_CHANGE: '현금흐름의 부호가 바뀌지 않아 해가 없습니다',
};

export const CONFLICT_OPTION_LABELS = {
  UNIT_TIMES_VOLUME: '단가 × 수량 기준',
  STATED_REVENUE: '기획서에 적힌 매출 기준',
};

export const SCENARIOS = [
  { mode: 'CONSERVATIVE', label: '보수', factor: 0.8 },
  { mode: 'BASE', label: '기준', factor: 1 },
  { mode: 'OPTIMISTIC', label: '낙관', factor: 1.2 },
];

export function parseJson(value, fallback = null) {
  if (!value) return fallback;
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

/** 저장된 가정 묶음을 읽는다. 없으면 화면이 빈 상태를 그릴 수 있게 기본 골격을 준다. */
export function readAssumptions(analysis) {
  const parsed = parseJson(analysis?.assumptionsJson, null);
  return {
    state: parsed?.state ?? 'NEEDS_ASSUMPTIONS',
    confirmedAt: parsed?.confirmedAt ?? null,
    items: parsed?.items ?? [],
    conflicts: parsed?.conflicts ?? [],
  };
}

/** 가정 목록을 계산에 쓰는 평평한 값 맵으로. 없는 키는 null로 남긴다(0으로 채우지 않는다). */
export function assumptionValues(assumptions) {
  const values = {};
  REQUIRED_KEYS.forEach((key) => { values[key] = null; });
  (assumptions?.items ?? []).forEach((item) => {
    values[item.key] = item.value ?? null;
  });
  return values;
}

export function missingKeys(values) {
  return REQUIRED_KEYS.filter((key) => values[key] === null || values[key] === undefined);
}

// ------------------------------------------------------------------ 지표

const metric = (value) => ({ value, reason: null, missingKeys: [] });
const missing = (keys) => ({ value: null, reason: 'MISSING_ASSUMPTION', missingKeys: keys });
const unavailable = (reason) => ({ value: null, reason, missingKeys: [] });
const isUnavailable = (item) => item.value === null || item.value === undefined;

function netPresentValue(investment, monthlyProfit, annualRate) {
  const monthlyRate = annualRate / 12;
  let total = -investment;
  for (let month = 1; month <= HORIZON_MONTHS; month += 1) {
    total += monthlyProfit / (1 + monthlyRate) ** month;
  }
  return total;
}

function cumulativeCashFlow(investment, monthlyProfit) {
  const series = [-investment];
  for (let month = 1; month <= HORIZON_MONTHS; month += 1) {
    series.push(series[month - 1] + monthlyProfit);
  }
  return series;
}

function irrOf(investment, monthlyProfit) {
  const low = netPresentValue(investment, monthlyProfit, IRR_LOWER);
  const high = netPresentValue(investment, monthlyProfit, IRR_UPPER);
  if (low === 0) return metric(IRR_LOWER);
  if (high === 0) return metric(IRR_UPPER);
  if (low > 0 === high > 0) return unavailable('NO_SIGN_CHANGE');
  let lo = IRR_LOWER;
  let hi = IRR_UPPER;
  for (let i = 0; i < IRR_ITERATIONS; i += 1) {
    const mid = (lo + hi) / 2;
    const value = netPresentValue(investment, monthlyProfit, mid);
    if (netPresentValue(investment, monthlyProfit, lo) > 0 === value > 0) lo = mid;
    else hi = mid;
  }
  return metric((lo + hi) / 2);
}

function verdictOf(contribution, breakEvenMonth, safetyMargin, absent) {
  if (absent.length > 0 && isUnavailable(contribution)) return 'INSUFFICIENT_INFORMATION';
  if (!isUnavailable(contribution) && contribution.value <= 0) return 'HIGH_RISK';
  if (breakEvenMonth.reason === 'NON_POSITIVE_MONTHLY_PROFIT') return 'HIGH_RISK';
  if (isUnavailable(breakEvenMonth)) return 'INSUFFICIENT_INFORMATION';
  const slowPayback = breakEvenMonth.value > HORIZON_MONTHS;
  const thinMargin = !isUnavailable(safetyMargin) && safetyMargin.value < SAFETY_MARGIN_WARN;
  return slowPayback || thinMargin ? 'CONDITIONAL' : 'PROMISING';
}

/**
 * 확정 가정(또는 슬라이더로 조정한 가정)에서 지표 전부를 낸다.
 * 계산 불가는 예외가 아니라 결과다 — 값을 null로 두고 사유를 함께 돌려준다.
 */
export function computeFinancials(values) {
  const absent = missingKeys(values);
  const {
    UNIT_PRICE: unitPrice, VARIABLE_COST_RATE: variableCostRate,
    MONTHLY_VOLUME: monthlyVolume, MONTHLY_FIXED_COST: monthlyFixedCost,
    INITIAL_INVESTMENT: initialInvestment, DISCOUNT_RATE: discountRate,
  } = values;

  const contributionMargin = unitPrice === null || variableCostRate === null
    ? missing(absent) : metric(unitPrice * (1 - variableCostRate));

  const monthlyProfit = isUnavailable(contributionMargin)
    || monthlyVolume === null || monthlyFixedCost === null
    ? missing(absent)
    : metric(contributionMargin.value * monthlyVolume - monthlyFixedCost);

  let breakEvenQty;
  if (isUnavailable(contributionMargin) || monthlyFixedCost === null) breakEvenQty = missing(absent);
  else if (contributionMargin.value <= 0) breakEvenQty = unavailable('NON_POSITIVE_CONTRIBUTION');
  else breakEvenQty = metric(monthlyFixedCost / contributionMargin.value);

  let safetyMarginPct;
  if (isUnavailable(breakEvenQty)) {
    safetyMarginPct = breakEvenQty.reason === 'MISSING_ASSUMPTION'
      ? missing(absent) : unavailable(breakEvenQty.reason);
  } else if (!monthlyVolume) {
    safetyMarginPct = missing(absent);
  } else {
    safetyMarginPct = metric((monthlyVolume - breakEvenQty.value) / monthlyVolume);
  }

  let breakEvenMonth;
  if (isUnavailable(monthlyProfit) || initialInvestment === null) breakEvenMonth = missing(absent);
  else if (monthlyProfit.value <= 0) breakEvenMonth = unavailable('NON_POSITIVE_MONTHLY_PROFIT');
  else breakEvenMonth = metric(Math.ceil(initialInvestment / monthlyProfit.value));

  const roi3y = isUnavailable(monthlyProfit) || !initialInvestment
    ? missing(absent)
    : metric((monthlyProfit.value * HORIZON_MONTHS - initialInvestment) / initialInvestment);

  const npv36m = isUnavailable(monthlyProfit)
    || initialInvestment === null || discountRate === null
    ? missing(absent)
    : metric(netPresentValue(initialInvestment, monthlyProfit.value, discountRate));

  const irr = isUnavailable(monthlyProfit) || initialInvestment === null
    ? missing(absent) : irrOf(initialInvestment, monthlyProfit.value);

  let peakFunding;
  let cashFlow = [];
  if (isUnavailable(monthlyProfit) || initialInvestment === null) {
    peakFunding = missing(absent);
  } else {
    cashFlow = cumulativeCashFlow(initialInvestment, monthlyProfit.value);
    let worst = 0;
    cashFlow.forEach((amount, month) => { if (amount < cashFlow[worst]) worst = month; });
    peakFunding = metric({ amount: cashFlow[worst], month: worst });
  }

  return {
    contributionMargin,
    monthlyProfit,
    breakEvenQty,
    breakEvenMonth,
    safetyMarginPct,
    roi3y,
    npv36m,
    irr,
    peakFunding,
    cumulativeCashFlow: cashFlow,
    verdict: verdictOf(contributionMargin, breakEvenMonth, safetyMarginPct, absent),
  };
}

/** 보수/기준/낙관 — 판매량과 객단가에만 곱한다. 저장하지 않는 샌드박스 조정이다. */
export function applyScenario(values, mode) {
  const scenario = SCENARIOS.find((item) => item.mode === mode) ?? SCENARIOS[1];
  const scale = (value) => (value === null || value === undefined ? value : value * scenario.factor);
  return {
    ...values,
    UNIT_PRICE: scale(values.UNIT_PRICE),
    MONTHLY_VOLUME: scale(values.MONTHLY_VOLUME),
  };
}

export function isDirty(current, base) {
  return REQUIRED_KEYS.some((key) => (current?.[key] ?? null) !== (base?.[key] ?? null));
}

// ------------------------------------------------------------------ 차트 좌표

/**
 * 누적 현금흐름을 SVG 좌표로. 순수 함수라 단위 테스트가 가능하다.
 * 0선의 y도 함께 돌려준다 — 손익분기를 눈으로 읽는 기준선이다.
 */
export function chartGeometry(series, { width = 640, height = 220, padding = 24 } = {}) {
  if (!series || series.length < 2) return null;
  const max = Math.max(...series, 0);
  const min = Math.min(...series, 0);
  const span = max - min || 1;
  const innerWidth = width - padding * 2;
  const innerHeight = height - padding * 2;
  const x = (index) => padding + (innerWidth * index) / (series.length - 1);
  const y = (value) => padding + innerHeight * (1 - (value - min) / span);
  return {
    width,
    height,
    zeroY: y(0),
    points: series.map((value, index) => ({ x: x(index), y: y(value), month: index, value })),
  };
}

// ------------------------------------------------------------------ 표시 형식

/** 표시 숫자는 전부 여기를 거친다 — 부동소수점 잔재가 화면에 나오지 않게. */
export function formatCurrency(value) {
  if (value === null || value === undefined) return '확인 필요';
  return `${Math.round(value).toLocaleString('ko-KR')}원`;
}

export function formatNumber(value, digits = 1) {
  if (value === null || value === undefined) return '확인 필요';
  return value.toLocaleString('ko-KR', {
    minimumFractionDigits: 0, maximumFractionDigits: digits,
  });
}

export function formatPercent(value, digits = 1) {
  if (value === null || value === undefined) return '확인 필요';
  return `${(value * 100).toFixed(digits)}%`;
}

export function formatMonths(value) {
  if (value === null || value === undefined) return '확인 필요';
  return `${value}개월`;
}

/**
 * 화면 표기용 값 — 단위에 따라 원/비율/개로 갈린다.
 *
 * 수량은 소수 첫째 자리까지만 보인다. 연 수량을 12로 나눈 값이라 원본은 666.6667처럼
 * 소수가 길게 남는데, 그 자리수를 그대로 내보내면 계산 잔재가 화면에 노출된다(§6).
 * **저장·계산은 언제나 원본 값**을 쓰고 여기서 줄이는 건 표기뿐이다.
 */
export function formatAssumption(item) {
  if (item?.value === null || item?.value === undefined) return '미입력';
  if (item.unit === 'RATIO') return formatPercent(item.value);
  if (item.unit === 'EA') return `${formatNumber(item.value, 1)}개`;
  return formatCurrency(item.value);
}
