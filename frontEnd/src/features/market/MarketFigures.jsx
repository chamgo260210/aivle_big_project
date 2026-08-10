import { Badge, Card } from '../../shared/ui';
import { abbreviateKrw, formatValue, gradeView } from './marketResult.js';

const STEPS = [
  ['TAM', 'tam', '전국 시장'],
  ['SAM', 'sam', '좁힌 시장'],
  ['SOM', 'som', '우리가 먹을 몫'],
];

/**
 * 시장 규모 — **세 수의 관계**를 계단으로 보인다.
 *
 * ⚠ SOM 은 엔진이 산출하지 않는다(`serialize.py` 의 `"som": None` 은 고의다 — SOM 식이
 * 아예 없다). 그래서 **칸을 지우지 않고 「산출 안 함」으로 그린다.** 안 그리면
 * 「못 구했다」와 「안 쟀다」가 한 사건이 되는데, 이 제품이 없애려는 실패가 정확히 그것이다.
 */
export default function MarketFigures({ market }) {
  return (
    <Card title="시장 규모 — 위에서 아래로">
      <ol className="market-funnel">
        {STEPS.map(([label, key, gloss], depth) => (
          <li key={label} style={{ marginInlineStart: `${depth * 1.5}rem` }}>
            <FunnelStep label={label} gloss={gloss} figure={market[key]} />
          </li>
        ))}
      </ol>

      {market.growth ? (
        <div className="market-funnel__growth">
          <FunnelStep label="성장률" gloss="과거 관측" figure={market.growth} />
        </div>
      ) : null}
    </Card>
  );
}

function FunnelStep({ label, gloss, figure }) {
  if (!figure) {
    return (
      <div className="market-funnel__step market-funnel__step--absent">
        <div className="market-funnel__head">
          <strong>{label}</strong>
          <span className="market-funnel__gloss">{gloss}</span>
          <Badge tone="neutral">산출 안 함</Badge>
        </div>
        <p className="market-funnel__note">
          이 파이프라인은 {label} 을 내지 않는다. 초기 점유를 세우려면 관측이 아니라
          <strong> 판단</strong>이 필요하고, 여기서는 판단을 하지 않는다.
          <br />빈칸은 <strong>0 이 아니라 「안 쟀다」</strong>다.
        </p>
      </div>
    );
  }

  const grade = gradeView(figure.grade);
  const short = figure.unit === 'KRW' ? abbreviateKrw(figure.value) : null;

  return (
    <div className="market-funnel__step">
      <div className="market-funnel__head">
        <strong>{label}</strong>
        <span className="market-funnel__gloss">{gloss}</span>
        <b className="market-funnel__value">{formatValue(figure.value, figure.unit)}</b>
        {short ? <span className="market-funnel__short">≈ {short}</span> : null}
        <Badge tone={grade.tone}>{grade.label}</Badge>
      </div>
      {figure.formula ? <code>{figure.formula}</code> : null}
      {/* 경계는 값과 같은 블록에 둔다. 떼어내기 어렵게. */}
      {figure.assumptions.map((line) => (
        <em key={line} className="market-caveat">{line}</em>
      ))}
      {figure.caveats.map((line) => (
        <em key={line} className="market-caveat">{line}</em>
      ))}
    </div>
  );
}
