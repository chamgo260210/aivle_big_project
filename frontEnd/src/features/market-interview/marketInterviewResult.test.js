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

  // ── 2026-08-15: 「물어본 사람이 맞나」와 「말 안 함 ≠ 분류 못 함」 ────────────
  //
  // 유료 n=40 실행에서 타겟 0명 / 비타겟 40명이 뽑혔는데 화면 경고가 0건이었고,
  // USAGE_SCENE 은 2명만 분류됐는데 화면은 「쓸 장면을 말한 사람이 없다」고 단언했다.
  // 아래 넷이 그 둘을 막는다.

  it('타겟이 모자란 것을 shortfall 로 판정하지 않는다 — 그 칸은 언제나 0이다', () => {
    // 서버가 `표본크기 - 뽑은수` 로 계산하는데 타겟이 모자라면 비타겟으로 채우므로
    // 값이 0으로 남는다. 실측 판이 정확히 그 모양이었다: 타겟 0 / 비타겟 40 / shortfall 0.
    const starved = normalizeMarketInterview({
      ...golden,
      targeting: { ...golden.targeting, targetRequested: 16, targetDrawn: 0,
        nonTargetDrawn: 20, shortfall: 0 },
    });
    expect(starved.targeting.shortfall).toBe(0);      // 옛 판정은 여전히 침묵한다
    expect(starved.targeting.targetShort).toBe(true); // 새 판정은 잡는다
    expect(starved.targeting.targeted).toBe(true);
  });

  it('조건을 안 건 조사에는 「타겟이 없다」고 말하지 않는다', () => {
    const anyone = normalizeMarketInterview({
      ...golden,
      targeting: { ...golden.targeting, targetRequested: 0, targetDrawn: 0 },
    });
    expect(anyone.targeting.targeted).toBe(false);
    expect(anyone.targeting.targetShort).toBe(false);
  });

  it('축마다 분류된 사람 수를 센다 — 주제가 겹쳐도 한 번만 센다', () => {
    result.sections.forEach((section) => {
      const rows = [...section.themes, ...section.hiddenThemes];
      const union = new Set(rows.flatMap((theme) => theme.respondentIds));
      expect(section.classified).toBe(union.size);
      // 언급 수의 합보다 크지 않다 — 한 사람이 여러 주제에 들면 합이 부풀기 때문이다.
      expect(section.classified)
        .toBeLessThanOrEqual(rows.reduce((sum, theme) => sum + theme.mentionCount, 0));
    });
  });

  it('분류가 답한 사람 수에 크게 못 미치면 그 축에 표시를 남긴다', () => {
    const thin = normalizeMarketInterview({
      ...golden,
      themes: [{ axis: 'USAGE_SCENE', label: '등하교', mentionCount: 2,
        respondentIds: ['R1', 'R2'], resolvedCount: 0, quote: '학원 갈 때' }],
    });
    const section = thin.sections.find((row) => row.axis === 'USAGE_SCENE');
    expect(section.classified).toBe(2);
    expect(section.thinCoverage).toBe(true);
  });

  it('빈 축 문구가 「말한 사람이 없다」라고 단언하지 않는다', () => {
    // 아홉 문항은 전원이 답을 쓰고 온다. 비는 이유는 거의 언제나 분류 실패다.
    AXIS_VIEW.forEach((view) => {
      // 문구 자체가 아니라 «뜻»을 검사한다 — 말투는 바뀌어도 이 규칙은 남아야 한다.
      expect(view.empty).not.toMatch(/말한 사람이 없/);
      expect(view.empty).toContain('분류된 답이 없');
    });
  });

  it('대안이 0건이어도 그 칸에 답을 쓴 사람 수는 센다', () => {
    // `alternatives` 계약에는 명단이 없어 분류 인원을 못 세지만, 「말한 사람이 없다」는
    // 거짓 단언은 이것만으로 막힌다 — 실측 판에서 40명 전원이 답을 썼는데 0건이었다.
    expect(result.relevanceAnswered)
      .toBe(golden.transcripts.filter((row) => row.relevance).length);
    expect(result.relevanceAnswered).toBeGreaterThan(0);
  });

  // ── 2026-08-15: 「이 조사가 센 것」 — 나열을 정보로 ──────────────────
  //
  // 화면이 9문항 순서대로 주제를 늘어놓아 「뭐 어쩌라는 건지 모르겠다」는 상태였다.
  // 맨 위 세 줄은 **해석이 아니라 집계**여야 하고, 아래 절의 수와 어긋나면 안 된다.

  it('맨 위 세 줄은 아래 절의 수와 정확히 같다 — 따로 세지 않는다', () => {
    const { barrier, alternative } = result.headline;
    const section = result.sections.find((row) => row.axis === 'BARRIER');
    const top = [...section.themes, ...section.hiddenThemes]
      .sort((a, b) => b.mentionCount - a.mentionCount)[0];
    expect(barrier.label).toBe(top.label);
    expect(barrier.count).toBe(top.mentionCount);
    expect(barrier.resolved).toBe(top.resolvedCount);
    expect(barrier.unresolved).toBe(top.mentionCount - top.resolvedCount);
    expect(alternative.label).toBe(result.alternatives[0].label);
  });

  it('타겟 분모를 함께 싣는다 — 없으면 「20명 중 19명」이 타겟 수 행세를 한다', () => {
    const { barrier } = result.headline;
    const row = result.contrast.find((item) => item.label === barrier.label);
    expect(barrier.targetCount).toBe(row.targetCount);
    expect(barrier.targetCount).toBeLessThanOrEqual(barrier.count);
  });

  it('장벽과 가장 많이 겹치는 제안을 고르고, 겹침 수도 명단에서 센다', () => {
    const { barrier, suggestion } = result.headline;
    const members = new Set(
      golden.themes.find((t) => t.axis === 'BARRIER' && t.label === barrier.label).respondentIds,
    );
    const picked = golden.themes.find(
      (t) => t.axis === 'SUGGESTION' && t.label === suggestion.label,
    );
    const overlap = picked.respondentIds.filter((id) => members.has(id)).length;
    expect(suggestion.overlap).toBe(overlap);
    // 다른 제안이 더 많이 겹치면 안 된다.
    golden.themes.filter((t) => t.axis === 'SUGGESTION').forEach((other) => {
      expect(other.respondentIds.filter((id) => members.has(id)).length)
        .toBeLessThanOrEqual(overlap);
    });
  });

  it('겹침이 제안 인원의 절반에 못 미치면 연결을 «주장하지 않는다»', () => {
    // 실측 판은 13/14 라 강하지만, 이 규칙은 다른 사업안에도 돈다. 겹침이 3명인데도
    // 「가장 많이 요청한 것은 X」로 똑같이 확신 있게 찍히는 것이 이 저장소의 고질이다.
    const weak = normalizeMarketInterview({
      ...golden,
      themes: [
        { axis: 'BARRIER', label: '비싸다', mentionCount: 2, respondentIds: ['R1', 'R2'],
          resolvedCount: 1, quote: '비싸요' },
        { axis: 'SUGGESTION', label: '색을 바꿔라', mentionCount: 6,
          respondentIds: ['R1', 'R3', 'R4', 'R5', 'R6', 'R7'], resolvedCount: 0, quote: '색' },
      ],
    });
    expect(weak.headline.suggestion.overlap).toBe(1);
    expect(weak.headline.suggestion.linked).toBe(false);
  });

  it('「아직 못 물어본 것」은 엔진 사정이 아니라 사업 질문이다', () => {
    const rows = result.openQuestions;
    expect(rows.length).toBeGreaterThan(0);
    rows.forEach((row) => {
      // 「다음에 이걸 확인해라」로 끝나야 한다 — 「이걸 해라」(권고)는 우리가 못 쓴다.
      expect(row).toMatch(/안 물었어요|묻지 않았/);
      // 엔진 사정(분류·이름표·포화)은 신뢰도 서랍의 몫이다. 여기 또 적으면 중복이 된다.
      expect(row).not.toMatch(/분류|이름표|포화/);
      // 마크다운 별표가 그대로 화면에 나가면 안 된다.
      expect(row).not.toContain('**');
    });
  });

  it('장벽 주제가 없으면 조용히 비운다 — 지어내지 않는다', () => {
    const empty = normalizeMarketInterview({ ...golden, themes: [] });
    expect(empty.headline).toBeNull();
    expect(empty.openQuestions).toEqual([]);
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
    expect(stripped.caveats[0]).toMatch(/인용하지 마/);
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
