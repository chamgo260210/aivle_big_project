import { StatusBadge } from '../../shared/ui';
import { CELL_STATUS_VIEW } from './marketResult.js';

/**
 * BM 캔버스 9칸.
 *
 * <p>표준 비대칭 배치를 `grid-template-areas` 로 그린다 — 저장소에 격자 CSS 가 없어서
 * 새로 만든다(`.concept-grid` 의 3열로는 이 모양이 안 나온다).
 *
 * ⚠ 칸에 `caveats` 가 있으면 **경계를 반드시 함께 그린다.** 그것 없이 상태 배지만 보이면
 * 「확인됨」이 무조건적 확인으로 읽힌다.
 */
export default function BmCanvas({ cells, onSelectEvidence, selectedEvidenceId }) {
  return (
    <div className="bmc-grid">
      {cells.map((cell) => {
        const view = CELL_STATUS_VIEW[cell.status] ?? { label: cell.status, tone: 'neutral' };
        return (
          <section key={cell.cell} className="bmc-cell" style={{ gridArea: cell.area }}>
            <header className="bmc-cell__head">
              <h3>{cell.label}</h3>
              <StatusBadge status={cell.status} />
            </header>

            {cell.absent ? (
              <p className="bmc-cell__absent">
                이 칸이 결과에 오지 않았다 — 「미확인」과 다른 사건이다.
              </p>
            ) : null}

            {cell.content.length > 0 ? (
              <ul className="bmc-cell__content">
                {cell.content.map((line) => <li key={line}>{line}</li>)}
              </ul>
            ) : (
              <p className="bmc-cell__empty">내용 없음 — {view.label}</p>
            )}

            {/* 경계는 접지 않는다. 값과 같은 화면에 있어야 도달한 것이다. */}
            {cell.caveats.map((caveat) => (
              <p key={caveat} className="bmc-cell__caveat">{caveat}</p>
            ))}

            <p className="bmc-cell__reason">{cell.reason}</p>

            {cell.evidenceIds.length > 0 ? (
              <div className="bmc-cell__evidence">
                <span>근거</span>
                {cell.evidenceIds.map((id) => (
                  <button
                    key={id}
                    type="button"
                    className={`bmc-chip${selectedEvidenceId === id ? ' is-active' : ''}`}
                    onClick={() => onSelectEvidence?.(id)}
                  >
                    {id}
                  </button>
                ))}
              </div>
            ) : null}

            {cell.missingEvidence.length > 0 ? (
              <p className="bmc-cell__missing">
                못 찾은 것: {cell.missingEvidence.join(' · ')}
              </p>
            ) : null}
          </section>
        );
      })}
    </div>
  );
}
