import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  CANVAS_LAYOUT, formatValue, gradeView, normalizeMarketResult,
} from './marketResult.js';

/**
 * **AI·백엔드와 같은 골든 픽스처**를 읽는다.
 *
 * 세 층이 같은 파일을 보므로, 한쪽이 스키마를 바꾸면 나머지 둘의 테스트가 즉시 빨개진다.
 * 사본을 만들면 그 성질이 사라진다 — 그래서 복사하지 않고 저장소 경로로 읽는다.
 */
function fixture(name) {
  const here = dirname(fileURLToPath(import.meta.url));
  const path = resolve(here, '../../../../ai/tests/fixtures/market_research', name);
  const raw = JSON.parse(readFileSync(path, 'utf-8'));
  Object.keys(raw).filter((key) => key.startsWith('_')).forEach((key) => delete raw[key]);
  return raw;
}

describe('normalizeMarketResult — FULL', () => {
  const result = normalizeMarketResult(fixture('full.json'));

  it('7과목이 라벨과 함께 온다', () => {
    expect(result.scorecard).toHaveLength(7);
    expect(result.scorecard.map((item) => item.label)).toContain('시장 크기');
    expect(result.scorecard.every((item) => item.state)).toBe(true);
  });

  it('근거마다 등급이 있고 id 로 찾을 수 있다', () => {
    expect(result.evidence.length).toBeGreaterThan(0);
    expect(result.evidence.every((item) => item.grade)).toBe(true);
    expect(result.evidenceById.get('C-F006')).toBeTruthy();
  });

  it('경계가 근거에 붙어 온다 — 값과 한 몸이다', () => {
    const withCaveat = result.evidence.filter((item) => item.caveats.length > 0);
    expect(withCaveat.length).toBeGreaterThan(0);
    expect(withCaveat.some((item) => item.caveats[0].includes('시장 매출 아님'))).toBe(true);
  });

  it('가격 대표값의 성격 문장이 살아 있다', () => {
    expect(result.market.price.baseKind).toBe('MEDIAN_PROVISIONAL');
    expect(result.market.price.baseNote).toContain('확정 단가가 아니다');
  });

  it('BM 쪽 칸은 비어 있다 — 모드가 섞이지 않는다', () => {
    expect(result.canvas).toBeNull();
    expect(result.bm).toBeNull();
  });
});

describe('normalizeMarketResult — BM', () => {
  const result = normalizeMarketResult(fixture('bm.json'));

  it('9칸이 표준 배치 순서로 온다', () => {
    expect(result.canvas).toHaveLength(9);
    expect(result.canvas.map((cell) => cell.cell)).toEqual(CANVAS_LAYOUT.map((s) => s.cell));
    expect(result.canvas.every((cell) => cell.absent === false)).toBe(true);
  });

  it('⭐ 인용한 근거의 경계가 칸에 실려 있다', () => {
    const segments = result.canvas.find((cell) => cell.cell === 'CUSTOMER_SEGMENTS');
    expect(segments.evidenceIds).toContain('C-F011');
    const fromEvidence = result.evidenceById.get('C-F011').caveats;
    fromEvidence.forEach((caveat) => expect(segments.caveats).toContain(caveat));
  });

  it('판정과 신뢰도가 온다', () => {
    expect(result.bm.decision).toBe('CONDITIONAL');
    expect(result.bm.confidence).toBe('MEDIUM');
  });
});

describe('누락을 조용히 넘기지 않는다', () => {
  it('등급이 없으면 «등급 표기 없음» 으로 드러낸다', () => {
    expect(gradeView(undefined).label).toBe('등급 표기 없음');
    expect(gradeView(undefined).tone).toBe('danger');
    expect(gradeView('확정').label).toBe('확정');
  });

  it('값이 없으면 «미확보» 라고 쓴다 — 빈 자리로 두지 않는다', () => {
    expect(formatValue(null, '원')).toBe('미확보');
    expect(formatValue(undefined)).toBe('미확보');
    expect(formatValue(0, '개')).toBe('0 개');       // 0 은 값이다
    expect(formatValue(19800, '원')).toBe('19,800 원');
  });

  it('칸이 아예 안 오면 absent 로 구분한다 — 미확인과 다른 사건이다', () => {
    const raw = fixture('bm.json');
    raw.canvas.cells = raw.canvas.cells.filter((cell) => cell.canvasCell !== 'CHANNELS');
    const result = normalizeMarketResult(raw);
    const channels = result.canvas.find((cell) => cell.cell === 'CHANNELS');
    expect(channels.absent).toBe(true);
  });

  it('결과가 없으면 null 을 준다 — 빈 객체로 흉내내지 않는다', () => {
    expect(normalizeMarketResult(null)).toBeNull();
    expect(normalizeMarketResult(undefined)).toBeNull();
  });
});
