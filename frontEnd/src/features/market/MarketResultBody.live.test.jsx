import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MarketResultBody } from './MarketResultBody.jsx';
import { normalizeMarketResult } from './marketResult.js';

/**
 * **유료 실행이 실제로 낸 봉투**를 화면 부품에 넣는다. (판 ㊸ 보완)
 *
 * <p>골든 픽스처(`MarketResultBody.test.jsx`)는 손으로 썼다. 그것이 정답이지만, 대가로
 * <b>손이 상상하지 못한 조합은 영원히 안 들어온다</b> — 값이 `null` 인 승격 카드,
 * 인용이 없는 행, `summary` 가 통째로 죽은 실행 같은 것들이다. 자바 쪽에도 같은 이유로
 * `MarketResearchLiveEnvelopeTests` 를 뒀다.
 *
 * <p><b>봉투 파일이 없으면 건너뛴다.</b> 유료 실행은 아무 때나 돌 수 없다.
 */
const HERE = dirname(fileURLToPath(import.meta.url));
const 봉투 = resolve(
  HERE, '../../../../ai/app/research/research2/runs-generated/p43-smoke-01-validation.json',
);

const 있다 = existsSync(봉투);
const 실측 = () => normalizeMarketResult(JSON.parse(readFileSync(봉투, 'utf-8')));

describe.skipIf(!있다)('MarketResultBody — 실측 봉투', () => {
  const draw = () => render(
    <MarketResultBody result={실측()} activeId={null} onJump={() => {}} />,
  );

  it('열 과목이 다 서고 터지지 않는다', () => {
    const { container } = draw();
    expect(container.querySelectorAll('[id^="sec-"]')).toHaveLength(10);
  });

  it('2·8·9절이 실측 값으로 그려진다', () => {
    draw();
    expect(screen.getByText(/이 가격이 시장 어디에 서 있나/)).toBeTruthy();
    expect(screen.getByText(/못 구한 것 — 어디서 구하나/)).toBeTruthy();
    expect(screen.getByText(/이 사업안을 미는 것과 흔드는 것/)).toBeTruthy();
  });

  it('★ 수요 줄이 **두 수를 모순 없이** 말한다', () => {
    // 실측 결함이었다: 「근거 0건」 배지 옆에 「근거 13건 ▾」 단추가 섰다.
    draw();
    expect(screen.getByText(/정황 근거/)).toBeTruthy();
    // 파이썬 `None` 이 한국어 문장에 박히던 자리.
    expect(screen.queryByText(/최고 등급 None/)).toBeNull();
  });

  it('★ 요약이 죽은 실행은 **죽었다고 말한다**', () => {
    // 이 실행의 `summary` 는 CHECK_FAILED 로 null 이다. 카드가 사라지므로
    // 사유가 안 뜨면 사용자는 요약이 있어야 한다는 사실 자체를 모른다.
    draw();
    expect(실측().summary).toBeFalsy();
    expect(screen.getByText(/요약 문장이 검사를 통과하지 못해 버렸어요/)).toBeTruthy();
  });

  it('★ 경계 문장이 **화면에 닿는다** — 봉투에만 있고 화면에 없으면 지운 것과 같다', () => {
    // `CLAUDE.md` §5-8: 경계 표시는 절대 제거하지 않는다. 이 봉투는 88장 중 48장이
    // 경계를 들고 있다(문장 61개). 접이식 안에 있어 **펼쳐야** 보이므로, 펼친 뒤 센다.
    const { container } = draw();
    // ⚠ **아코디언이다** — 한 번에 한 절만 열린다. 전부 눌러 놓고 한 번에 세면 마지막
    //   절(「찾지 못한 것」)만 열린 채로 0 이 나온다. 절마다 열어서 센다.
    let 본 = 0;
    container.querySelectorAll('[id^="sec-"]').forEach((sec) => {
      const button = sec.querySelector('[aria-expanded]');
      if (!button) return;
      if (button.getAttribute('aria-expanded') === 'false') fireEvent.click(button);
      본 += sec.querySelectorAll('.mr-caveat').length;
      if (button.getAttribute('aria-expanded') === 'true') fireEvent.click(button);
    });
    const 봉투경계 = 실측().evidence.reduce((n, e) => n + (e.caveats?.length || 0), 0);
    expect(봉투경계).toBeGreaterThan(0);
    expect(본).toBeGreaterThan(0);
  });

  it('★ 강조 별표가 **글자로** 남아 있지 않다', () => {
    // 실측(2026-08-15): 8절 처방 표의 「왜」 칸만 Emphasis 를 안 거쳐
    // 「**어디를 볼지 적는다**」가 별표째 찍혔다. 문구를 그대로 비교하는 검사는
    // 이 부류를 **구조적으로 못 잡는다** — 기대 문자열에도 별표를 적기 때문이다.
    // 그래서 화면 전체를 훑는다.
    const { container } = draw();
    const 남은 = (container.textContent || '').match(/\*\*[^*\n]{1,40}\*\*/g) || [];
    expect(남은).toEqual([]);
  });

  it('★ 자릿수가 깨진 수가 화면에 없다', () => {
    // 「8조 9,854」 + 「억원」 을 갈라 읽어 8.0e20 원(80,000경)이 나갔던 자리.
    const 값 = 실측().evidence.map((e) => e.value).filter((v) => typeof v === 'number');
    expect(값.length).toBeGreaterThan(0);
    // 이 컨셉에서 1경(1e16) 을 넘는 원화 값은 없다 — 넘으면 배율을 두 번 곱한 것이다.
    expect(Math.max(...값)).toBeLessThan(1e16);
  });
});
