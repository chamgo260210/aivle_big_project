import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { createMarketApi } from './marketApi.js';
import { Alert, Badge, Button, Card, LoadingState } from '../../shared/ui';
import { createConceptPortfolioApi } from '../concept-portfolio/api/conceptPortfolioApi.js';
import CompetitorSeedForm from './CompetitorSeedForm.jsx';
import { MarketResultBody } from './MarketResultBody.jsx';
import { BmResultBody } from './BmResultBody.jsx';
import RefinementSummary from './RefinementSummary.jsx';
import { DECISION_VIEW, evidenceSubjectIndex } from './marketResult.js';
import useCellFocus from './useCellFocus.js';
import useMarketPolling from './useMarketPolling.js';
import './market.css';

/**
 * 여정 3번 — <b>사업 검증</b>. 시장조사와 BM 캔버스가 한 화면, 한 실행으로 접혔다.
 *
 * <p>왜 하나인가. 둘은 같은 질문의 앞뒤다 — 「시장이 있나」와 「그 시장에서 이 사업이
 * 서나」. 화면이 갈려 있으면 캔버스의 문장이 어느 관측에 기대는지 사용자가 두 화면을
 * 오가며 맞춰야 했고, 실행이 갈려 있으면 「조사는 끝났는데 캔버스가 왜 비었나」를 묻는다.
 *
 * <p><b>한 화면이 세 상태를 갈아 낀다</b>(와이어프레임 `public/wireframe.html` 이 정본):
 * 조사 전 · 조사 중 · 결과. 판정은 `useMarketPolling` 이 이미 주는 `result`·`active` 로만
 * 한다 — 새 상태를 만들면 화면과 서버가 갈린다.
 *
 * <p>⚠ <b>옛 실행 결과를 버리지 않는다.</b> 2-4 이전 프로젝트에는 FULL·BM 이 따로 남아
 * 있다. 검증 실행이 아직 없으면 그 둘을 읽어 그린다 — 안 그리면 다 끝낸 프로젝트가
 * 「아무것도 안 했다」로 보인다.
 *
 * <p><b>이 한 칸 안에 화면이 둘이다</b> — 「사업 검증」과 「다듬어진 컨셉」. 라우트를 나누지
 * 않는다(여정 칸은 하나다). 대신 본문 맨 위의 2단계 표시가 그 사실을 말한다 — 그게 없으면
 * 「다듬어진 컨셉 보기」를 누른 사람이 다음 <b>단계</b>로 넘어간 줄 안다.
 */
export default function BusinessValidationPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);

  const load = useCallback(() => api.currentBusinessValidation(), [api]);
  // 컨셉은 **서버가 정한다** — 확정된 사업안(Market Seed)이 유일한 입력이다.
  // 예전에는 화면이 견본 이름표를 보냈고, 사업안을 확정하기 전에 누르면 서버가 그 견본으로
  // 조용히 떨어져 **남의 컨셉 원장으로 6/6 SUCCEEDED** 를 냈다(2026-08-12 실측: 미용실
  // 노쇼 견본이 냉동 간편식 사업안의 결과로 나왔다). 지금은 아무 이름표도 안 보낸다.
  const start = useCallback(() => api.startBusinessValidation(today()), [api]);
  const validation = useMarketPolling(load, start);

  // 옛 실행 — 표시 전용이다. 여기서 새로 시작하는 길은 만들지 않는다.
  const loadLegacyFull = useCallback(() => api.currentMarketResearch(), [api]);
  const loadLegacyBm = useCallback(() => api.currentBusinessModel(), [api]);
  const legacyFull = useMarketPolling(loadLegacyFull, null);
  const legacyBm = useMarketPolling(loadLegacyBm, null);

  const seedNames = useCompetitorSeedNames(api);

  // KPI → 과목 줄 착지. 캔버스 칸은 착지할 자리가 없다(「칸별 세부」를 안 그린다).
  const sectionFocus = useCellFocus('sec-');

  /**
   * 「다시 조사」를 누르면 <b>조사 전 화면으로 되돌아간다</b> — 목업 그대로.
   *
   * <p>왜 곧장 안 돌리나. 경쟁 씨앗은 <b>실행 전에</b> 받아야 한다 — 하네스가 슬롯을
   * 설계할 때 읽으므로 돌린 뒤에 적으면 그 판에는 안 들어간다. 결과 화면에는 씨앗 폼이
   * 없으니(목업), 여기서 되돌아가지 않으면 경쟁사를 고칠 길이 아예 사라진다.
   *
   * <p>서버 상태가 아니라 <b>이 화면만의 값</b>이다 — 결과는 그대로 두고 보여줄 것만 바꾼다.
   */
  const [reseeding, setReseeding] = useState(false);
  const startRun = useCallback(() => {
    setReseeding(false);
    validation.trigger();
  }, [validation]);

  // 화면 2 는 «들어갈 때» 읽는다. 검증 화면에 세 번의 GET 을 미리 얹을 이유가 없다.
  const [screen, setScreen] = useState('validation');
  const revision = useRevision(client, api, projectId, screen === 'revision');

  /**
   * 화면 2 「근거 보기」 → 화면 1 의 그 과목으로 착지.
   *
   * ⚠ <b>같은 틱에 부르면 그 노드가 아직 없다</b>(화면 1 이 아직 안 그려졌다).
   * 화면을 바꾼 «다음 프레임»에 점프한다.
   */
  const jumpToSubject = useCallback((subject) => {
    setScreen('validation');
    requestAnimationFrame(() => sectionFocus.jump(subject));
  }, [sectionFocus]);

  // 근거 id → 과목. 화면 2 의 「근거 보기」가 어느 줄로 갈지 정하는 표라 결과가 바뀔 때만 다시 짠다.
  const evidenceSubjects = useMemo(
    () => evidenceSubjectIndex(validation.result ?? legacyFull.result),
    [validation.result, legacyFull.result],
  );

  if (validation.loading || legacyFull.loading || legacyBm.loading) {
    return <LoadingState label="사업 검증 결과를 불러오는 중" />;
  }

  // 검증 실행이 있으면 그것 하나가 두 몫을 다 채운다(봉투가 같다).
  const marketResult = validation.result ?? legacyFull.result;
  const bmResult = validation.result ?? legacyBm.result;
  const running = validation.active;
  const decision = bmResult?.bm ? DECISION_VIEW[bmResult.bm.decision] : null;

  if (screen === 'revision') {
    return (
      <section className="market-page">
        <Steps screen={screen} onGo={setScreen} />
        {revision.loading ? <LoadingState label="다듬어진 컨셉을 불러오는 중" /> : (
          <RefinementSummary
            result={revision.refinement}
            concept={revision.concept}
            evidenceSubjects={evidenceSubjects}
            onJumpSubject={jumpToSubject}
            onBack={() => setScreen('validation')}
            onFinalize={revision.selectionId ? revision.finalize : null}
            finalizing={revision.finalizing}
            error={revision.error}
          />
        )}
      </section>
    );
  }

  return (
    <section className="market-page">
      <Steps screen={screen} onGo={setScreen} />
      <div className="pipeline-page-heading">
        <p>3. 사업 검증</p>
        <h2>
          사업 검증
          {/* 판정은 결과가 있을 때만 붙는다 — 조사 전에 배지가 서면 안 돈 검사가 통과로 읽힌다. */}
          {!running && decision ? <> <Badge tone={decision.tone}>{decision.label}</Badge></> : null}
        </h2>
        <span>시장 조사와 비즈니스 모델 분석으로 사업안을 검증해요.</span>
      </div>

      {validation.error ? <Alert tone="danger">{validation.error}</Alert> : null}
      {validation.run?.state === 'FAILED' ? (
        <Alert tone="danger">
          검증이 실패했어요{validation.run.errorCode ? ` (${validation.run.errorCode})` : ''}
          {validation.run.errorReason ? `: ${validation.run.errorReason}` : ''}.
          {validation.run.retryable ? ' 다시 시도할 수 있어요.' : ' 입력을 확인해야 해요.'}
          {' '}조사 원장은 남아 있어서 다시 눌러도 수집을 다시 사지 않아요.
        </Alert>
      ) : null}

      {/* ⚠ **도는 동안에는 옛 결과를 감춘다.** 예전에는 새 조사가 도는 중에도 직전 판의
          표가 그대로 떠 있었다 — 사용자가 「지금 이 사업안의 결과」로 읽는다. 실제로
          2026-08-12 에 미용실 견본 결과가 냉동 간편식 사업안의 화면에 그대로 떠 있었다.
          아직 안 나온 값을 화면에 두는 것은 조용한 오답이다(견본 폴백과 같은 계보). */}
      {running ? (
        <RunningCard elapsed={validation.elapsed} />
      ) : (!marketResult || reseeding) ? (
        <IdleCard api={api} busy={validation.busy} onStart={startRun} />
      ) : (
        <>
          <div className="bv-sec">
            <h3>시장 분석</h3>
            <span>
              {marketResult.asOf ? `${marketResult.asOf} 에 조사를 마쳤어요` : '조사를 마쳤어요'}
              {seedNames.length > 0 ? ` · 반영된 경쟁사: ${seedNames.join(' · ')}` : ''}
            </span>
            <Button variant="ghost" onClick={() => setReseeding(true)} disabled={validation.busy}>
              다시 조사
            </Button>
          </div>

          {/* ⚠ **조사 뒤에는 경쟁사 입력을 두지 않는다.** 목업이 그렇다 — 결과 화면에
              씨앗 폼이 없고, 무엇이 반영됐는지는 바로 위 «반영된 경쟁사: …» 한 줄이 말한다.
              예전에는 여기에 아코디언으로 접어 뒀는데, 조사가 끝난 판에는 고쳐도 반영될
              자리가 없어 「고칠 수 있다」는 잘못된 신호만 준다. 고치려면 「다시 조사」로
              돌아가 조사 전 화면에서 적는다. */}

          <MarketResultBody result={marketResult} activeId={sectionFocus.active} onJump={sectionFocus.jump} />

          <div className="bv-sec">
            <h3>비즈니스 모델</h3>
            <span>시장 분석 결과를 바탕으로 분석했어요</span>
          </div>

          {bmResult ? <BmResultBody result={bmResult} /> : (
            <Alert tone="warning">
              조사 결과는 있는데 캔버스가 없어요 — 「다시 조사」를 누르면 조사 원장을 그대로 쓰고
              캔버스만 다시 만들어요.
            </Alert>
          )}

          <div className="bv-foot">
            <Button onClick={() => setScreen('revision')}>다듬어진 컨셉 보기 →</Button>
          </div>
        </>
      )}
    </section>
  );
}

const STEPS = [['validation', '사업 검증'], ['revision', '다듬어진 컨셉']];

/**
 * 이 단계가 <b>두 장</b>이라는 것을 먼저 알린다.
 *
 * <p>왼쪽 셸의 여정은 「3. 사업 검증」한 칸이라, 그 안에서 화면이 두 번 넘어간다는 사실이
 * 어디에도 없다. 그러면 「다듬어진 컨셉 보기」를 누른 사람이 다음 <b>단계</b>로 넘어간 줄 안다.
 */
function Steps({ screen, onGo }) {
  return (
    <ol className="bv-steps">
      {STEPS.map(([key, label], index) => (
        <li key={key}>
          <button type="button" onClick={() => onGo(key)}
            className={screen === key ? 'is-on' : ''}
            aria-current={screen === key ? 'step' : undefined}>
            <span className="bv-steps__n num">{index + 1}</span>{label}
          </button>
        </li>
      ))}
    </ol>
  );
}

/** 404·409·422 는 「아직 없다」다 — 오류로 세우면 화면이 통째로 빨개진다. */
const optional = (promise) => promise
  .then((payload) => payload ?? null)
  .catch((error) => ([404, 409, 422].includes(error?.status) ? null : Promise.reject(error)));

/**
 * 화면 2 가 읽는 세 가지 — <b>다듬기 결과 · 컨셉 원문 · 법률 보고서</b>.
 *
 * <p>새 계약을 만들지 않는다. 셋 다 이미 있는 조회다. 다만 셋이 모두
 * <b>`selectionId` 를 먼저 알아야</b> 해서 한 번의 대기는 피할 수 없다 — 그다음 셋은
 * 한꺼번에 간다(직렬로 늘어놓으면 폭포가 된다).
 */
function useRevision(client, marketApi, projectId, enabled) {
  const portfolio = useMemo(() => createConceptPortfolioApi(client), [client]);
  const [state, setState] = useState({ loading: true, selectionId: null, refinement: null, concept: null, error: null });
  const [finalizing, setFinalizing] = useState(false);

  // ⚠ **전체 법률보고서(`portfolio.report`)를 더는 읽지 않는다.** 이 화면의 법률 칸은
  //    다듬기가 «바꾼 것»에 걸리는 법만 보인다 — 그 델타는 `/concept-refinement` 응답의
  //    `deltaLegal` 이 준다. 전체 보고서를 읽으면 안 바뀐 조항까지 8건이 늘어서 부분 검사가
  //    전체 검사로 보인다(2026-08-13 반려).
  const load = useCallback(async () => {
    const selection = (await optional(portfolio.currentSelection(projectId)))?.data ?? null;
    const selectionId = selection?.selectionId ?? null;
    if (!selectionId) return { loading: false, selectionId: null, refinement: null, concept: null, error: null };
    const [refinement, seed] = await Promise.all([
      optional(marketApi.currentRefinement(selectionId)),
      optional(portfolio.marketSeed(projectId, selectionId)),
    ]);
    return {
      loading: false,
      selectionId,
      refinement,
      concept: seed?.data?.snapshot ?? null,
      error: null,
    };
  }, [portfolio, marketApi, projectId]);

  useEffect(() => {
    if (!enabled) return undefined;
    let alive = true;
    load()
      .then((next) => { if (alive) setState(next); })
      .catch((failure) => {
        if (alive) setState((value) => ({ ...value, loading: false, error: getUserErrorMessage(failure) }));
      });
    return () => { alive = false; };
  }, [enabled, load]);

  /**
   * <b>시장 검증 후 최종 확정.</b> 확정하고 나면 다시 읽는다 — 서버가 법률보고서 재확정과
   * 시드 재발급을 순서대로 태우므로 화면의 값이 바뀐다.
   */
  const finalize = useCallback(async () => {
    if (!state.selectionId) return;
    setFinalizing(true);
    try {
      await marketApi.finalizeRefinedConcept(state.selectionId, `refine-finalize-${state.selectionId}`);
      setState(await load());
    } catch (failure) {
      setState((value) => ({ ...value, error: getUserErrorMessage(failure) }));
    } finally {
      setFinalizing(false);
    }
  }, [marketApi, state.selectionId, load]);

  return { ...state, finalizing, finalize };
}

/**
 * 조사 전 — <b>경쟁 씨앗을 실행 버튼 «바로 위»에서 받는다.</b>
 *
 * <p>슬롯 하네스가 조사를 설계할 때 이 칸을 읽는다. 결과가 나온 뒤에 적으면 그 판에는
 * 반영되지 않으므로, 받는 자리는 반드시 실행 버튼 앞이어야 한다.
 */
function IdleCard({ api, busy, onStart }) {
  return (
    <Card title="시장 조사">
      <p className="market-note">아직 조사하지 않았어요. 조사에는 보통 20분 넘게 걸려요.</p>
      <p className="bv-label">알고 있는 경쟁사가 있다면 적어 주세요 (선택)</p>
      <CompetitorSeedForm api={api} disabled={busy} />
      <div className="bv-foot">
        <Button onClick={onStart} disabled={busy}>시장조사 시작하기</Button>
      </div>
    </Card>
  );
}

/**
 * 조사 중 — 경과 시간과 <b>불확정 진행바.</b>
 *
 * <p>⚠ 퍼센트로 바꾸지 마라. <b>서버가 진행률을 주지 않는다</b> — 없는 수를 그리면 그것이
 * 곧 지어낸 값이다. 왕복하는 막대는 「돌고 있다」만 말하고 얼마나 남았는지는 말하지 않는다.
 */
function RunningCard({ elapsed }) {
  return (
    <Card title="조사하고 있어요">
      <p className="market-note">
        <b className="num">{formatElapsed(elapsed)}</b> 지났어요 · 보통 20분 넘게 걸려요.
        이 화면을 닫아도 조사는 계속돼요.
      </p>
      {/* 값 없는 progressbar = 불확정. aria-valuenow 를 넣으면 거짓 진행률이 읽힌다. */}
      <div className="bv-runbar" role="progressbar" aria-label="조사 진행 중"><i /></div>
    </Card>
  );
}

/**
 * 이 판에 반영된 경쟁 씨앗의 이름 — <b>되비추기 전용.</b>
 *
 * <p>「무엇을 넣고 돌렸는지」가 결과 옆에 없으면 사용자가 자기 입력이 먹혔는지 알 방법이
 * 없다. 실패는 조용히 넘긴다 — 적은 적이 없는 것도 정상이다.
 */
function useCompetitorSeedNames(api) {
  const [names, setNames] = useState([]);

  useEffect(() => {
    let alive = true;
    api.currentCompetitorSeeds()
      .then((view) => {
        if (!alive) return;
        setNames((view?.seeds ?? []).map((seed) => seed?.name).filter(Boolean));
      })
      .catch(() => { /* 적은 적이 없으면 되비출 것도 없다 */ });
    return () => { alive = false; };
  }, [api]);

  return names;
}

/** 초 → 「N분 N초」. 20분짜리 작업에 「1,247초」는 읽히지 않는다. */
function formatElapsed(seconds) {
  const safe = Number.isFinite(seconds) && seconds > 0 ? Math.floor(seconds) : 0;
  return `${Math.floor(safe / 60)}분 ${safe % 60}초`;
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
