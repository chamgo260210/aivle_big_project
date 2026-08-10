import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from './marketApi.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { Alert, Badge, Button, Card, LoadingState } from '../../shared/ui';
import BmCanvas from './BmCanvas.jsx';
import EvidenceCard from './EvidenceCard.jsx';
import useMarketPolling from './useMarketPolling.js';
import { DECISION_VIEW } from './marketResult.js';
import './market.css';

/**
 * 2단계 — BM 캔버스. 1단계 결과를 근거로 채운다.
 *
 * <p>칸의 근거 칩을 누르면 아래 근거 카드가 강조된다 — 그 연결이 없으면
 * 칸의 문장이 <b>출처 없는 단정</b>으로 읽힌다.
 */
export default function BmCanvasPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const client = useApiClient();
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);
  const [selected, setSelected] = useState(null);

  const load = useCallback(() => api.currentBusinessModel(), [api]);
  // ⚠ 여기 실린 conceptId 는 **쓰이지 않는다.** 백엔드가 1단계 결과의 conceptId 를 그대로
  //    이어 쓴다 — 1단계와 다른 컨셉으로 판정하면 「관측은 A, 잣대는 B」가 되기 때문이다.
  const start = useCallback(() => api.startBusinessModel(String(projectId), today()),
    [api, projectId]);
  const { run, result, error, busy, loading, active, elapsed, trigger } = useMarketPolling(load, start);

  const selectEvidence = useCallback((id) => {
    setSelected(id);
    document.getElementById(`evidence-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, []);

  if (loading) return <LoadingState label="BM 캔버스를 불러오는 중" />;

  const decision = result?.bm ? DECISION_VIEW[result.bm.decision] : null;

  return (
    <section className="market-page">
      <header className="market-page__head">
        <div>
          <h2>비즈니스 모델 캔버스</h2>
          <p>시장조사에서 <strong>관측된 근거로만</strong> 채운다. 근거가 없는 칸은 비워 두고 사유를 적는다.</p>
        </div>
        <div className="market-page__actions">
          <Button variant="ghost" onClick={() => navigate(projectRoutes.market(projectId))}>
            시장조사로
          </Button>
          <Button onClick={trigger} disabled={busy || active}>
            {active ? '생성 중…' : result ? '다시 생성' : '캔버스 만들기'}
          </Button>
        </div>
      </header>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {active ? <Alert tone="info">캔버스를 만드는 중이다 — {elapsed}초 경과.</Alert> : null}
      {run?.state === 'FAILED' ? (
        <Alert tone="danger">생성이 실패했다{run.errorCode ? ` (${run.errorCode})` : ''}.</Alert>
      ) : null}

      {!result ? (
        !active ? (
          <Card>
            <p>아직 캔버스가 없다. 「캔버스 만들기」를 눌러라.</p>
            <p className="market-note">시장조사를 먼저 끝내야 한다 — 근거 없이는 만들지 않는다.</p>
          </Card>
        ) : null
      ) : (
        <>
          {result.bm ? (
            <Card title="판정">
              <p className="market-decision">
                {decision ? <Badge tone={decision.tone}>{decision.label}</Badge> : null}
                <span>신뢰도 {result.bm.confidence ?? '—'}</span>
              </p>
              <p>{result.bm.summary}</p>
              <dl className="market-fit">
                <div><dt>시장 적합성</dt><dd>{result.bm.marketFitSummary}</dd></div>
                <div><dt>내부 일관성</dt><dd>{result.bm.consistencySummary}</dd></div>
              </dl>
              {result.bm.legal && !result.bm.legal.used ? (
                <Alert tone="warning">
                  법률 검토 결과가 <strong>반영되지 않았다</strong>. 이 판정은 시장 근거만 본 것이다.
                </Alert>
              ) : null}
            </Card>
          ) : (
            <Alert tone="warning">
              BM 판정이 오지 않았다 — 시장조사 결과는 유효하다. 다시 생성해 볼 수 있다.
            </Alert>
          )}

          {result.canvas ? (
            <BmCanvas
              cells={result.canvas}
              onSelectEvidence={selectEvidence}
              selectedEvidenceId={selected}
            />
          ) : null}

          <Card title={`근거 ${result.evidence.length}건`}>
            <p className="market-note">칸의 근거 칩을 누르면 여기로 온다.</p>
            <div className="market-evidence-list">
              {result.evidence.map((item) => (
                <EvidenceCard
                  key={item.id}
                  id={`evidence-${item.id}`}
                  item={item}
                  highlighted={selected === item.id}
                />
              ))}
            </div>
          </Card>
        </>
      )}
    </section>
  );
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
