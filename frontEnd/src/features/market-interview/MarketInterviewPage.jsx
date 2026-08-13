import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { Alert, Button, Card, LoadingState } from '../../shared/ui';
import ConceptBoardEditor from './ConceptBoardEditor.jsx';
import InterviewCard from './InterviewCard.jsx';
import SampleSizePicker from './SampleSizePicker.jsx';
import { createMarketInterviewApi } from './marketInterviewApi.js';
import {
  COMPREHENSION_VIEW,
  DIFFERENTIATION_VIEW,
  mentionText,
  renderBoard,
} from './marketInterviewResult.js';
import useMarketInterviewPolling from './useMarketInterviewPolling.js';
import './market-interview.css';

const EMPTY_BOARD = Object.freeze({
  conceptName: '', targetUsers: '', problemScenario: '',
  featureSet: [], differentiators: '', priceKrw: null,
});

/** 이해도 막대 순서 — 좋은 쪽부터. 「판정 못 함」은 0 이면 안 그린다. */
const COMPREHENSION_ORDER = Object.freeze(['accurate', 'partial', 'misunderstood', 'unclassified']);
const DIFFERENTIATION_ORDER = Object.freeze(['different', 'similar', 'unclear', 'unclassified']);

/** Fact 층에 나란히 놓는 축들. 아래 셋은 각자 자기 절을 갖는다. */
const FACT_AXES = Object.freeze(['LIKE', 'CONCERN']);

/**
 * 세 층 — 이 화면의 정직성 장치다.
 *
 * 어디까지가 집계 그대로이고, 어디부터가 계산이고, 어디까지가 응답자가 실제로 한 말인지를
 * 화면이 <b>스스로 밝힌다</b>. 밝히지 않으면 「AI 가 이렇게 판단했다」로 읽히는데,
 * 이 조사에는 그런 판단이 없다.
 */
const LAYERS = Object.freeze([
  { key: 'fact', title: 'Fact', detail: '집계 그대로' },
  { key: 'insight', title: 'Insight', detail: '결정론 교차만 · AI 호출 0회' },
  { key: 'sowhat', title: 'So-What', detail: '응답자 발언 범위만' },
]);

/**
 * 시장 인터뷰 화면 — 확정된 사업안을 던지고 사람들의 <b>말</b>을 듣는다.
 *
 * <p>이 화면이 파는 것은 수치가 아니라 언어다. 유일한 수치인 「언급 수」도
 * <b>이 표본에서 몇 명이 그 말을 했는지</b>일 뿐이다. 그래서 세 가지를 화면이 지킨다:
 *
 * <ol>
 *   <li><b>백분율을 쓰지 않는다.</b> 「20명 중 7명」으로만 쓴다 — 「35%」로 쓰는 순간
 *       「시장의 35%」로 읽힌다.</li>
 *   <li><b>이해도를 맨 위에 둔다.</b> 오해가 많으면 아래의 「끌리는 점」은 읽을 값이 아니다.
 *       컨셉이 나쁜 게 아니라 설명이 나쁜 것이고, 고칠 곳이 완전히 다르다.</li>
 *   <li><b>분모는 답한 사람 수다.</b> 뽑은 사람 수가 아니다.</li>
 * </ol>
 */
export default function MarketInterviewPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createMarketInterviewApi(client, projectId), [client, projectId]);

  const [board, setBoard] = useState(EMPTY_BOARD);
  const [boardError, setBoardError] = useState(null);
  const [boardLoading, setBoardLoading] = useState(true);
  const [sampleSize, setSampleSize] = useState(40);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const loaded = await api.board();
        if (alive) setBoard({ ...EMPTY_BOARD, ...loaded });
      } catch (failure) {
        // 확정된 사업안이 없으면 404 다. 견본으로 떨어지지 않는다 — 서버가 보낸 문구를
        // 그대로 보인다(문구의 정본은 서버다).
        if (alive) setBoardError(failure?.message ?? getUserErrorMessage(failure));
      } finally {
        if (alive) setBoardLoading(false);
      }
    })();
    return () => { alive = false; };
  }, [api]);

  const load = useCallback(() => api.currentInterview(), [api]);
  const start = useCallback(() => api.startInterview({
    ...board,
    featureSet: board.featureSet.map((f) => f.trim()).filter(Boolean),
  }, sampleSize), [api, board, sampleSize]);

  const { run, result, error, busy, loading, active, elapsed, trigger } =
    useMarketInterviewPolling(load, start);

  // 파생값은 effect 가 아니라 렌더에서 만든다.
  const preview = renderBoard(board);
  const ready = board.conceptName.trim().length > 0 && !boardError;
  const canRun = ready && !busy && !active;

  if (loading || boardLoading) return <LoadingState label="시장 인터뷰를 불러오는 중" />;

  return (
    <section className="mi-page">
      <InterviewSteps ready={ready} active={active} done={Boolean(result)} elapsed={elapsed} />

      <Card title="무엇을 보여줄까">
        {boardError ? (
          <Alert tone="danger">{boardError}</Alert>
        ) : (
          <>
            <p className="mi-page__lead">
              확정한 사업안에서 그대로 가져왔다. 응답자에게 <strong>이 설명 하나</strong>를 보이고
              정해진 9문항을 묻는다 — 첫인상, 본인 말로 설명, 끌리는 점, 걸리는 점,
              무엇이 다른가, 필요성, 언제 쓸 것 같은가, 안 사는 이유, 바꾸고 싶은 것.
              끌림·걸림·안 사는 이유는 <strong>왜 그런지까지</strong> 되묻는다.
            </p>
            <ConceptBoardEditor board={board} onChange={setBoard}
                                disabled={busy || active} preview={preview} />
          </>
        )}
      </Card>

      {ready ? (
        <>
          <Card title="표본">
            <SampleSizePicker value={sampleSize} onChange={setSampleSize}
                              disabled={busy || active} />
          </Card>

          <div className="mi-page__actions">
            {active ? <span className="mi-page__elapsed">{elapsed}초 경과</span> : null}
            <Button onClick={trigger} disabled={!canRun}>
              {active ? '인터뷰 중…' : result ? '다시 인터뷰' : '인터뷰 실행'}
            </Button>
          </div>
        </>
      ) : null}

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {run?.state === 'FAILED' && run?.errorCode ? (
        <Alert tone="danger">실행이 실패했다 — {failureText(run.errorCode)}</Alert>
      ) : null}

      {result ? <InterviewResult result={result} /> : null}

      <InterviewFootnote result={result} />
    </section>
  );
}

function failureText(code) {
  if (code === 'TWIN_BANK_UNAVAILABLE') return '카드 뱅크가 서버에 붙어 있지 않다(운영 설정 문제다).';
  if (code === 'MARKET_INTERVIEW_NO_USABLE_RESPONSE') {
    return '답이 표본의 절반도 걷히지 않았다. 줄여서 내보내지 않고 실패시킨 것이다 — 다시 실행해 보라.';
  }
  if (code === 'TASK_TIMEOUT') return '예산 안에 끝나지 않았다. 표본을 줄여 다시 해 보라.';
  return code;
}

/**
 * 이 모듈 안에서 사용자가 하는 일은 둘이다. 왼쪽 사이드바가 «어느 모듈인가»를 말하므로
 * 여기는 «그 모듈 안 어디인가»만 말한다.
 */
function InterviewSteps({ ready, active, done, elapsed }) {
  const steps = [
    { title: '보여줄 것 확인', state: ready ? 'done' : 'current', detail: null },
    {
      title: '인터뷰 실행',
      state: done ? 'done' : active ? 'current' : ready ? 'next' : 'waiting',
      detail: done ? '완료' : active ? `${elapsed}초` : null,
    },
  ];
  return (
    <ol className="mi-steps" aria-label="진행 단계">
      {steps.map((step, index) => (
        <li key={step.title} className="mi-steps__step" data-state={step.state}>
          <span className="mi-steps__dot" aria-hidden="true">
            {step.state === 'done' ? '✓' : index + 1}
          </span>
          <span className="mi-steps__title">{step.title}</span>
          {step.detail ? <span className="mi-steps__detail">{step.detail}</span> : null}
        </li>
      ))}
    </ol>
  );
}

function InterviewResult({ result }) {
  const { answered } = result;
  return (
    <div className="mi-result">
      {result.caveatsMissing ? (
        <Alert tone="danger">
          경계 문구가 결과에 실려오지 않았다 — 이 결과를 그대로 인용하지 마라.
        </Alert>
      ) : null}

      <SaturationAlert themes={result.saturatedThemes} />
      <SampleHeader targeting={result.targeting} sampling={result.sampling}
                    answered={answered} />
      <ComprehensionPanel comprehension={result.comprehension} answered={answered} />

      <LayerLegend />

      <Layer layer="fact">
        {result.sections
          .filter((section) => FACT_AXES.includes(section.axis))
          .map((section) => (
            <ThemeSection key={section.axis} section={section} answered={answered} />
          ))}

        <DifferentiationPanel counts={result.differentiation} answered={answered}
                              section={result.sections.find((s) => s.axis === 'DIFFERENTIATION')} />

        <UsageScenePanel section={result.sections.find((s) => s.axis === 'USAGE_SCENE')}
                         answered={answered} />

        <section className="mi-panel">
          <h3 className="mi-panel__title">지금은 이렇게 해결한다</h3>
          <p className="mi-panel__lead">
            「본인 상황에 필요한가」를 물으며 함께 나온 <strong>현재의 대안</strong>이다.
            이 조사가 이겨야 할 상대는 경쟁 제품이 아니라 대개 여기 적힌 것들이다.
            <strong>1인당 하나</strong>만 세므로 합계가 사람 수를 넘지 않는다.
          </p>
          {result.alternatives.length > 0 ? (
            <ul className="mi-alts">
              {result.alternatives.map((item) => (
                <li key={item.label}>
                  <span className="mi-alts__label">{item.label}</span>
                  <span className="mi-alts__count">{mentionText(item.mentionCount, answered)}</span>
                </li>
              ))}
            </ul>
          ) : <p className="mi-panel__empty">현재의 대안을 말한 사람이 없다.</p>}
        </section>

        <ThemeSection section={result.sections.find((s) => s.axis === 'BARRIER')}
                      answered={answered} showResolved />
      </Layer>

      <Layer layer="insight">
        <SegmentPanel segments={result.segments} />
        <ContrastPanel rows={result.contrast} targeting={result.targeting} />
      </Layer>

      <Layer layer="sowhat">
        <SuggestionLinkPanel rows={result.suggestionLinks} answered={answered} />
      </Layer>

      <section className="mi-panel">
        <h3 className="mi-panel__title">대표 응답자</h3>
        <p className="mi-panel__lead">
          이해도가 갈리는 사람들로 골랐다. 뽑는 규칙은 결정론이라 같은 표본이면 같은 사람이 나온다.
        </p>
        {result.interviews.length > 0 ? (
          result.interviews.map((card) => <InterviewCard key={card.key} card={card} />)
        ) : <p className="mi-panel__empty">보여 줄 응답을 고르지 못했다.</p>}
      </section>

      <details className="mi-figures">
        <summary>전원 응답 열람 ({result.transcripts.length}명)</summary>
        <p className="mi-panel__lead">
          위의 모든 수는 여기 있는 답에서만 나왔다. <strong>세어 본 것이 맞는지 여기서 되짚을 수 있다.</strong>
        </p>
        {result.transcripts.map((card) => (
          <InterviewCard key={card.id} card={card}
                         badge={card.target ? '타겟' : '비타겟'} />
        ))}
      </details>

      <details className="mi-figures">
        <summary>실행 기록 보기</summary>
        <dl>
          <div><dt>뽑은 사람</dt><dd>{result.sampling.drawn}명 / 요청 {result.sampling.requested}명</dd></div>
          <div><dt>답한 사람</dt><dd>{answered}명</dd></div>
          <div><dt>형식 위반</dt><dd>{result.telemetry.formatViolations ?? '—'}건</dd></div>
          <div><dt>실패</dt><dd>{result.telemetry.failures ?? '—'}건</dd></div>
          <div><dt>모델</dt><dd>{result.telemetry.model ?? '—'}</dd></div>
          <div><dt>걸린 시간</dt><dd>{result.telemetry.seconds ?? '—'}초</dd></div>
        </dl>
        {result.sampling.hasShortCells ? (
          <p className="mi-figures__short">
            층이 얕아 목표를 못 채운 칸이 있다 — 그 층의 목소리는 이 결과에 덜 실렸다:{' '}
            {result.sampling.shortCells.map((cell) => `${cell.cell} ${cell.available}/${cell.quota}`).join(', ')}
          </p>
        ) : null}
      </details>
    </div>
  );
}

/**
 * 이해도 — <b>맨 위에 두는 것이 설계다.</b>
 *
 * 오해한 사람이 많으면 아래의 「끌리는 점」·「걸리는 점」은 이 제품에 대한 반응이 아니라
 * 응답자가 상상한 다른 물건에 대한 반응이다. 그 사실을 먼저 보지 않으면 결과를 통째로
 * 잘못 읽는다.
 */
function ComprehensionPanel({ comprehension, answered }) {
  const rows = COMPREHENSION_ORDER
    .map((key) => ({ key, count: comprehension[key], ...COMPREHENSION_VIEW[key] }))
    .filter((row) => row.count > 0);
  const width = (count) => (answered > 0 ? `${(count / answered) * 100}%` : '0%');

  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">이 설명이 읽혔나</h3>
      <p className="mi-panel__lead">
        응답자에게 제품을 <strong>본인 말로 다시 설명하게</strong> 하고 자극과 대조한 것이다.
        오해가 많으면 컨셉이 나쁜 게 아니라 <strong>설명이 나쁜 것</strong>이고, 아래의
        반응은 이 제품이 아니라 응답자가 상상한 물건에 대한 반응이다.
      </p>

      <div className="mi-bar" role="img"
           aria-label={rows.map((row) => `${row.label} ${row.count}명`).join(', ')}>
        {rows.map((row) => (
          <span key={row.key} className={`mi-bar__part tone-${row.tone}`}
                style={{ width: width(row.count) }} />
        ))}
      </div>
      <ul className="mi-legend">
        {rows.map((row) => (
          <li key={row.key}>
            <span className={`mi-legend__dot tone-${row.tone}`} aria-hidden="true" />
            {row.label} <strong>{mentionText(row.count, answered)}</strong>
          </li>
        ))}
      </ul>

      {comprehension.misreadPoints.length > 0 ? (
        <div className="mi-misread">
          <p className="mi-misread__title">어디를 잘못 읽었나</p>
          <ul>{comprehension.misreadPoints.map((point) => <li key={point}>{point}</li>)}</ul>
        </div>
      ) : null}
    </section>
  );
}

/**
 * 한 축의 주제 목록. 막대 너비는 <b>답한 사람 수에 대한 비</b>이고 값이 아니다 —
 * 숫자는 언제나 「n명 중 x명」으로만 쓴다.
 *
 * <p>축이 6개로 늘어난 뒤로 <b>상위 몇 개만</b> 펼친다(정규화기의 `THEMES_VISIBLE`).
 * 나머지는 접되 개수를 밝힌다 — 접었다는 사실을 숨기면 「다 보여줬다」로 읽힌다.
 */
function ThemeSection({ section, answered, showResolved = false, title = null }) {
  if (!section) return null;
  const hidden = section.hiddenThemes ?? [];
  const row = (theme) => (
    <li key={`${theme.axis}-${theme.label}`} className="mi-theme">
      <div className="mi-theme__head">
        <span className="mi-theme__label">{theme.label}</span>
        <span className="mi-theme__count">{mentionText(theme.mentionCount, answered)}</span>
      </div>
      <div className="mi-theme__track" aria-hidden="true">
        <span className={`mi-theme__fill tone-${section.tone}`}
              style={{ width: answered > 0 ? `${(theme.mentionCount / answered) * 100}%` : '0%' }} />
      </div>
      {theme.quote ? <p className="mi-theme__quote">&ldquo;{theme.quote}&rdquo;</p> : null}
      {showResolved && theme.resolvedCount > 0 ? (
        <p className="mi-theme__resolved">
          이 중 <strong>{theme.resolvedCount}명</strong>이 「이게 해결되면 사겠다」고 말했다
          <span className="mi-theme__aside"> — 물어본 것이 아니라 스스로 말한 것만 셌다</span>
        </p>
      ) : null}
    </li>
  );

  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">{title ?? section.title}</h3>
      {section.themes.length > 0 ? (
        <>
          <ul className="mi-themes">{section.themes.map(row)}</ul>
          {hidden.length > 0 ? (
            <details className="mi-more">
              <summary>나머지 {hidden.length}개 보기</summary>
              <ul className="mi-themes">{hidden.map(row)}</ul>
            </details>
          ) : null}
        </>
      ) : <p className="mi-panel__empty">{section.empty}</p>}
    </section>
  );
}

/**
 * 포화 경고 — <b>전원이 같은 말을 한 축은 읽으면 안 된다.</b>
 *
 * 2026-08-12 에 n=40 실행의 모든 주제가 40/40 으로 나왔고 화면은 그것을 그대로 그렸다.
 * 코딩 구조를 뒤집어 그 고장은 막았지만, 진짜로 전원이 같은 말을 하는 경우는 남는다.
 * 그때 할 일은 감추는 것이 아니라 <b>읽지 말라고 말하는 것</b>이다.
 */
function SaturationAlert({ themes }) {
  if (!themes || themes.length === 0) return null;
  return (
    <Alert tone="warning">
      <strong>답이 갈리지 않은 축이 있다</strong> — {themes.join(' · ')}.
      자극이 한 속성(예: 가격)에 쏠렸거나, 합성 응답자의 분산이 소실됐거나, 표본이 좁은 것이다.
      셋 중 무엇인지는 아래 <strong>전원 응답</strong>을 눈으로 훑어야 알 수 있다.
      그 전까지 이 축은 결론으로 쓰지 마라.
    </Alert>
  );
}

/** 표본 머리 — <b>분모를 갈라 적는다.</b> 타겟과 비타겟을 한 수로 합치면 대비가 사라진다. */
function SampleHeader({ targeting, sampling, answered }) {
  return (
    <section className="mi-panel mi-panel--flat">
      <h3 className="mi-panel__title">누구에게 물었나</h3>
      <p className="mi-panel__lead">
        타겟 <strong>{targeting.targetDrawn}명</strong> + 참고용 비타겟{' '}
        <strong>{targeting.nonTargetDrawn}명</strong>을 뽑아 <strong>{answered}명</strong>이 답했다
        (요청 {sampling.requested}명).
      </p>
      <p className="mi-panel__aside">
        타겟 조건: <code>{targeting.criteriaText}</code>{' '}
        — 사업안의 「누구를 위한 것인가」를 기계가 옮긴 것이다. <strong>틀렸는지 아는 사람은
        당신뿐이니 여기부터 보라.</strong>
      </p>
      {targeting.shortfall > 0 ? (
        <p className="mi-panel__aside">
          조건에 맞는 사람이 모자라 {targeting.shortfall}명을 채우지 못했다 — 조건이 좁다는 뜻이다.
        </p>
      ) : null}
    </section>
  );
}

/** 세 층의 범례. 어디까지가 집계이고 어디부터가 계산인지 화면이 스스로 밝힌다. */
function LayerLegend() {
  return (
    <ul className="mi-layers" aria-label="결과를 읽는 세 층">
      {LAYERS.map((layer) => (
        <li key={layer.key} className="mi-layers__item" data-layer={layer.key}>
          <span className="mi-layers__dot" aria-hidden="true" />
          <strong>{layer.title}</strong> — {layer.detail}
        </li>
      ))}
    </ul>
  );
}

function Layer({ layer, children }) {
  const meta = LAYERS.find((item) => item.key === layer);
  return (
    <div className="mi-layer" data-layer={layer}>
      <p className="mi-layer__tag">{meta.title} <span>{meta.detail}</span></p>
      {children}
    </div>
  );
}

/** 차별성 — <b>「비슷하다」가 다수인 것 자체가 결과다.</b> 실패가 아니라 읽어야 할 신호다. */
function DifferentiationPanel({ counts, answered, section }) {
  const rows = DIFFERENTIATION_ORDER
    .map((key) => ({ key, count: counts[key], ...DIFFERENTIATION_VIEW[key] }))
    .filter((row) => row.count > 0);
  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">지금 있는 것들과 다른가</h3>
      <p className="mi-panel__lead">
        「다른 게 없으면 없다고 하셔도 된다」고 묻고 받은 답이다.
        <strong> 「비슷하다」가 많은 것은 조사의 실패가 아니라 결과다.</strong>
      </p>
      <ul className="mi-legend">
        {rows.map((row) => (
          <li key={row.key}>
            <span className={`mi-legend__dot tone-${row.tone}`} aria-hidden="true" />
            {row.label} <strong>{mentionText(row.count, answered)}</strong>
          </li>
        ))}
      </ul>
      {section && section.themes.length > 0 ? (
        <ul className="mi-themes">
          {section.themes.map((theme) => (
            <li key={theme.label} className="mi-theme">
              <div className="mi-theme__head">
                <span className="mi-theme__label">{theme.label}</span>
                <span className="mi-theme__count">
                  {mentionText(theme.mentionCount, answered)}
                </span>
              </div>
              {theme.quote ? <p className="mi-theme__quote">&ldquo;{theme.quote}&rdquo;</p> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

/** 사용 장면 — <b>상상 응답이라는 라벨을 뗄 수 없다.</b> 실제 행동이 아니다. */
function UsageScenePanel({ section, answered }) {
  return (
    <>
      <ThemeSection section={section} answered={answered} title="언제 쓸 것 같은가" />
      <p className="mi-panel__aside mi-panel__aside--tight">
        ⚠ <strong>상상해서 답한 것</strong>이다 — 실제로 그때 그렇게 쓴다는 뜻이 아니다.
        기획한 장면과 다른 것이 우세하면 포지셔닝을 다시 볼 신호이지, 그 자체가 수요는 아니다.
      </p>
    </>
  );
}

/** 세그먼트 교차 — <b>계산일 뿐이다.</b> 해석 문장을 붙이지 않는 것이 이 절의 규율이다. */
function SegmentPanel({ segments }) {
  if (!segments || segments.length === 0) {
    return (
      <section className="mi-panel">
        <h3 className="mi-panel__title">누가 그 말을 했나</h3>
        <p className="mi-panel__empty">교차할 주제가 없다.</p>
      </section>
    );
  }
  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">누가 그 말을 했나</h3>
      <p className="mi-panel__lead">
        주제를 말한 사람들을 프로필 네 축으로 갈라 센 것이다. <strong>세기만 했고 해석은
        붙이지 않았다</strong> — 각 줄의 합은 그 주제의 언급 수와 정확히 같다.
      </p>
      {segments.map((segment) => (
        <div key={`${segment.axis}-${segment.label}`} className="mi-segment">
          <p className="mi-segment__head">
            <span className="mi-segment__label">{segment.label}</span>
            <span className="mi-segment__count">{segment.mentionCount}명</span>
          </p>
          {segment.breakdown.map((dimension) => (
            <p key={dimension.dimension} className="mi-segment__row">
              <span className="mi-segment__dim">{dimension.dimension}</span>
              {dimension.buckets.map((bucket) => (
                <span key={bucket.label} className="mi-segment__bucket">
                  {bucket.label} <strong>{bucket.count}</strong>
                </span>
              ))}
            </p>
          ))}
        </div>
      ))}
    </section>
  );
}

/**
 * 타겟 vs 비타겟 — <b>두 수를 나누지 않는다.</b> 분모가 다르기 때문이다.
 * 분모를 제목에 적어 두 수를 나란히만 놓는다.
 */
function ContrastPanel({ rows, targeting }) {
  const meaningful = (rows ?? []).filter((row) => row.nonTargetCount > 0);
  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">타겟 밖에서도 같은 말이 나왔나</h3>
      <p className="mi-panel__lead">
        왼쪽은 타겟 {targeting.targetDrawn}명 중, 오른쪽은 비타겟 {targeting.nonTargetDrawn}명 중
        몇 명인지다. <strong>분모가 달라 두 수를 나누지 않는다.</strong>
        비타겟에서 뜻밖에 강한 반응이 보이면 타겟을 다시 그릴 근거가 된다.
      </p>
      {meaningful.length > 0 ? (
        <ul className="mi-contrast">
          {meaningful.map((row) => (
            <li key={`${row.axis}-${row.label}`}>
              <span className="mi-contrast__label">{row.label}</span>
              <span className="mi-contrast__pair">
                타겟 <strong>{row.targetCount}</strong> / 비타겟 <strong>{row.nonTargetCount}</strong>
              </span>
            </li>
          ))}
        </ul>
      ) : <p className="mi-panel__empty">비타겟에서 같은 말을 한 사람이 없다.</p>}
    </section>
  );
}

/**
 * 개선 제안 ↔ 우려·장벽 — 연결의 근거는 <b>같은 사람이 둘 다 말했다</b>는 것뿐이다.
 * 「그러니 값을 내려라」 같은 문장은 이 화면이 만들지 않는다.
 */
function SuggestionLinkPanel({ rows, answered }) {
  const withLinks = (rows ?? []).filter((row) => row.links.length > 0);
  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">바꿔 달라는 말은 무엇에 걸려 있나</h3>
      <p className="mi-panel__lead">
        제안을 한 사람이 <strong>같은 답지 안에서</strong> 어떤 우려·장벽을 말했는지 이어 본 것이다.
        연결의 근거는 그것뿐이고, <strong>무엇을 먼저 고치라는 판단은 여기 없다.</strong>
      </p>
      {withLinks.length > 0 ? (
        <ul className="mi-links">
          {withLinks.map((row) => (
            <li key={row.label}>
              <p className="mi-links__head">
                <span className="mi-links__label">{row.label}</span>
                <span className="mi-links__count">{mentionText(row.mentionCount, answered)}</span>
              </p>
              <ul className="mi-links__inner">
                {row.links.map((link) => (
                  <li key={`${link.axis}-${link.label}`}>
                    ↳ {link.label} <strong>{link.overlapCount}명</strong>이 함께 말함
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      ) : <p className="mi-panel__empty">제안과 우려를 함께 말한 사람이 없다.</p>}
    </section>
  );
}

/**
 * 맨 아래 각주 — 일반 면책 + **서버가 값과 함께 실어 보낸 경계 문구**.
 *
 * ⚠ 이 저장소는 경계 표시를 지우지 않는다(CLAUDE.md 규칙 7). 문장은 서버가 만든 그대로다
 * (`ai/app/interview/caveats.py`). 빠졌으면 결과 위에서 크게 운다.
 */
export function InterviewFootnote({ result }) {
  const notes = result?.caveats ?? [];
  return (
    <footer className="mi-footnote">
      <p>
        이 결과는 실존 인물의 응답이 아니라 한국미디어패널조사(KISDI) 실측 프로파일로 만든
        디지털 트윈의 시뮬레이션이다. 숫자는 <strong>이 표본에서 그 말을 한 사람 수</strong>일
        뿐이며 시장 규모도 구매율도 아니다 — 백분율로 환산하지 마라. 이 조사 형식은 외적
        타당성 시험을 거치지 않았고, 가격 수용도·지불의사는 답하지 않는다.
      </p>
      {notes.length > 0 ? (
        <details>
          <summary>이 결과를 읽는 법 {notes.length}가지</summary>
          <ul>{notes.map((note) => <li key={note}>{note}</li>)}</ul>
        </details>
      ) : null}
    </footer>
  );
}
