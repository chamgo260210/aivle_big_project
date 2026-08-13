import { COMPREHENSION_VIEW, profileLines } from './marketInterviewResult.js';

/**
 * 응답자 한 명. 프로필 6칸 + 그 사람이 실제로 한 말 9줄.
 *
 * <p>배지가 «선택»이 아니라 <b>이해도</b>인 것이 우열 조사와의 차이다. 「다른 물건으로
 * 이해」 카드가 한 장 섞여 있는 것은 실패가 아니라 <b>설계</b>다 — 그 카드가
 * 「컨셉이 나쁘다」와 「설명이 나쁘다」를 눈으로 가르게 한다.
 *
 * <p>대표 카드와 전원 응답이 <b>같은 컴포넌트를 쓴다.</b> 전원 응답 쪽만 `badge` 로
 * 타겟/비타겟을 덧붙인다 — 근거를 되짚는 자리라 누구의 말인지가 함께 보여야 한다.
 */
export default function InterviewCard({ card, badge = null }) {
  const { head, sub } = profileLines(card.profile);
  const view = COMPREHENSION_VIEW[card.comprehension] ?? COMPREHENSION_VIEW.unclassified;

  return (
    <article className="mi-interview">
      <div className="mi-interview__head">
        <span className={`mi-interview__avatar tone-${view.tone}`}>
          {card.profile.age ?? '—'}
        </span>
        <div className="mi-interview__who">
          <p className="mi-interview__line">{head}</p>
          <p className="mi-interview__sub">{sub}</p>
        </div>
        {badge ? <span className="mi-interview__badge tone-neutral">{badge}</span> : null}
        <span className={`mi-interview__badge tone-${view.tone}`}>{view.label}</span>
      </div>
      <dl className="mi-interview__answers">
        {card.answers.map((answer) => (
          <div key={answer.key}>
            <dt>{answer.label}</dt>
            <dd>&ldquo;{answer.value}&rdquo;</dd>
          </div>
        ))}
      </dl>
    </article>
  );
}
