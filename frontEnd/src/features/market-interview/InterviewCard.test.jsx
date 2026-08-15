import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import InterviewCard from './InterviewCard.jsx';
import { InterviewFootnote } from './MarketInterviewPage.jsx';
import { normalizeMarketInterview } from './marketInterviewResult.js';

const here = dirname(fileURLToPath(import.meta.url));
const golden = normalizeMarketInterview(JSON.parse(readFileSync(
  resolve(here, '../../../../ai/tests/fixtures/market_interview/interview.json'), 'utf-8',
)));

describe('InterviewCard', () => {
  it('프로필과 그 사람이 실제로 한 말을 함께 보인다', () => {
    const card = golden.interviews[0];
    render(<InterviewCard card={card} />);
    expect(screen.getByText(/세 ·/)).toBeInTheDocument();
    expect(screen.getByText('첫인상')).toBeInTheDocument();
    expect(screen.getAllByRole('definition').length).toBe(card.answers.length);
  });

  it('배지는 «선택»이 아니라 이해도다 — 그 카드가 설명의 문제를 눈으로 보게 한다', () => {
    const misread = golden.interviews.find((card) => card.comprehension === 'misunderstood');
    expect(misread).toBeTruthy();
    render(<InterviewCard card={misread} />);
    expect(screen.getByText('다른 물건으로 이해')).toBeInTheDocument();
  });

  it('나이를 못 읽었으면 자리를 «—» 로 둔다', () => {
    const card = { ...golden.interviews[0], profile: { ...golden.interviews[0].profile, age: null } };
    render(<InterviewCard card={card} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});

describe('InterviewFootnote', () => {
  it('결과가 없어도 면책은 남는다', () => {
    render(<InterviewFootnote result={null} />);
    expect(screen.getByText(/한국미디어패널조사\(KISDI\)/)).toBeInTheDocument();
    expect(screen.getByText(/백분율로 환산하지 마/)).toBeInTheDocument();
  });

  it('서버가 보낸 경계 문구를 그대로 편다', () => {
    render(<InterviewFootnote result={golden} />);
    expect(screen.getByText(`이 결과를 읽는 법 ${golden.caveats.length}가지`)).toBeInTheDocument();
    expect(screen.getByText(/외적 타당성 시험을 거치지 않았다/)).toBeInTheDocument();
  });
});
