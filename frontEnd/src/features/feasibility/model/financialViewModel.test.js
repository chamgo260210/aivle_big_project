import { describe, expect, it } from 'vitest';
import {
  applyScenario, assumptionValues, chartGeometry, computeFinancials, formatAssumption,
  isDirty, missingKeys, readAssumptions,
} from './financialViewModel.js';

/**
 * 기준 케이스는 백엔드 `FinancialCalculationPolicyTests`와 **같은 수치**다.
 * 공식이 두 곳에 있으므로 이 값이 두 구현의 일치를 못박는다 — 한쪽만 고치면 여기서 깨진다.
 */
const BASE = {
  UNIT_PRICE: 38000,
  VARIABLE_COST_RATE: 0.29,
  MONTHLY_VOLUME: 1000,
  MONTHLY_FIXED_COST: 20000000,
  INITIAL_INVESTMENT: 50000000,
  DISCOUNT_RATE: 0.1,
};

describe('computeFinancials', () => {
  it('백엔드 정책과 같은 기준 수치를 낸다', () => {
    const result = computeFinancials(BASE);
    expect(result.contributionMargin.value).toBe(26980);
    expect(result.monthlyProfit.value).toBe(6980000);
    expect(result.breakEvenQty.value).toBeCloseTo(741.29, 2);
    expect(result.breakEvenMonth.value).toBe(8);
    expect(result.roi3y.value).toBeCloseTo(4.0256, 4);
    expect(result.safetyMarginPct.value).toBeCloseTo(0.2587, 4);
    expect(result.verdict).toBe('PROMISING');
  });

  it('공헌이익이 0 이하면 손익분기가 존재하지 않는다고 말한다', () => {
    const result = computeFinancials({ ...BASE, VARIABLE_COST_RATE: 1.1 });
    expect(result.contributionMargin.value).toBeLessThan(0);
    expect(result.breakEvenQty.value).toBeNull();
    expect(result.breakEvenQty.reason).toBe('NON_POSITIVE_CONTRIBUTION');
    expect(result.verdict).toBe('HIGH_RISK');
  });

  it('월 영업이익이 0 이하면 도달 불가로 남긴다', () => {
    const result = computeFinancials({ ...BASE, MONTHLY_VOLUME: 100 });
    expect(result.breakEvenMonth.value).toBeNull();
    expect(result.breakEvenMonth.reason).toBe('NON_POSITIVE_MONTHLY_PROFIT');
    // 손익분기 수량 자체는 여전히 계산된다 — 목표가 거기 못 미칠 뿐이다
    expect(result.breakEvenQty.value).toBeCloseTo(741.29, 2);
  });

  it('결측 가정은 0으로 채우지 않고 어떤 키가 없는지 남긴다', () => {
    const result = computeFinancials({ ...BASE, MONTHLY_FIXED_COST: null });
    expect(result.contributionMargin.value).toBe(26980);
    expect(result.monthlyProfit.value).toBeNull();
    expect(result.monthlyProfit.missingKeys).toContain('MONTHLY_FIXED_COST');
    expect(result.verdict).toBe('INSUFFICIENT_INFORMATION');
  });

  it('누적 현금흐름은 0개월차 −초기투자에서 시작해 37포인트다', () => {
    const result = computeFinancials(BASE);
    expect(result.cumulativeCashFlow).toHaveLength(37);
    expect(result.cumulativeCashFlow[0]).toBe(-50000000);
    expect(result.peakFunding.value).toEqual({ amount: -50000000, month: 0 });
  });
});

describe('applyScenario', () => {
  it('판매량과 객단가에만 20%를 곱한다', () => {
    const conservative = applyScenario(BASE, 'CONSERVATIVE');
    expect(conservative.MONTHLY_VOLUME).toBe(800);
    expect(conservative.UNIT_PRICE).toBe(30400);
    expect(conservative.MONTHLY_FIXED_COST).toBe(BASE.MONTHLY_FIXED_COST);
  });

  it('기준 시나리오는 값을 그대로 둔다', () => {
    expect(applyScenario(BASE, 'BASE')).toEqual(BASE);
  });
});

describe('isDirty', () => {
  it('확정 가정과 같으면 조정되지 않은 것으로 본다', () => {
    expect(isDirty(BASE, BASE)).toBe(false);
    expect(isDirty({ ...BASE, MONTHLY_VOLUME: 900 }, BASE)).toBe(true);
  });
});

describe('chartGeometry', () => {
  it('0선과 좌표를 순수 함수로 낸다', () => {
    const geometry = chartGeometry([-100, 0, 100], { width: 100, height: 100, padding: 0 });
    expect(geometry.points).toHaveLength(3);
    expect(geometry.points[0]).toMatchObject({ x: 0, y: 100, month: 0 });
    expect(geometry.points[2]).toMatchObject({ x: 100, y: 0, month: 2 });
    expect(geometry.zeroY).toBe(50);
  });

  it('점이 부족하면 그리지 않는다', () => {
    expect(chartGeometry([])).toBeNull();
  });
});

describe('가정 읽기', () => {
  const analysis = {
    assumptionsJson: JSON.stringify({
      state: 'NEEDS_ASSUMPTIONS',
      items: [{ key: 'UNIT_PRICE', value: 38000, unit: 'KRW' }],
      conflicts: [],
    }),
  };

  it('저장된 가정에서 값 맵을 만들고 없는 키는 null로 남긴다', () => {
    const values = assumptionValues(readAssumptions(analysis));
    expect(values.UNIT_PRICE).toBe(38000);
    expect(values.MONTHLY_FIXED_COST).toBeNull();
    expect(missingKeys(values)).toContain('MONTHLY_FIXED_COST');
  });

  it('깨진 JSON은 화면을 죽이지 않는다', () => {
    expect(readAssumptions({ assumptionsJson: '{oops' }).items).toEqual([]);
  });

  it('단위에 따라 표기가 갈린다', () => {
    expect(formatAssumption({ value: 0.29, unit: 'RATIO' })).toBe('29.0%');
    expect(formatAssumption({ value: 38000, unit: 'KRW' })).toBe('38,000원');
    expect(formatAssumption({ value: null })).toBe('미입력');
  });

  it('연 수량을 12로 나눈 계산 잔재가 화면에 새지 않는다', () => {
    // 저장·계산은 원본(666.6667)을 쓰되 표기는 소수 첫째 자리까지다
    expect(formatAssumption({ value: 666.6667, unit: 'EA' })).toBe('666.7개');
    expect(formatAssumption({ value: 614.0351, unit: 'EA' })).toBe('614개');
  });
});
