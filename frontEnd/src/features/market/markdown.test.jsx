import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import Markdown from './markdown.jsx';
import { parseBlocks } from './markdownBlocks.js';

/**
 * 봉투가 실어 주는 <b>보고서 글</b>을 그리는 작은 렌더러.
 *
 * <p>⚠ <b>`dangerouslySetInnerHTML` 을 쓰지 않는다.</b> 모델이 쓴 글을 HTML 로 부어 넣으면
 * 모델 출력이 곧 DOM 이 된다 — 그래서 여기 표·링크가 <b>React 요소</b>인지 본다.
 */
describe('markdown — 보고서 글 렌더러', () => {
  it('표는 `---:` 을 **오른쪽 정렬**로, 「출처」 열을 작은 회색으로 그린다', () => {
    const md = [
      '| 시장·범주 | 연도 | 규모 | 출처 |',
      '|---|---:|---:|---|',
      '| 국내 HMR 판매액 | 2025 | 6조 8천억 원 | [7대 이슈](https://www.atfis.or.kr/a) |',
    ].join('\n');
    const { container } = render(<Markdown text={md} />);
    const th = [...container.querySelectorAll('th')].map((el) => el.className);
    expect(th).toEqual(['', 'num', 'num', 'src']);
    const td = [...container.querySelectorAll('td')].map((el) => el.className);
    expect(td).toEqual(['', 'num', 'num', 'src']);
    // 표는 가로 스크롤 상자 안에 있다 — 다섯 칸짜리 표가 화면 밖으로 나가지 않게.
    expect(container.querySelector('.tw table')).toBeTruthy();
  });

  it('링크는 **진짜 `<a>`** 이고 새 창으로 연다', () => {
    render(<Markdown text="출처는 [7대 이슈](https://www.atfis.or.kr/a) 다." />);
    const a = screen.getByRole('link', { name: '7대 이슈' });
    expect(a.getAttribute('href')).toBe('https://www.atfis.or.kr/a');
    expect(a.getAttribute('target')).toBe('_blank');
  });

  it('`javascript:` 같은 주소는 링크로 만들지 않되 **글자는 남긴다**', () => {
    const { container } = render(<Markdown text="[누르지 마시오](javascript:alert(1))" />);
    expect(container.querySelector('a')).toBeNull();
    // ⚠ 조용히 버리지 않는다 — 사라지면 「무엇이 있었는지」조차 모른다.
    expect(container.textContent).toContain('누르지 마시오');
  });

  it('제목·굵게·불릿·인용을 그린다', () => {
    const md = [
      '### 1.1 국내 간편식 시장',
      '',
      '**국내 판매액**과 출하액은 기준이 다르다.',
      '',
      '- 첫째 줄',
      '- 둘째 줄',
      '',
      '> 이 값은 전망치다.',
    ].join('\n');
    const { container } = render(<Markdown text={md} />);
    expect(screen.getByRole('heading', { level: 3 }).textContent).toBe('1.1 국내 간편식 시장');
    expect(container.querySelector('p b').textContent).toBe('국내 판매액');
    expect(container.querySelectorAll('li')).toHaveLength(2);
    expect(container.querySelector('blockquote').textContent).toContain('전망치');
  });

  it('빈 글이면 **아무것도 그리지 않는다**', () => {
    const { container } = render(<Markdown text={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('표가 아닌 `|` 줄도 **버리지 않는다** — 글자로 남는다', () => {
    const blocks = parseBlocks('| 머리글만 있고 구분줄이 없다 |');
    expect(blocks).toHaveLength(1);
    expect(blocks[0].type).toBe('paragraph');
  });
});
