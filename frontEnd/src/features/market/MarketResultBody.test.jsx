import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MarketResultBody } from './MarketResultBody.jsx';
import { normalizeMarketResult } from './marketResult.js';

/**
 * **AI·백엔드와 같은 골든 픽스처**로 화면을 그린다. (판 ㊸)
 *
 * <p>왜 이 파일이 필요했나 — 이 판까지 화면을 눈으로 보는 길은 `/wireframe.html` 하나였는데
 * 그 URL 은 `public/wireframe.html`(손으로 쓴 정적 목업)이 가리고 있어 <b>제품 부품을 한 번도
 * 안 그리고 있었다.</b> 눈으로 보는 것을 대신하지는 않지만, <b>「그렸는데 빠졌다」는 여기서 잡는다.</b>
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
  <MarketResultBody result={result(patch)} activeId={null} onJump={() => {}} />,
);

describe('MarketResultBody — 판 ㊸ 배선', () => {
  it('목차가 10줄이고 「찾지 못한 것」이 맨 끝이다', () => {
    const { container } = draw();
    const ids = [...container.querySelectorAll('[id^="sec-"]')].map((el) => el.id);
    expect(ids).toHaveLength(10);
    // ⚠ 순서가 곧 절 번호다. 중간에 끼우면 기존 번호가 밀린다.
    expect(ids.at(-1)).toBe('sec-NOT_FOUND');
    expect(ids).toContain('sec-CHANNEL');
    expect(ids).toContain('sec-UNIT_ECONOMICS');
    expect(ids).toContain('sec-REGULATION');
  });

  it('2절 판단은 **결론까지** 그린다 — 계산식만 남으면 반쪽이다', () => {
    draw();
    expect(screen.getByText(/이 가격이 시장 어디에 서 있나/)).toBeTruthy();
    expect(screen.getByText(/값이 아닌 이유가 서지 않으면/)).toBeTruthy();
    // 비교쌍이 없어 **못 쓴 갈래**도 나온다 — 침묵을 「해당 없음」으로 읽히게 두지 않는다.
    expect(screen.getByText(/안 씁니다/)).toBeTruthy();
  });

  it('8절 처방은 「어디서 구하나」까지 그린다', () => {
    draw();
    const card = screen.getByText(/못 구한 것 — 어디서 구하나/).closest('section, div');
    expect(within(card).getByText(/공중위생관리법/)).toBeTruthy();
  });

  it('9절은 미는 것과 흔드는 것을 갈라 그린다', () => {
    draw();
    expect(screen.getByText(/이 사업안을 미는 것과 흔드는 것/)).toBeTruthy();
    // 픽스처의 9절은 「흔듦」 한 줄이다 — 갈래 상자가 그 이름으로 선다.
    const card = screen.getByText(/이 사업안을 미는 것과 흔드는 것/).closest('section, div');
    expect(within(card).getByRole('heading', { level: 4, name: /흔드는 것/ })).toBeTruthy();
    expect(within(card).getByText(/값만으로는 고를 이유가 없어요|경쟁 구독료/)).toBeTruthy();
  });

  it('2·8·9절이 **안 온 실행**에서는 그 자리가 아예 없다 — 빈 카드를 세우지 않는다', () => {
    draw((raw) => {
      raw.judgment = null;
      raw.prescriptions = null;
      raw.synthesis = null;
    });
    expect(screen.queryByText(/이 가격이 시장 어디에 서 있나/)).toBeNull();
    expect(screen.queryByText(/못 구한 것 — 어디서 구하나/)).toBeNull();
    expect(screen.queryByText(/이 사업안을 미는 것과 흔드는 것/)).toBeNull();
  });

  it('덜 조사된 사유가 **화면에 선다** — 원장에만 남기지 않는다', () => {
    // ⚠ 이것이 없으면 예산이 끊겨 절이 빈 것과 **정말 자료가 없는 것**이 같아 보인다.
    draw((raw) => {
      raw.degradations = [
        { stage: 'sections', code: 'SECTIONS_TRUNCATED', detail: '예산 상한 40건까지만 읽었다' },
        // 목록에 없는 코드는 안 그린다 — 전부 그리면 진짜 경고가 안 읽힌다.
        { stage: 'harness', code: 'NOT_WIRED', detail: '저장된 수집 위에서 돈다' },
      ];
    });
    expect(screen.getByText(/이 조사가 다 돌지 못했어요/)).toBeTruthy();
    expect(screen.getByText(/문서를 다 읽지 못했어요/)).toBeTruthy();
    expect(screen.queryByText(/저장된 수집 위에서 돈다/)).toBeNull();
  });

  it('요약이 죽은 실행은 **죽었다고 말한다** — 카드가 사라지면 사용자는 있었는지도 모른다', () => {
    // ⚠ 유료 스모크(2026-08-15)의 **유일한 실패**가 이것인데 화면 0곳에 닿았다.
    //    요약 카드는 summary 가 null 이면 통째로 안 그려지므로, 이 줄이 없으면
    //    「요약이 없다」와 「요약을 못 만들었다」가 사용자에게 같은 말이 된다.
    draw((raw) => {
      raw.summary = null;
      raw.degradations = [
        { stage: 'summary', code: 'CHECK_FAILED', detail: '검사 미통과 3회 — 요약을 버리고 카드만 낸다' },
      ];
    });
    expect(screen.getByText(/요약 문장이 검사를 통과하지 못해 버렸어요/)).toBeTruthy();
    // 값과 근거는 살아 있다고 말해 준다 — 「전부 틀렸다」로 읽히면 그것도 거짓이다.
    expect(screen.getByText(/값과 근거는 그대로예요/)).toBeTruthy();
  });

  it('옛 결과의 없는 과목은 **「안 쟀다」고 말한다** — 말없이 비워 두지 않는다', () => {
    // 판 ㊸ 이전 결과는 성적표가 7과목이라 새 셋이 아예 없다.
    const { container } = draw((raw) => {
      raw.scorecard = raw.scorecard.filter(
        (row) => !['CHANNEL', 'UNIT_ECONOMICS', 'REGULATION'].includes(row.subject),
      );
    });
    // 줄은 그대로 열 개다 — 목차가 결과마다 달라지면 그것도 거짓말이다.
    expect(container.querySelectorAll('[id^="sec-"]')).toHaveLength(10);
    expect(screen.getAllByText(/이 조사에는 없던 과목이에요/).length).toBe(3);
  });

  it('구성비 표가 반쪽이면 **그렇다고 말한다** — 반쪽 표는 빈칸보다 나쁘다', () => {
    // 합이 100%가 아닌 3행짜리 채널 표. 실측된 병이다 — 채널 절 합이 47%였고
    // 숨은 특약점 29.65%가 1위 대형마트 31.05%와 대등했다.
    const 행 = (id, subject, value) => ({
      id, kind: '관측', metric: '매출처별 판매비중', subject, period: '2025',
      value, unit: '%', grade: '확정', gradeReason: '등급표:public_filing',
      sourceUrl: 'https://kind.krx.co.kr/x', sourceKind: 'public_filing', retrievedAt: null,
      quote: null, caveats: [], formula: null, inputs: null, materialIds: [], assumptions: [],
      section: 'CHANNEL', placement: 'COMPETITOR_FIRM', issuer: '예시사',
      tableKey: 'T-1|매출처별 판매비중|2025', raw: `${value}%`,
    });
    const { container } = draw((raw) => {
      raw.evidence.push(행('t-1', '대형마트', 31.05), 행('t-2', '대리점', 10.21), 행('t-3', '편의점', 5.99));
    });
    fireEvent.click(container.querySelector('#sec-CHANNEL button'));
    expect(screen.getByText(/47\.3% 로/)).toBeTruthy();
    expect(screen.getByText(/보이지 않는 행이 있고, 그것이 1위일 수도 있다/)).toBeTruthy();
    // 발행사 꼬리표 — **두 회사의 표가 하나로 읽히는 것을 막는다.**
    expect(screen.getAllByText('경쟁사(예시사)').length).toBe(3);
  });
});
