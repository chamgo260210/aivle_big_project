import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createJourneyApi } from '../journey/journeyApi.js';
import { Accordion, Alert, Badge, Button, Card, LoadingState } from '../../shared/ui';
import EvidenceCard from './EvidenceCard.jsx';
import MarketFigures from './MarketFigures.jsx';
import CalculationBreakdown from './CalculationBreakdown.jsx';
import NotFoundPanel from './NotFoundPanel.jsx';
import useMarketPolling from './useMarketPolling.js';
import { SCORE_STATE_VIEW, abbreviateKrw, formatValue, gradeView } from './marketResult.js';
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
 * <p>블록 순서는 <b>「이 숫자를 믿어도 되는가」에 답이 쌓이는 순서</b>다:
 * 신원 → 숫자 3장 → 신뢰 스트립 → 요약 → 퍼널 → 해부 → 가격 → 못 찾은 것 → 근거 → 감사로그.
 * 성적표와 실행 경과는 자기진단이라 맨 아래 접어 둔다 — 사용자가 먼저 읽을 것이 아니다.
 */
export default function MarketResearchPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const client = useApiClient();
  const api = useMemo(() => createJourneyApi(client, projectId), [client, projectId]);
  const [conceptKey, setConceptKey] = useState(SAMPLE_CONCEPTS[0][0]);
  const [selected, setSelected] = useState(null);

  const load = useCallback(() => api.currentMarketResearch(), [api]);
  const start = useCallback(() => api.startMarketResearch(conceptKey, today(), null),
    [api, conceptKey]);
  const { run, result, error, busy, loading, active, elapsed, trigger } = useMarketPolling(load, start);

  // 칩 → 근거 카드로 착지. BmCanvasPage 와 같은 방식이다.
  const selectEvidence = useCallback((id) => {
    setSelected(id);
    document.getElementById(`evidence-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, []);

  if (loading) return <LoadingState label="시장조사 결과를 불러오는 중" />;

  const calcCards = result ? result.evidence.filter((item) => item.kind === '계산') : [];
  const observed = result ? result.evidence.filter((item) => item.kind === '관측') : [];

  return (
    <section className="market-page">
      <header className="market-page__head">
        <div>
          <h2>시장조사</h2>
          <p>공개 통계·공시·언론에서 <strong>관측된 것만</strong> 모은다. 없는 것은 「못 찾은 것」으로 남는다.</p>
          {result ? <RunIdentity result={result} /> : null}
        </div>
        <Button onClick={trigger} disabled={busy || active}>
          {active ? '조사 중…' : result ? '다시 조사' : '시장조사 실행'}
        </Button>
      </header>

      {/* 임시 다리 — 컨셉이 DB 에서 오게 되면 이 블록은 통째로 사라진다.
          결과가 있으면 접는다. 첫 화면을 임시 다리가 먹지 않게. */}
      {result ? (
        <Accordion title="견본 컨셉 다시 고르기">
          <ConceptPicker
            conceptKey={conceptKey} setConceptKey={setConceptKey} disabled={busy || active}
          />
        </Accordion>
      ) : (
        <Card title="견본 컨셉">
          <ConceptPicker
            conceptKey={conceptKey} setConceptKey={setConceptKey} disabled={busy || active}
          />
        </Card>
      )}

      {error ? <Alert tone="danger">{error}</Alert> : null}

      {active ? (
        <Alert tone="info">
          조사 중이다 — <strong>{elapsed}초</strong> 경과.
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
          {result.market ? <Headline market={result.market} /> : null}
          <TrustStrip result={result} observed={observed} calcCards={calcCards} />

          <SummaryBlock result={result} />

          {result.market ? <MarketFigures market={result.market} /> : null}

          <CalculationBreakdown
            cards={calcCards}
            evidenceById={result.evidenceById}
            onSelectEvidence={selectEvidence}
          />

          {result.market?.price ? (
            <PriceBlock price={result.market.price} evidenceById={result.evidenceById} />
          ) : null}

          {result.market ? (
            <NotFoundPanel
              notFound={result.market.notFound}
              coverageCaveat={result.market.coverageCaveat}
            />
          ) : null}

          <Card title={`근거 ${result.evidence.length}건`}>
            <p className="market-note">
              값 옆의 <em>기울임</em>은 <strong>경계</strong>다 — 값과 함께 옮겨야 하는 문장이다.
            </p>
            {/* 계산 카드에는 「쓰인 곳」을 붙이지 않는다 — 그건 재료가 아니라 결과라서
                「없음」이 버려진 값처럼 읽힌다. 무엇을 만들었는지는 위 해부 블록이 말한다. */}
            <div className="market-evidence-list">
              {result.evidence.map((item) => (
                <EvidenceCard
                  key={item.id}
                  id={`evidence-${item.id}`}
                  item={item}
                  highlighted={selected === item.id}
                  usedIn={item.kind === '계산' ? undefined : (result.usedIn.get(item.id) ?? [])}
                />
              ))}
            </div>
          </Card>

          {/* 자기진단 감사로그. 사용자가 먼저 읽을 것이 아니라서 접는다. */}
          <Scorecard rows={result.scorecard} />
          <RunLog stages={result.stages} degradations={result.degradations} />

          {result.notes.length > 0 ? (
            <ul className="market-notes">
              {result.notes.map((note) => <li key={note}>{note}</li>)}
            </ul>
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

function ConceptPicker({ conceptKey, setConceptKey, disabled }) {
  return (
    <div className="market-concept-picker">
      {SAMPLE_CONCEPTS.map(([key, label]) => (
        <Button
          key={key}
          variant={key === conceptKey ? 'primary' : 'outline'}
          aria-pressed={key === conceptKey}
          disabled={disabled}
          onClick={() => setConceptKey(key)}
        >
          {label}
        </Button>
      ))}
    </div>
  );
}

/** 이 결과가 «무엇의, 언제 것»인가. 기준일이 없으면 그렇게 적는다. */
function RunIdentity({ result }) {
  return (
    <p className="market-identity">
      {result.conceptId ?? '컨셉 미상'}
      {' · '}
      {result.asOf ? `기준일 ${result.asOf}` : <strong>기준일 미기재</strong>}
      {result.mode ? ` · ${result.mode}` : ''}
      {result.runId ? ` · run ${result.runId}` : ''}
    </p>
  );
}

/** 첫 화면 — 결론 숫자를 등급과 **동시에** 준다. 숫자만 먼저 보이면 확정으로 읽힌다. */
function Headline({ market }) {
  const cards = [['TAM', market.tam], ['SAM', market.sam], ['성장률', market.growth]]
    .filter(([, figure]) => figure);
  if (cards.length === 0) return null;

  return (
    <div className="market-headline">
      {cards.map(([label, figure]) => {
        const grade = gradeView(figure.grade);
        const short = figure.unit === 'KRW' ? abbreviateKrw(figure.value) : null;
        return (
          <Card key={label} className="market-headline__card">
            <span className="market-headline__label">{label}</span>
            <b>{short ?? formatValue(figure.value, figure.unit)}</b>
            {short ? <small>{formatValue(figure.value, figure.unit)}</small> : null}
            <Badge tone={grade.tone}>{grade.label}</Badge>
          </Card>
        );
      })}
    </div>
  );
}

/** 위 숫자를 얼마나 믿을지의 척도. 0 은 빈칸이 아니라 「0」으로 쓴다. */
function TrustStrip({ result, observed, calcCards }) {
  const unbacked = calcCards.reduce((sum, card) => {
    const inputs = Object.keys(card.inputs ?? {}).length;
    return sum + Math.max(0, inputs - card.materialIds.length);
  }, 0);
  const notFound = result.market?.notFound ?? [];
  const countOf = (group) => notFound
    .filter((block) => block.group === group)
    .reduce((sum, block) => sum + block.count, 0);

  return (
    <p className="market-trust">
      <span>근거 <b>{result.evidence.length}</b>건 (관측 {observed.length} · 계산 {calcCards.length})</span>
      <span>뒷받침 없는 입력 <b>{unbacked}</b></span>
      <span>아직 못 채운 것 <b>{countOf('NOT_YET')}</b></span>
      <span>값이 갈린 것 <b>{countOf('DIVERGED')}</b></span>
    </p>
  );
}

/**
 * 칸별 종합 요약. 없을 수 있다 — 예산이 모자라면 이 단계를 건너뛴다.
 * ⚠ **없다고 조용히 비워 두지 않는다.** 왜 없는지 적어야 「요약이 곧 결론」이라는 오해가 안 생긴다.
 */
function SummaryBlock({ result }) {
  if (result.summary?.length) {
    return (
      <Card title="핵심 요약">
        <ul className="market-summary">
          {result.summary.map((line) => (
            <li key={line.sentence}>
              {line.cell ? <span className="market-summary__cell">{line.cell}</span> : null}
              {line.sentence}
              {line.cardIds.map((id) => <code key={id}>{id}</code>)}
            </li>
          ))}
        </ul>
      </Card>
    );
  }
  const why = result.degradations.find((d) => d.stage === 'summary');
  if (!why) return null;
  return (
    <Alert tone="warning" title="요약 문장을 만들지 않았다">
      {why.detail ?? why.code} ({why.code}). 값·등급·경계는 아래 카드에 그대로 있다.
    </Alert>
  );
}

/** 가격은 밴드로 읽어야 한다. 대표값 하나만 남으면 확정 단가로 읽힌다. */
function PriceBlock({ price, evidenceById }) {
  const grade = gradeView(price.grade);
  const cited = price.evidenceIds.map((id) => evidenceById.get(id)).filter(Boolean);
  const hosts = new Set(cited.map((item) => hostOf(item.sourceUrl)).filter(Boolean));

  return (
    <Card title="가격">
      <p className="market-price__band">
        <b>{formatValue(price.min, price.currency)}</b>
        <span>~</span>
        <b>{formatValue(price.max, price.currency)}</b>
        <Badge tone={grade.tone}>{grade.label}</Badge>
      </p>
      {/* ⚠ 이 문장을 빼면 잠정 대표값이 확정 단가로 읽힌다. */}
      {price.baseNote ? (
        <em className="market-caveat">
          대표값 {formatValue(price.base, price.currency)} — {price.baseNote}
        </em>
      ) : null}
      {price.caveats.map((line) => <em key={line} className="market-caveat">{line}</em>)}

      {cited.length > 0 ? (
        <>
          <p className="market-note">
            관측된 표시가격 {cited.length}건 — 값이 갈렸다. <strong>실패가 아니라 조사 결과다.</strong>
          </p>
          <table className="market-scorecard">
            <thead><tr><th>값</th><th>근거</th><th>등급</th><th>출처</th></tr></thead>
            <tbody>
              {cited.map((item) => (
                <tr key={item.id}>
                  <th scope="row">{formatValue(item.value, item.unit)}</th>
                  <td><code>{item.id}</code></td>
                  <td>{item.grade ?? '등급 표기 없음'}</td>
                  <td>{hostOf(item.sourceUrl) ?? '출처 링크 없음'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {/* 건수와 독립성은 다르다. 한 도메인에서 3건은 3중 확인이 아니다. */}
          {hosts.size === 1 && cited.length > 1 ? (
            <Alert tone="warning">
              {cited.length}건이지만 출처 도메인은 <strong>{[...hosts][0]} 하나</strong>다
              {' — '}{cited.length}중 확인이 아니라 1중 확인이다.
            </Alert>
          ) : null}
        </>
      ) : null}
    </Card>
  );
}

function Scorecard({ rows }) {
  if (!rows?.length) return null;
  const weak = rows.filter((row) => row.state === 'MISSING' || row.state === 'PARTIAL').length;
  return (
    <details className="ui-accordion" open={weak > 0}>
      <summary>이 실행이 무엇을 쟀나 — {rows.length}과목 성적표{weak > 0 ? ` (보완 ${weak})` : ''}</summary>
      <div>
        <table className="market-scorecard">
          <thead><tr><th>과목</th><th>상태</th><th>내용</th></tr></thead>
          <tbody>
            {rows.map((row) => {
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
      </div>
    </details>
  );
}

/**
 * 실행 경과. **백분율로 바꾸지 않는다** — `stages` 는 단계 상태 목록이지 진행률이 아니다.
 * %로 만들면 없는 정밀도를 지어내게 된다.
 */
function RunLog({ stages, degradations }) {
  if (!stages.length && !degradations.length) return null;
  const seconds = stages.reduce((sum, s) => sum + (s.seconds ?? 0), 0);
  const llm = stages.reduce((sum, s) => sum + (s.llmCalls ?? 0), 0);
  return (
    <details className="ui-accordion" open={degradations.length > 0}>
      <summary>
        실행 경과 — {stages.length}단계 · {seconds}초 · LLM {llm}회
        {degradations.length > 0 ? ` · 건너뛴 단계 ${degradations.length}` : ''}
      </summary>
      <div>
        <table className="market-scorecard">
          <thead><tr><th>단계</th><th>상태</th><th>초</th><th>LLM</th></tr></thead>
          <tbody>
            {stages.map((stage) => (
              <tr key={stage.name}>
                <th scope="row">{stage.name}</th>
                <td>{stage.status}</td>
                <td>{stage.seconds ?? '—'}</td>
                <td>{stage.llmCalls ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {degradations.length > 0 ? (
          <ul className="market-degradations">
            {degradations.map((d) => (
              <li key={`${d.stage}-${d.code}`}>
                <strong>{d.stage}</strong> ({d.code}) — {d.detail}
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </details>
  );
}

function hostOf(url) {
  try {
    return new URL(url).host;
  } catch {
    return null;
  }
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
