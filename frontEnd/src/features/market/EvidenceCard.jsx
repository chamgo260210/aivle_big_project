import { Badge } from '../../shared/ui';
import { formatValue, gradeView } from './marketResult.js';

/**
 * 근거 카드 하나.
 *
 * ⚠ **값·등급·경계가 한 카드 안에 있어야 한다.** 경계를 별도 섹션으로 빼면
 * 값만 복사해 가는 것이 쉬워지고, 그러면 「전사 매출 — 시장 매출 아님」 같은 문장이
 * 사라진 채 12조라는 숫자만 남는다. 이 프로젝트가 반복해서 당한 실패가 정확히 그것이다.
 *
 * 마크업은 기존 `legal-evidence-list`(JourneyPages.jsx)를 따랐다 — 인용문·조회일·출처는
 * 이미 있던 패턴이고, **값·단위·등급 배지·경계**가 새로 얹은 것이다.
 */
export default function EvidenceCard({ item, highlighted = false, id }) {
  const grade = gradeView(item.grade);
  const host = item.sourceUrl ? safeHost(item.sourceUrl) : null;

  return (
    <article
      id={id}
      className={`market-evidence${highlighted ? ' is-highlighted' : ''}`}
      aria-current={highlighted ? 'true' : undefined}
    >
      <header className="market-evidence__head">
        <span className="market-evidence__id">{item.id}</span>
        <strong className="market-evidence__metric">
          {item.metric ?? '계량 미표기'}
          {item.subject ? ` · ${item.subject}` : ''}
        </strong>
        <Badge tone={grade.tone}>{grade.label}</Badge>
      </header>

      {/* 값과 경계를 같은 블록에 둔다 — 떼어내기 어렵게. */}
      <div className="market-evidence__value">
        <b>{formatValue(item.value, item.unit)}</b>
        {item.period ? <span className="market-evidence__period">{item.period}</span> : null}
        {item.caveats.map((caveat) => (
          <em key={caveat} className="market-evidence__caveat">{caveat}</em>
        ))}
      </div>

      {item.quote ? <blockquote>{item.quote}</blockquote> : null}

      {item.formula ? (
        <div className="market-evidence__calc">
          <code>{item.formula}</code>
          {item.assumptions.length > 0 ? (
            <ul>
              {item.assumptions.map((assumption) => <li key={assumption}>{assumption}</li>)}
            </ul>
          ) : null}
        </div>
      ) : null}

      <footer className="market-evidence__foot">
        {/* 등급이 왜 그렇게 나왔는지를 같이 보인다 — 등급명만 있으면 되짚을 수 없다. */}
        {item.gradeReason ? <span title={item.gradeReason}>{item.gradeReason}</span> : null}
        {item.retrievedAt ? <span>조회 {item.retrievedAt.slice(0, 10)}</span> : (
          <span className="market-evidence__missing">조회일 없음</span>
        )}
        {host ? (
          <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">{host}</a>
        ) : (
          <span className="market-evidence__missing">출처 링크 없음</span>
        )}
      </footer>
    </article>
  );
}

function safeHost(url) {
  try {
    return new URL(url).host;
  } catch {
    return null;
  }
}
