import { useCallback, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { Alert, Button, Card, LoadingState } from '../../shared/ui';
import SampleSizePicker from './SampleSizePicker.jsx';
import StimulusEditor from './StimulusEditor.jsx';
import { createTwinSurveyApi } from './twinSurveyApi.js';
import { interviewLines } from './twinSurveyResult.js';
import { gateSurvey } from './taskTypeGate.js';
import useTwinSurveyPolling from './useTwinSurveyPolling.js';
import './twin-survey.css';

/** 빈 쌍 하나로 시작한다. 속성 한 줄이 있어야 편집기가 표를 그린다. */
const INITIAL_PAIRS = [{
  pairId: 'P1',
  X: { label: 'A안', attrs: { 형태: '' }, priceKrw: null },
  Y: { label: 'B안', attrs: { 형태: '' }, priceKrw: null },
}];

/**
 * 패널 트윈 조사 화면.
 *
 * <p>이 화면이 파는 것은 <b>방향과 신뢰구간</b>이다. 크기·점유율·선택확률은 이 파이프라인이
 * 산출하지 않는다 — 없는 것이지 0 이 아니다. 그래서 두 가지를 화면이 지킨다:
 *
 * <ol>
 *   <li><b>경계 문구를 값과 같은 카드에 둔다.</b> 배너로 올리면 스크롤 밖으로 나가고,
 *       그러면 경계 없는 수치가 그대로 인용된다.</li>
 *   <li><b>「못 잼」과 「차이 없음」을 다르게 쓴다.</b> 흐리면 없는 결론이 생긴다.</li>
 * </ol>
 *
 * <p>실행 버튼은 <b>화면과 서버가 각자</b> 막는다. 화면 게이트({@code taskTypeGate.js})는
 * 서버({@code ai/app/twin/task_type.py})의 거울이고, 갈리면 서버가 이긴다.
 */
export default function TwinSurveyPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createTwinSurveyApi(client, projectId), [client, projectId]);

  const [situation, setSituation] = useState('가게에서 하나를 고릅니다. 아래 두 상품이 있습니다.');
  const [pairs, setPairs] = useState(INITIAL_PAIRS);
  const [sampleSize, setSampleSize] = useState(100);

  const load = useCallback(() => api.currentSurvey(), [api]);
  const start = useCallback(() => api.startSurvey(situation, pairs, sampleSize),
    [api, situation, pairs, sampleSize]);
  const { run, result, error, busy, loading, active, elapsed, trigger } =
    useTwinSurveyPolling(load, start);

  const gate = gateSurvey(pairs);
  const canRun = gate.canRun && situation.trim().length >= 5 && !busy && !active;

  if (loading) return <LoadingState label="트윈 조사 결과를 불러오는 중" />;

  return (
    <section className="twin-page">
      <div className="pipeline-page-heading">
        <p>8. 패널 트윈 조사</p>
        <h2>두 상품안 중 어느 쪽이 이기나</h2>
        <span>
          한국미디어패널조사 실측 프로파일로 만든 디지털 트윈에게 묻는다.
          답은 <strong>방향과 신뢰구간까지</strong>다 — 크기·점유율·선택확률은 내지 않는다.
        </span>
      </div>

      <Alert tone="warning">
        이 결과는 <strong>실존 인물의 응답이 아니다</strong>. 실측 프로파일 기반 시뮬레이션이며,
        검증 성적이 유지되는 <strong>명백한 우열형</strong>에서만 제공한다 —
        가격이 걸린 질문(지불의사)은 실행 모델에 따라 방향이 뒤집혀 제공하지 않는다.
      </Alert>

      <Card title="자극">
        <StimulusEditor
          situation={situation}
          pairs={pairs}
          onSituationChange={setSituation}
          onChange={setPairs}
          disabled={busy || active}
        />
      </Card>

      <Card title="표본">
        <SampleSizePicker
          pairs={pairs}
          value={sampleSize}
          onChange={setSampleSize}
          disabled={busy || active}
        />
      </Card>

      <div className="twin-page__actions">
        {active ? <span className="twin-page__elapsed">{elapsed}초 경과</span> : null}
        <Button onClick={trigger} disabled={!canRun}>
          {active ? '조사 중…' : result ? '다시 조사' : '조사 실행'}
        </Button>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {run?.state === 'FAILED' && run?.errorCode ? (
        <Alert tone="danger">실행이 실패했다 — {failureText(run.errorCode)}</Alert>
      ) : null}

      {result ? <TwinResult result={result} /> : null}
    </section>
  );
}

function failureText(code) {
  if (code === 'TWIN_BANK_UNAVAILABLE') return '카드 뱅크가 서버에 붙어 있지 않다(운영 설정 문제다).';
  if (code === 'TWIN_TASK_TYPE_NOT_SERVICEABLE') return '성적이 없는 유형이라 서버가 거절했다.';
  if (code === 'TASK_TIMEOUT') return '예산 안에 끝나지 않았다. 표본을 줄여 다시 해 보라.';
  return code;
}

function TwinResult({ result }) {
  return (
    <div className="twin-result">
      {result.warnings.length > 0 ? (
        <Alert tone="danger">
          경계 문구가 빠진 쌍이 있다 — 이 결과를 그대로 인용하지 마라: {result.warnings.join(', ')}
        </Alert>
      ) : null}

      {result.pairs.map((pair) => (
        <PairPanel key={pair.pairId} pair={pair} result={result} />
      ))}
    </div>
  );
}

/**
 * 한 쌍의 결과 한 판. 목업(`persona_interview_mockup.html`)의 구조를 따른다 —
 * 머리글 · 판정 · 구성 막대 · 대표 인터뷰 · 경계.
 *
 * <b>수치를 접어 두는 이유</b>는 감추려는 것이 아니라 순서 때문이다. 「어느 쪽이 이겼나」와
 * 「사람들이 왜 그렇게 말했나」가 먼저 오고, Δ·MDE 는 그 판단을 확인하려는 사람이 편다.
 */
function PairPanel({ pair, result }) {
  const c = pair.composition;
  return (
    <section className="twin-panel">
      <header className="twin-panel__head">
        <div>
          <p className="twin-panel__title">패널 트윈 조사</p>
          <p className="twin-panel__subtitle">{pair.labels.X} vs {pair.labels.Y}</p>
        </div>
        <span className="twin-panel__done">{result.sampling.drawn}명 완료</span>
      </header>

      <div className="twin-verdict">
        <div className="twin-verdict__line">
          <span className="twin-verdict__headline">
            {pair.measurable ? `${c.leadLabel} 우세` : '판정 불가 — 못 잼'}
          </span>
          <span className="twin-verdict__share">
            {c.leadPercent}% vs {c.trailPercent}% · 미결정 {c.undecidedPercent}%
          </span>
        </div>
        {/* 이긴 쪽 / 미결정·위치응답 / 진 쪽. 이 막대는 응답자 구성이지 점유율이 아니다. */}
        <div className="twin-bar" role="img"
             aria-label={`${c.leadLabel} ${c.lead}명, 미결정 ${c.undecided}명, ${c.trailLabel} ${c.trail}명`}>
          <span className="twin-bar__lead" style={{ width: `${c.leadPercent}%` }} />
          <span className="twin-bar__undecided" style={{ width: `${c.undecidedPercent}%` }} />
          <span className="twin-bar__trail" style={{ width: `${c.trailPercent}%` }} />
        </div>
        <p className="twin-verdict__reason">{pair.decisionReason}</p>
      </div>

      <p className="twin-panel__section">대표 응답자 인터뷰</p>
      {pair.interviews.length > 0 ? (
        pair.interviews.map((interview, index) => (
          <InterviewCard key={index} interview={interview} labels={pair.labels} />
        ))
      ) : (
        <p className="twin-panel__empty">인용할 응답을 고르지 못했다.</p>
      )}

      <details className="twin-panel__how">
        <summary>대표는 어떻게 골랐나</summary>
        <p>
          이긴 쪽 2명 · 진 쪽 2명 · 미결정 1명을 성×연령 층이 겹치지 않게 뽑는다.
          제시 순서를 보고 고른 응답자(위치응답)는 제외한다 — 그 말을 이유로 읽으면
          없는 근거가 생긴다. 난수를 쓰지 않아 같은 조사면 같은 5명이 나온다.
        </p>
      </details>

      {/* ⚠ 경계는 **이 판 안**에 둔다. 값과 떨어지면 값만 인용된다. */}
      <ul className={`twin-panel__caveats${pair.caveatsMissing ? ' is-missing' : ''}`}>
        {pair.caveats.map((note) => <li key={note}>{note}</li>)}
      </ul>

      <details className="twin-panel__figures">
        <summary>측정치 보기</summary>
        <dl>
          <div><dt>Δ(내용 성분)</dt><dd>{pair.deltaText}</dd></div>
          <div><dt>신뢰구간</dt><dd>{pair.intervalText ?? '—'}</dd></div>
          <div><dt>측정 한계(MDE)</dt><dd>{format3(pair.mde)}</dd></div>
          <div><dt>위치 성분</dt><dd>{format3(pair.positionComponent)}</dd></div>
          <div><dt>확정 응답자</dt><dd>{pair.nPaired}명 / {pair.nRespondents}명</dd></div>
          <div><dt>유형</dt><dd>{pair.taskTypeView.label}</dd></div>
        </dl>
        <ul className="twin-panel__classes">
          {pair.classes.map((item) => <li key={item.key}>{item.label} {item.count}명</li>)}
        </ul>
        {result.sampling.hasShortCells ? (
          <p className="twin-panel__short">층이 얕아 목표를 못 채운 셀이 있다 — 실효표본이 작다.</p>
        ) : null}
      </details>
    </section>
  );
}

function InterviewCard({ interview, labels }) {
  const { head, sub, badge } = interviewLines(interview, labels);
  const tone = interview.choiceView.tone;
  return (
    <article className="twin-interview">
      <div className="twin-interview__head">
        <span className={`twin-interview__avatar tone-${tone}`}>
          {interview.profile.age ?? '—'}
        </span>
        <div className="twin-interview__who">
          <p className="twin-interview__line">{head}</p>
          <p className="twin-interview__sub">{sub}</p>
        </div>
        <span className={`twin-interview__badge tone-${tone}`}>{badge}</span>
      </div>
      <p className="twin-interview__quote">&ldquo;{interview.quote}&rdquo;</p>
    </article>
  );
}

function format3(value) {
  return typeof value === 'number' ? value.toFixed(3) : '—';
}
