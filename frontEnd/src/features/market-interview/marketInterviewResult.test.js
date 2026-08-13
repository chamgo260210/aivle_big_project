import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import {
  AXIS_VIEW,
  THEMES_VISIBLE,
  mentionText,
  normalizeMarketInterview,
  priceText,
  profileLines,
  renderBoard,
} from './marketInterviewResult.js';

/**
 * ⚠ **AI·백엔드와 같은 파일을 읽는다.**
 * `ai/tests/test_interview_golden.py` 와 `MarketInterviewContractTests.java` 가 이 파일을
 * 함께 읽는다. 하나이므로 한쪽만 고치면 반대쪽이 즉시 빨개진다.
 */
const here = dirname(fileURLToPath(import.meta.url));
const golden = JSON.parse(readFileSync(
  resolve(here, '../../../../ai/tests/fixtures/market_interview/interview.json'), 'utf-8',
));

describe('normalizeMarketInterview', () => {
  const result = normalizeMarketInterview(golden);

  it('분모는 뽑은 사람이 아니라 답한 사람이다', () => {
    expect(result.answered).toBe(golden.telemetry.answered);
    expect(result.sampling.drawn).toBe(golden.sampling.drawn);
    expect(result.answered).not.toBe(result.sampleSize);
  });

  it('여섯 축을 화면 순서대로 나눈다', () => {
    expect(result.sections.map((section) => section.axis))
      .toEqual(AXIS_VIEW.map((view) => view.axis));
    // 접힌 것까지 세면 하나도 안 빠져야 한다 — 상한은 그리는 자리에만 걸린다.
    const total = result.sections.reduce(
      (sum, section) => sum + section.themes.length + section.hiddenThemes.length, 0,
    );
    expect(total).toBe(golden.themes.length);
  });

  it('축마다 상위 몇 개만 펼치고 나머지는 개수를 밝혀 접는다', () => {
    result.sections.forEach((section) => {
      expect(section.themes.length).toBeLessThanOrEqual(THEMES_VISIBLE);
      // 접을 것이 있으면 펼친 자리가 꽉 차 있어야 한다 — 아니면 순서가 뒤집힌 것이다.
      if (section.hiddenThemes.length > 0) {
        expect(section.themes).toHaveLength(THEMES_VISIBLE);
      }
    });
  });

  it('주제마다 응답자 명단을 세지 않고 서버가 센 수를 그대로 쓰지 않는다', () => {
    // 3층 전체가 이 명단 위에 선다. 서버가 이미 검산했고, 화면은 그 수만 읽는다.
    golden.themes.forEach((theme) => {
      expect(theme.mentionCount).toBe(theme.respondentIds.length);
    });
  });

  it('Insight·So-What 블록을 정규화한다', () => {
    expect(result.segments.length).toBeGreaterThan(0);
    expect(result.contrast).toHaveLength(golden.themes.length);
    expect(result.suggestionLinks.length).toBeGreaterThan(0);
    expect(result.transcripts).toHaveLength(golden.transcripts.length);
    expect(result.transcripts.some((row) => row.target)).toBe(true);
    expect(result.transcripts.some((row) => !row.target)).toBe(true);
  });

  it('세그먼트 버킷 합은 언급 수와 같다 — 어긋나면 화면의 두 수가 갈린다', () => {
    result.segments.forEach((segment) => {
      segment.breakdown.forEach((dimension) => {
        const total = dimension.buckets.reduce((sum, bucket) => sum + bucket.count, 0);
        expect(total).toBe(segment.mentionCount);
      });
    });
  });

  it('타겟 조건식을 화면에 그대로 싣는다 — 틀렸는지 아는 사람은 사용자뿐이다', () => {
    expect(result.targeting.criteriaText).toBe(golden.targeting.criteriaText);
    expect(result.targeting.targetDrawn + result.targeting.nonTargetDrawn)
      .toBe(golden.sampling.drawn);
  });

  it('포화가 없으면 경고를 만들지 않고, 있으면 그대로 올린다', () => {
    expect(result.saturatedThemes).toEqual([]);
    const saturated = normalizeMarketInterview({
      ...golden,
      telemetry: { ...golden.telemetry, homogeneity: {
        ...golden.telemetry.homogeneity, saturatedThemes: ['CONCERN: 가격이 비싸다'],
      } },
    });
    expect(saturated.saturatedThemes).toEqual(['CONCERN: 가격이 비싸다']);
  });

  it('뷰모델에 비율 칸을 만들지 않는다 — 만드는 순간 크기 주장이 된다', () => {
    const keys = new Set();
    const walk = (node) => {
      if (Array.isArray(node)) node.forEach(walk);
      else if (node && typeof node === 'object') {
        Object.entries(node).forEach(([key, value]) => { keys.add(key); walk(value); });
      }
    };
    walk({ ...result, telemetry: null });   // 계측은 실행 기록이지 값이 아니다
    const banned = [...keys].filter((key) => /percent|ratio|share|proportion|pct/i.test(key));
    expect(banned).toEqual([]);
  });

  it('말이 하나도 없는 카드는 버린다', () => {
    const silent = Object.fromEntries(
      ['firstImpression', 'restatement', 'like', 'concern', 'differentiation',
        'relevance', 'usageScene', 'barrier', 'suggestion'].map((key) => [key, null]),
    );
    const stripped = { ...golden, interviews: [{ ...golden.interviews[0], ...silent }] };
    expect(normalizeMarketInterview(stripped).interviews).toHaveLength(0);
  });

  it('경계 문구가 비면 조용히 지나가지 않고 크게 운다', () => {
    const stripped = normalizeMarketInterview({ ...golden, caveats: [] });
    expect(stripped.caveatsMissing).toBe(true);
    expect(stripped.caveats[0]).toContain('인용하지 마라');
  });

  it('경계 문구는 서버가 보낸 그대로 싣는다', () => {
    expect(result.caveatsMissing).toBe(false);
    expect(result.caveats).toEqual(golden.caveats);
    expect(result.caveats.some((note) => note.includes('한국미디어패널조사(KISDI)'))).toBe(true);
  });

  it('얕은 층을 드러낸다 — 조용히 채우지 않는다', () => {
    // ⚠ 골든에는 얕은 층이 없다(실제 `stratified_sample` 은 자기 셀 크기로 쿼터를 잡아서
    //    구조적으로 안 생긴다). 그래서 여기서만 합성 입력으로 시험한다 — 뱅크가 마르는
    //    큰 표본에서는 서버가 이 모양으로 보고한다.
    const shallow = normalizeMarketInterview({
      ...golden,
      sampling: { ...golden.sampling, shortCells: { '남 20대': { quota: 2, available: 0 } } },
    });
    expect(shallow.sampling.hasShortCells).toBe(true);
    expect(shallow.sampling.shortCells[0]).toMatchObject({ cell: '남 20대', quota: 2 });
    expect(result.sampling.hasShortCells).toBe(false);
  });

  it('빈 입력에는 null 을 준다', () => {
    expect(normalizeMarketInterview(null)).toBeNull();
    expect(normalizeMarketInterview('결과')).toBeNull();
  });
});

describe('renderBoard', () => {
  it('서버가 응답자에게 보인 문장과 바이트 동일하다', () => {
    // 갈라지면 화면의 「이렇게 보인다」가 거짓말이 된다.
    // 고칠 곳은 이 테스트가 아니라 `ai/app/interview/models.py` 와 짝을 맞추는 쪽이다.
    expect(renderBoard(golden.conceptBoard)).toBe(golden.conceptBoard.rendered);
  });

  it('빈 칸은 줄째로 뺀다 — 「(없음)」을 보이면 응답자가 그 공백에 반응한다', () => {
    expect(renderBoard({ conceptName: '밴드', featureSet: [], priceKrw: null }))
      .toBe('이름: 밴드\n가격: 아직 정해지지 않았습니다');
  });

  it('가격이 없으면 «미정»이라고 밝힌다 — 침묵하면 응답자가 값을 상상한다', () => {
    expect(renderBoard({ conceptName: '밴드' })).toContain('가격: 아직 정해지지 않았습니다');
  });
});

describe('mentionText', () => {
  it('언제나 「n명 중 x명」이다 — 백분율을 만들지 않는다', () => {
    expect(mentionText(7, 20)).toBe('20명 중 7명');
    expect(mentionText(7, 20)).not.toContain('%');
  });

  it('분모가 없으면 사람 수만 쓴다', () => {
    expect(mentionText(3, 0)).toBe('3명');
  });
});

describe('priceText', () => {
  it('원 단위 정수를 천 단위로 끊는다', () => {
    expect(priceText(39000)).toBe('39,000원');
  });

  it('없으면 값을 지어내지 않는다', () => {
    expect(priceText(null)).toBe('아직 정하지 않음');
  });
});

describe('profileLines', () => {
  it('못 읽은 칸은 빠진다 — 「알 수 없음」으로 채우지 않는다', () => {
    const { head, sub } = profileLines({ age: 41, gender: '여성', household: null, region: '서울' });
    expect(head).toBe('41세 · 여성 · 서울');
    expect(sub).toBe('');
  });
});
