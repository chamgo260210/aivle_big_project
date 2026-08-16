import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MarketReportView } from './MarketReportView.jsx';
import { normalizeMarketResult } from './marketResult.js';

/**
 * <b>보고서 화면</b> — 목표 문서 `docs/market-research-redesign/market-report.html` 의 구조.
 *
 * <p>AI·백엔드와 <b>같은 골든 픽스처</b>를 읽는다. 임시 상수를 만들지 않는 이유는
 * 그것이 <b>진짜 모양</b>이기 때문이다 — 픽스처가 바뀌면 여기가 즉시 빨개진다.
 */
function result(patch = null) {
  const here = dirname(fileURLToPath(import.meta.url));
  const path = resolve(here, '../../../../ai/tests/fixtures/market_research/full.json');
  const raw = JSON.parse(readFileSync(path, 'utf-8'));
  Object.keys(raw).filter((key) => key.startsWith('_')).forEach((key) => delete raw[key]);
  if (patch) patch(raw);
  return normalizeMarketResult(raw);
}

const draw = (patch) => render(
  <MarketReportView result={result(patch)} activeId={null} onJump={() => {}} />,
);

describe('MarketReportView — 봉투가 실어 준 보고서 글', () => {
  it('`report` 가 **null 이면 예전 화면 그대로** 나온다', () => {
    // ⚠ 여섯 가지 경우(BM 모드·재채점·예산 부족 …)에 글이 오지 않는다. 물러서기가 정상 경로다.
    const { container } = draw((raw) => { raw.report = null; });
    expect(container.querySelectorAll('[id^="sec-"]').length).toBe(9);
    expect(container.querySelector('.mreport')).toBeNull();
  });

  it('글이 온 절만 **번호·제목·큰 수 카드·글** 순서로 선다', () => {
    const { container } = draw();
    const sections = [...container.querySelectorAll('.mreport section')];
    // 픽스처가 실어 준 절만큼만 선다 — 빈 절을 아홉 개 세우지 않는다.
    expect(sections.length).toBeGreaterThan(0);
    const first = sections[0];
    expect(first.querySelector('h2 .n').textContent).toBe('1');
    expect(first.querySelector('h2').textContent).toContain('시장 크기');
    // 절은 **접히는 상자**다(판 ㊻) — 첫 절만 열려 있다.
    expect(first.querySelector('.secbox').open).toBe(true);
    // 순서 — 큰 수 카드가 글보다 «위»다.
    const order = [...first.querySelector('.secbox').children]
      .map((el) => el.className || el.tagName);
    expect(order.indexOf('kpi')).toBeLessThan(order.indexOf('md'));
    // 글의 표가 **React 표**로 선다(`dangerouslySetInnerHTML` 아님).
    expect(first.querySelector('.md .tw table thead th.num')).toBeTruthy();
  });

  it('★ **경고를 뺀 적이 없다** — 유령 수와 컨셉 누출을 글 «위»에 적는다', () => {
    draw();
    expect(screen.getByText(/이 글은 AI 가 조사 결과를 읽고 쓴 것입니다/)).toBeTruthy();
    expect(screen.getByText(/재료에 없는 수가 16개/)).toBeTruthy();
    expect(screen.getByText(/사업가가 «입력한» 가정이 조사 결과처럼 섞인 것 1개/)).toBeTruthy();
    // 봉투가 보낸 머리말도 경계 표시다 — 재료 건수·쓴 모델이 거기 있다.
    expect(screen.getByText(/인용 대조를 통과한 사실/)).toBeTruthy();
  });

  it('센 수가 0이어도 **「AI 가 쓴 글」이라는 사실은 말한다**', () => {
    draw((raw) => { raw.report.unverifiedNumbers = 0; raw.report.conceptLeaks = 0; });
    expect(screen.getByText(/이 글은 AI 가 조사 결과를 읽고 쓴 것입니다/)).toBeTruthy();
    expect(screen.queryByText(/재료에 없는 수가/)).toBeNull();
  });

  it('★ 8·9절은 **기계 것이 먼저**고 모델 글에는 이름이 붙는다', () => {
    const { container } = draw();
    const 절 = [...container.querySelectorAll('.mreport section')]
      .find((el) => el.querySelector('h2').textContent.includes('못 구한 것'));
    expect(절).toBeTruthy();
    const order = [...절.querySelector('.secbox').children]
      .map((el) => el.className || el.tagName);
    // 기계(처방 표) → 「AI 가 쓴 정리」 표시 → 모델 글.
    expect(order.indexOf('byai')).toBeLessThan(order.indexOf('md'));
    expect(order.indexOf('mr-rx')).toBeLessThan(order.indexOf('byai'));
    expect(절.textContent).toContain('인용 대조를 거친');
  });

  it('꼬리말은 **지금 늘 null 이라** 그 블록을 안 그린다', () => {
    const { container } = draw();
    expect(container.querySelector('.mreport footer .md')).toBeNull();
    // 왔을 때는 뜬다.
    const 있음 = draw((raw) => { raw.report.tail = '**보태는 말**'; });
    expect(있음.container.querySelector('.mreport footer .md')).toBeTruthy();
  });

  it('⚠ 기존 화면을 **지우지 않는다** — 「근거로 검산하기」 안에 그대로 있다', () => {
    const { container } = draw();
    const check = container.querySelector('details.mreport-check');
    expect(check).toBeTruthy();
    // 등급·근거 표·못 구한 것이 사는 자리. 접혀 있을 뿐 아홉 절이 다 있다.
    expect(check.querySelectorAll('[id^="sec-"]').length).toBe(9);
  });
});
