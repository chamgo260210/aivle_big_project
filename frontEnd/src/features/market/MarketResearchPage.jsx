import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createJourneyApi } from '../journey/journeyApi.js';
import { Alert, Badge, Button, Card, LoadingState } from '../../shared/ui';
import EvidenceCard from './EvidenceCard.jsx';
import useMarketPolling from './useMarketPolling.js';
import { SCORE_STATE_VIEW, formatValue, gradeView } from './marketResult.js';
import './market.css';

/**
 * 견본 컨셉 — <b>임시 다리다</b>.
 *
 * <p>제품에서 컨셉은 DB 에 있고 콘셉트 생성 단계가 만든다. 그때 이 버튼은 없어진다
 * (`시장조사/문서/작업/현재작업.md` §③-2). 지금은 AI 쪽 `pipeline.CONCEPTS` 의
 * 이름표를 그대로 보내고, 그 표가 (컨셉 파일, 원장) 을 정한다.
 */
const SAMPLE_CONCEPTS = [
  ['beauty-noshow', '미용실 노쇼 관리'],
  ['household-ledger', '가계부 앱'],
  ['pet-treat', '반려동물 수제 간식'],
];

/**
 * 1단계 — 시장조사.
 *
 * <p>「실행」 → 폴링(90~266초) → <b>7과목 성적표 + 근거 카드</b> → 「다음」으로 BM 캔버스.
 */
export default function MarketResearchPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const client = useApiClient();
  const api = useMemo(() => createJourneyApi(client, projectId), [client, projectId]);
  const [conceptKey, setConceptKey] = useState(SAMPLE_CONCEPTS[0][0]);

  const load = useCallback(() => api.currentMarketResearch(), [api]);
  const start = useCallback(() => api.startMarketResearch(conceptKey, today(), null),
    [api, conceptKey]);
  const { run, result, error, busy, loading, active, elapsed, trigger } = useMarketPolling(load, start);

  if (loading) return <LoadingState label="시장조사 결과를 불러오는 중" />;

  return (
    <section className="market-page">
      <header className="market-page__head">
        <div>
          <h2>시장조사</h2>
          <p>공개 통계·공시·언론에서 <strong>관측된 것만</strong> 모은다. 없는 것은 「못 찾은 것」으로 남는다.</p>
        </div>
        <Button onClick={trigger} disabled={busy || active}>
          {active ? '조사 중…' : result ? '다시 조사' : '시장조사 실행'}
        </Button>
      </header>

      {/* 임시 다리 — 컨셉이 DB 에서 오게 되면 이 블록은 통째로 사라진다. */}
      <Card title="견본 컨셉">
        <div className="market-concept-picker">
          {SAMPLE_CONCEPTS.map(([key, label]) => (
            <Button
              key={key}
              variant={key === conceptKey ? 'primary' : 'outline'}
              aria-pressed={key === conceptKey}
              disabled={busy || active}
              onClick={() => setConceptKey(key)}
            >
              {label}
            </Button>
          ))}
        </div>
      </Card>

      {error ? <Alert tone="danger">{error}</Alert> : null}

      {active ? (
        <Alert tone="info">
          조사 중이다 — <strong>{elapsed}초</strong> 경과. 보통 2~5분 걸린다(공개 통계를 실제로 조회한다).
        </Alert>
      ) : null}

      {run?.state === 'FAILED' ? (
        <Alert tone="danger">
          실행이 실패했다{run.errorCode ? ` (${run.errorCode})` : ''}.
          {run.retryable ? ' 다시 시도할 수 있다.' : ' 입력을 확인해야 한다.'}
        </Alert>
      ) : null}

      {!result ? (
        !active ? <Card><p>아직 조사한 적이 없다. 「시장조사 실행」을 눌러라.</p></Card> : null
      ) : (
        <>
          <Card title="산출물 성적표">
            {/* 「사유가 잘 붙었는가」가 아니라 「쓸 값이 있는가」를 보여 준다. */}
            <table className="market-scorecard">
              <thead>
                <tr><th>과목</th><th>상태</th><th>내용</th></tr>
              </thead>
              <tbody>
                {result.scorecard?.map((row) => {
                  const view = SCORE_STATE_VIEW[row.state] ?? { label: row.state, tone: 'neutral' };
                  return (
                    <tr key={row.subject}>
                      <th scope="row">{row.label}</th>
                      <td><Badge tone={view.tone}>{view.label}</Badge></td>
                      <td>{row.detail || '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Card>

          {result.market ? <MarketFigures market={result.market} /> : null}

          <Card title={`근거 ${result.evidence.length}건`}>
            <p className="market-note">
              값 옆의 <em>기울임</em>은 <strong>경계</strong>다 — 값과 함께 옮겨야 하는 문장이다.
            </p>
            <div className="market-evidence-list">
              {result.evidence.map((item) => <EvidenceCard key={item.id} item={item} />)}
            </div>
          </Card>

          <Card title="못 찾은 것">
            {/* 접지 않는다. 공백을 0으로 읽으면 안 된다. */}
            {result.market?.notFound?.length ? (
              <ul>
                {result.market.notFound.map((item) => (
                  <li key={item.item}><strong>{item.item}</strong> — {item.detail}</li>
                ))}
              </ul>
            ) : <p>보고된 공백이 없다.</p>}
            {result.market?.coverageCaveat ? (
              <Alert tone="warning">{result.market.coverageCaveat}</Alert>
            ) : null}
          </Card>

          {result.degradations.length > 0 ? (
            <Alert tone="warning">
              일부 단계를 건너뛰었다: {result.degradations.map((d) => `${d.stage}(${d.code})`).join(' · ')}
            </Alert>
          ) : null}

          <footer className="market-page__foot">
            <Button onClick={() => navigate(`/app/projects/${projectId}/journey/business-model`)}>
              다음 — BM 캔버스 만들기
            </Button>
          </footer>
        </>
      )}
    </section>
  );
}

function MarketFigures({ market }) {
  const rows = [
    ['TAM', market.tam], ['SAM', market.sam], ['SOM', market.som], ['성장률', market.growth],
  ].filter(([, figure]) => figure);

  return (
    <Card title="시장 규모">
      <dl className="market-figures">
        {rows.map(([label, figure]) => {
          const grade = gradeView(figure.grade);
          return (
            <div key={label}>
              <dt>{label}</dt>
              <dd>
                <b>{formatValue(figure.value, figure.unit)}</b>
                <Badge tone={grade.tone}>{grade.label}</Badge>
                {figure.formula ? <code>{figure.formula}</code> : null}
                {figure.assumptions.map((a) => <em key={a} className="market-caveat">{a}</em>)}
                {figure.caveats.map((c) => <em key={c} className="market-caveat">{c}</em>)}
              </dd>
            </div>
          );
        })}
        {market.price ? (
          <div>
            <dt>가격 밴드</dt>
            <dd>
              <b>
                {formatValue(market.price.min)} ~ {formatValue(market.price.max)} {market.price.currency}
              </b>
              <Badge tone={gradeView(market.price.grade).tone}>
                {gradeView(market.price.grade).label}
              </Badge>
              {/* ⚠ 이 문장을 빼면 잠정 대표값이 확정 단가로 읽힌다. */}
              {market.price.baseNote ? (
                <em className="market-caveat">
                  대표값 {formatValue(market.price.base)} — {market.price.baseNote}
                </em>
              ) : null}
            </dd>
          </div>
        ) : null}
      </dl>
    </Card>
  );
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
