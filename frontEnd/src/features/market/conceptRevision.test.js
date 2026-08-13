import { describe, expect, it } from 'vitest';
import { highlightChanges, narrativeParts, normalizeDeltaLegal } from './conceptRevision.js';

/**
 * 초록 표시는 <b>찾았을 때만</b> 붙는다.
 *
 * 못 찾는 것이 정상이라는 점이 이 함수의 요점이다 — 다듬기가 건드리는 것은 가설이라
 * 컨셉 서술문에 그 문자열이 없을 수 있다. 그때 원문을 손대면 없는 문장이 만들어진다.
 */
describe('highlightChanges', () => {
  it('원문에서 찾은 구간만 번호를 달고 나머지는 원문 그대로다', () => {
    const parts = highlightChanges('바쁜 1인 가구 직장인에게 정찬급 냉동식을 드려요.', [
      { after: '1인 가구 직장인' },
    ]);
    expect(parts).toEqual([
      { text: '바쁜 ', ref: null },
      { text: '1인 가구 직장인', ref: 1 },
      { text: '에게 정찬급 냉동식을 드려요.', ref: null },
    ]);
    expect(parts.map((part) => part.text).join('')).toBe('바쁜 1인 가구 직장인에게 정찬급 냉동식을 드려요.');
  });

  it('못 찾으면 원문 한 조각뿐이다 — 짜깁기하지 않는다', () => {
    const text = '바쁜 직장인에게 냉동식을 드려요.';
    expect(highlightChanges(text, [{ after: '9,500원대' }, { after: '가' }, { after: null }]))
      .toEqual([{ text, ref: null }]);
  });

  it('겹치는 자리는 앞선 것만 남고 번호는 변경 목록의 순서를 따른다', () => {
    const parts = highlightChanges('저나트륨 건강식 라인', [
      { after: '없는 말' }, { after: '저나트륨 건강식' }, { after: '건강식' },
    ]);
    expect(parts).toEqual([
      { text: '저나트륨 건강식', ref: 2 },
      { text: ' 라인', ref: null },
    ]);
  });
});

/**
 * 서술문은 <b>서버가 검증한 것만</b> 온다. 화면은 그것을 그리기만 한다.
 *
 * 비어 있으면 `null` 이어야 한다 — 빈 배열을 조각 목록으로 넘기면 화면이 빈 문단을 세우고,
 * 그것이 「컨셉이 비었다」로 읽힌다.
 */
describe('narrativeParts', () => {
  it('조각을 화면이 읽는 모양으로 바꾼다 — highlightChanges 와 같은 모양이다', () => {
    expect(narrativeParts([
      { text: '바쁜 ', changeRef: null },
      { text: '1인 가구 직장인', changeRef: 1 },
      { text: '에게 팔아요.', changeRef: null },
    ])).toEqual([
      { text: '바쁜 ', ref: null },
      { text: '1인 가구 직장인', ref: 1 },
      { text: '에게 팔아요.', ref: null },
    ]);
  });

  it('없거나 비었으면 null 이다 — 화면이 칸 나열로 폴백한다', () => {
    expect(narrativeParts(undefined)).toBeNull();
    expect(narrativeParts([])).toBeNull();
    expect(narrativeParts([{ text: '', changeRef: 1 }])).toBeNull();
  });

  it('정수가 아닌 참조는 안 바뀐 구간으로 떨어뜨린다 — 엉뚱한 자리를 물들이지 않는다', () => {
    expect(narrativeParts([{ text: '한 문장', changeRef: '1' }]))
      .toEqual([{ text: '한 문장', ref: null }]);
  });
});

/**
 * 법률 카드는 <b>조항과 소견이 이어져 있을 때만</b> 「왜 걸리는가」를 말한다.
 *
 * 이어짐이 없으면 예전처럼 법명·조항만 세운다 — 조문 해설로 대신 채우지 않는다.
 */
describe('normalizeDeltaLegal', () => {
  const review = (clause) => ({
    legalReview: { productionStatus: 'NEEDS_FACTS', officialEvidenceReferences: [clause] },
    hypothesisTypes: ['PRICE'],
  });

  it('조항에 이어진 소견과 상태 배지를 편다', () => {
    const legal = normalizeDeltaLegal(review({
      lawName: '식품표시광고법', articleReference: '제6조', conceptStatus: 'REFLECTED',
      findings: [{ topic: '「저나트륨」 표시 기준', text: '기준에 못 미쳐 표현을 고쳤어요.' }],
    }));
    expect(legal.clauses[0].lawName).toBe('식품표시광고법');
    expect(legal.clauses[0].status).toEqual({ label: '컨셉에 반영했어요', tone: 'success' });
    expect(legal.clauses[0].findings).toEqual([
      { topic: '「저나트륨」 표시 기준', text: '기준에 못 미쳐 표현을 고쳤어요.' },
    ]);
    expect(legal.changed).toEqual(['가격']);
  });

  it('모르는 상태에는 배지를 안 단다 — 없는 판정을 그리면 근거 없는 「확인됨」이 된다', () => {
    const legal = normalizeDeltaLegal(review({ lawName: '전자상거래법' }));
    expect(legal.clauses[0].status).toBeNull();
    expect(legal.clauses[0].findings).toEqual([]);
  });

  it('본문 없는 소견은 버린다 — 제목만 있는 줄은 「왜」를 말하지 않는다', () => {
    const legal = normalizeDeltaLegal(review({
      lawName: '축산물 위생관리법', findings: [{ topic: '허가' }, { topic: '', text: '허가가 필요해요.' }],
    }));
    expect(legal.clauses[0].findings).toEqual([{ topic: '', text: '허가가 필요해요.' }]);
  });

  it('검토 자체가 없으면 null 이다 — 「아직 안 돌았다」와 「걸린 법이 없다」는 다르다', () => {
    expect(normalizeDeltaLegal(null)).toBeNull();
    expect(normalizeDeltaLegal({ legalReview: null })).toBeNull();
  });
});
