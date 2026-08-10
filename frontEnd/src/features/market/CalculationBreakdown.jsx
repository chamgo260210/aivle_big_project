import { Badge, Card } from '../../shared/ui';
import { formatValue, gradeView } from './marketResult.js';

/**
 * 「이 숫자는 무엇으로 만들어졌나」 — 계산 카드의 해부.
 *
 * ⚠ **변수 행마다 ●관측/○가정 을 찍지 않는다.** 엔진이 어느 변수가 어느 근거로
 * 뒷받침되는지 모르기 때문이다 — `materialIds` 는 근거 목록이지 변수별 짝이 아니다
 * (`research2/service/cards.py` 의 `mat_ids`). 값이 겹칠 때 추측으로 찍으면 화면이
 * **조용히 거짓말한다**. 이 제품이 가장 하면 안 되는 일이다.
 *
 * 대신 가정 문장이 이미 변수 이름을 부른다 — 「세그먼트비중 0.19 는…」 「침투율 0.1 은…」.
 * 산문이 표보다 정확한 자리라서, 표는 변수와 값만 세우고 판정은 문장에 맡긴다.
 */
export default function CalculationBreakdown({ cards, evidenceById, onSelectEvidence }) {
  if (cards.length === 0) return null;

  return (
    <Card title="이 숫자는 무엇으로 만들어졌나">
      <p className="market-note">
        계산된 값은 <strong>입력이 관측으로 뒷받침되는 만큼만</strong> 단단하다.
        뒷받침 없는 입력이 하나라도 있으면 등급은 「추정」으로 내려간다.
      </p>
      {cards.map((card) => (
        <Breakdown
          key={card.id}
          card={card}
          evidenceById={evidenceById}
          onSelectEvidence={onSelectEvidence}
        />
      ))}
    </Card>
  );
}

function Breakdown({ card, evidenceById, onSelectEvidence }) {
  const grade = gradeView(card.grade);
  const inputs = Object.entries(card.inputs ?? {});
  const backed = card.materialIds.length;

  return (
    <section className="market-breakdown">
      <header className="market-breakdown__head">
        <strong>{card.metric ?? card.id}</strong>
        <b>{formatValue(card.value, card.unit)}</b>
        <Badge tone={grade.tone}>{grade.label}</Badge>
      </header>

      {card.formula ? <code>{card.formula}</code> : null}

      {inputs.length > 0 ? (
        <table className="market-scorecard">
          <thead>
            <tr><th>변수</th><th>값</th></tr>
          </thead>
          <tbody>
            {inputs.map(([name, value]) => (
              <tr key={name}>
                <th scope="row">{name}</th>
                <td>{typeof value === 'number' ? value.toLocaleString('ko-KR') : String(value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : <p className="market-note">입력 변수가 기록되지 않았다.</p>}

      <p className="market-breakdown__count">
        입력 {inputs.length}개 · 관측이 뒷받침한 것 {backed}개
        {inputs.length > backed ? (
          <> · <strong>뒷받침 없는 것 {inputs.length - backed}개</strong></>
        ) : null}
      </p>

      {card.materialIds.length > 0 ? (
        <p className="market-breakdown__materials">
          뒷받침한 근거:{' '}
          {card.materialIds.map((id) => (
            <button
              key={id}
              type="button"
              className="bmc-chip"
              onClick={() => onSelectEvidence?.(id)}
            >
              {id}
              {evidenceById.get(id)?.grade ? ` · ${evidenceById.get(id).grade}` : ''}
            </button>
          ))}
        </p>
      ) : null}

      {/* 등급이 왜 그렇게 나왔는지. **파싱하지 않고 그대로 옮긴다** —
          문자열이 바뀌는 날 조용히 빈칸이 되는 쪽이 더 나쁘다. */}
      {card.gradeReason ? <p className="market-breakdown__link">{card.gradeReason}</p> : null}

      {card.assumptions.length > 0 ? (
        <div className="market-breakdown__assumptions">
          <h4>가정으로 남은 것 — 값과 함께 옮겨야 하는 문장이다</h4>
          <ul>
            {card.assumptions.map((line) => <li key={line}>{line}</li>)}
          </ul>
        </div>
      ) : null}

      {card.caveats.map((line) => <em key={line} className="market-caveat">{line}</em>)}
    </section>
  );
}
