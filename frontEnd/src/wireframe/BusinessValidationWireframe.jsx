/**
 * 「사업 검증」→「다듬어진 컨셉」 두 화면의 와이어프레임 본체.
 *
 * <p>진입점은 `main.jsx` 다. 컴포넌트를 그쪽에 두면 Fast Refresh 가 깨진다
 * (`react-refresh/only-export-components`).
 */
import { useState } from 'react';

import { Alert, Badge, Button, Card } from '../shared/ui';
import BmCanvas, { GradeBadge, SourceLink } from '../features/market/BmCanvas.jsx';
// 2·8·9절은 **제품 부품을 그대로 쓴다.** 와이어프레임이 자기 마크업으로 베끼면
// 화면과 와이어프레임이 갈리고, 그러면 와이어프레임이 거짓말이 된다.
import { JudgmentCard, PrescriptionCard, SynthesisCard } from '../features/market/MarketResultBody.jsx';
import Emphasis from '../features/market/emphasis.jsx';
import useCellFocus from '../features/market/useCellFocus.js';
import {
  CANVAS_CELL_LABEL, DECISION_VIEW, GATE_CAUSE_VIEW, GATE_TITLE, SCORE_STATE_VIEW,
  SECTION_ORDER, SECTION_TITLE, SUBJECT_LABEL,
  abbreviateKrw, formatValue, normalizeMarketResult,
} from '../features/market/marketResult.js';
import { SAMPLE_RESULT, SAMPLE_REVISION, SUBJECT_EVIDENCE } from './sample.js';

const result = normalizeMarketResult(SAMPLE_RESULT);

/** 매 렌더마다 다시 만들 이유가 없다 — 결과가 모듈 상수라 표도 상수다. */
const SCORE = Object.fromEntries((result.scorecard ?? []).map((row) => [row.subject, row]));
// ⚠ **목차의 정본은 `SECTION_ORDER` 다** (판 ㊺). 예전에는 `Object.keys(SUBJECT_LABEL)` 로
//    셌는데, 성장률·계산이 1절 «안»으로 접히고 8·9절이 새로 서면서 **10칸·옛 순서**로
//    갈렸다. 이 파일 위 주석이 「와이어프레임이 갈리면 거짓말이 된다」고 스스로 적어 뒀고,
//    실제로 갈렸다 — 화면 테스트는 이 파일을 안 본다.
const SUBJECTS = SECTION_ORDER.map((subject, index) => [subject, index + 1]);
const NOOP = () => {};

/** 조사는 20분 넘게 걸린다 — 「중」이 반드시 자리를 가져야 한다. */
const PHASES = [['idle', '조사 전'], ['running', '조사 중'], ['done', '결과']];

export default function BusinessValidationWireframe() {
  const [screen, setScreen] = useState('validation');
  const [phase, setPhase] = useState('done');
  const secFocus = useCellFocus('sec-');
  const bm = result.bm;

  // 화면 2 의 「근거 보기」는 화면 1 의 그 과목으로 착지한다. 화면을 먼저 바꾸고
  // 다음 프레임에 점프한다 — 같은 틱에 부르면 아직 그 노드가 없다.
  const jumpToSubject = (subject) => {
    setScreen('validation');
    requestAnimationFrame(() => secFocus.jump(subject));
  };

  return (
    <div className="wf">
      <div className="wf__bar">
        <b>와이어프레임</b>
        <span>계획서 2-3 · 3-5 · <b>데이터는 예시입니다 — 실제 조사 결과가 아닙니다</b></span>
        <div className="wf__phases">
          {screen === 'validation' && PHASES.map(([key, label]) => (
            <button key={key} type="button" onClick={() => setPhase(key)}
              className={phase === key ? 'is-on' : ''}>{label}</button>
          ))}
        </div>
      </div>

      <div className="wf__shell">
        <aside className="wf__side">
          <p>셸이 이미 그린다</p>
          <ul>
            {['1. 아이디어', '2. 사업안', '3. 사업 검증', '4. 기술·운영',
              '5. 재무', '6. 시장 인터뷰', '7. 마케팅'].map((label) => (
              <li key={label} className={label.startsWith('3.') ? 'is-on' : ''}>{label}</li>
            ))}
          </ul>
        </aside>

        <main className="market-page wf__main">
          <Steps screen={screen} onGo={setScreen} />
          {screen === 'validation' ? (
            <Validation phase={phase} setPhase={setPhase} bm={bm} secFocus={secFocus}
              onNext={() => setScreen('revision')} />
          ) : (
            <Revision onBack={() => setScreen('validation')} onJump={jumpToSubject} />
          )}
        </main>
      </div>
    </div>
  );
}

/**
 * 이 단계가 <b>두 장</b>이라는 것을 먼저 알린다.
 *
 * <p>왼쪽 셸의 여정은 「3. 사업 검증」한 칸이라, 그 안에서 화면이 두 번 넘어간다는 사실이
 * 어디에도 없다. 그러면 「다음」을 누른 사람이 다음 <b>단계</b>로 넘어간 줄 안다.
 */
function Steps({ screen, onGo }) {
  const steps = [
    ['validation', '사업 검증'],
    ['revision', '다듬어진 컨셉'],
  ];
  return (
    <ol className="wf-steps">
      {steps.map(([key, label], index) => (
        <li key={key}>
          <button type="button" onClick={() => onGo(key)}
            className={screen === key ? 'is-on' : ''}
            aria-current={screen === key ? 'step' : undefined}>
            <span className="wf-steps__n">{index + 1}</span>{label}
          </button>
        </li>
      ))}
    </ol>
  );
}

/* ══════════════ 화면 1 — 사업 검증 ══════════════ */

function Validation({ phase, setPhase, bm, secFocus, onNext }) {
  return (
    <>
      <div className="pipeline-page-heading">
        <p>3. 사업 검증</p>
        <h2>
          사업 검증
          {phase === 'done' && bm ? (
            <> <Badge tone={DECISION_VIEW[bm.decision]?.tone ?? 'neutral'}>
              {DECISION_VIEW[bm.decision]?.label ?? bm.decision}
            </Badge></>
          ) : null}
        </h2>
        <span>시장 조사와 비즈니스 모델 분석으로 사업안을 검증해요.</span>
      </div>

      {phase === 'idle' ? <IdlePhase onStart={() => setPhase('running')} /> : null}
      {phase === 'running' ? <RunningPhase /> : null}
      {phase === 'done' ? (
        <DonePhase bm={bm} secFocus={secFocus}
          onAgain={() => setPhase('idle')} onNext={onNext} />
      ) : null}
    </>
  );
}

/** 경쟁 씨앗을 받는 자리는 **실행 버튼 바로 위**다 — 조사 뒤엔 그 판에 못 넣는다. */
function IdlePhase({ onStart }) {
  return (
    <Card title="시장 조사">
      <p className="market-note">아직 조사하지 않았어요. 조사에는 보통 20분 넘게 걸려요.</p>
      <label className="wf__label" htmlFor="wf-seed">알고 있는 경쟁사가 있다면 적어 주세요 (선택)</label>
      <input id="wf-seed" className="wf__input" type="text"
        placeholder="예: A사 냉동 도시락, B사 프리미엄 밀키트" />
      <p className="market-note">적어 주신 경쟁사는 조사에 함께 반영돼요.</p>
      <div className="mr-actions"><Button onClick={onStart}>시장조사 시작하기</Button></div>
    </Card>
  );
}

/** 경과 시간만으로는 멈춘 줄 안다. 진행바는 **제품에 아직 없는 부품**이다. */
function RunningPhase() {
  return (
    <Card title="조사하고 있어요">
      <p className="market-note">
        <b>4분 12초</b> 지났어요 · 보통 20분 넘게 걸려요. 이 화면을 닫아도 조사는 계속돼요.
      </p>
      <div className="wf__runbar"><i /></div>
    </Card>
  );
}

function DonePhase({ bm, secFocus, onAgain, onNext }) {
  const [open, setOpen] = useState(null);
  const m = result.market ?? {};

  return (
    <>
      <div className="wf__sec">
        <h3>시장 분석</h3>
        <span>{result.asOf} 에 조사를 마쳤어요 · 반영된 경쟁사: A사 냉동 도시락 · B사 프리미엄 밀키트</span>
        <Button onClick={onAgain}>다시 조사하기</Button>
      </div>

      <div className="mr-kpis">
        <Kpi label="전체 시장 (TAM)" figure={m.tam} />
        <Kpi label="노릴 수 있는 시장 (SAM)" figure={m.sam} />
        <Kpi label="연 성장률" figure={m.growth} />
        {m.price ? (
          <div className="mr-kpi">
            <span>시장 가격대</span>
            <b className="num">
              {formatValue(m.price.min, m.price.currency)}
              {' ~ '}{formatValue(m.price.max, m.price.currency)}
            </b>
            <small className="num">{formatValue(m.price.base, m.price.currency)} (잠정 대표값)</small>
            <GradeBadge grade={m.price.grade} />
          </div>
        ) : null}
      </div>

      {/* 2절 — 값을 보여 주는 것과 **「그래서 어디에 서 있나」를 말해 주는 것**은 다른 일이다. */}
      <JudgmentCard judgment={result.judgment} />

      <Card>
        {SUBJECTS.map(([subject, n]) => (
          <Subject key={subject} subject={subject} n={n} row={SCORE[subject]}
            open={open === subject} focused={secFocus.active === subject}
            onToggle={() => setOpen((prev) => (prev === subject ? null : subject))} />
        ))}
        {/* 읽는 조건은 **과목 밑 한 줄**이다. 카드로 세우면 그것부터 읽히는데,
            이건 숫자를 읽고 난 다음에 붙는 단서다. */}
        <p className="wf-basis">
          {m.coverageCaveat}
          {m.price?.caveats.map((line) => <Emphasis key={line} text={` ${line}`} />)}
        </p>
      </Card>

      {/* 8절 — 「못 구했다」로 끝내면 사업가는 거기서 멈춘다. **어디서 구하는지**까지 적는다. */}
      <PrescriptionCard rows={result.prescriptions} />

      {/* 9절 — 사업가가 돈을 내는 자리. 사실이 이 사업안을 **미는지 흔드는지**를 말한다. */}
      <SynthesisCard rows={result.synthesis} />

      <div className="wf__sec">
        <h3>비즈니스 모델</h3>
        <span>시장 분석에서 확인된 근거로만 채웠어요</span>
      </div>

      {bm?.gateReasons?.length ? (
        <Alert tone="danger">
          <strong>아직 판매할 수 없어요 — 해결할 문제 {bm.gateReasons.length}가지</strong>
          <ul>
            {bm.gateReasons.map((reason, index) => {
              const cause = GATE_CAUSE_VIEW[reason.cause] ?? GATE_CAUSE_VIEW.UNMAPPED;
              return (
                <li key={`${reason.code}-${reason.cell ?? index}`}>
                  {GATE_TITLE[reason.code] ?? reason.code}
                  {reason.cell ? ` · ${CANVAS_CELL_LABEL[reason.cell] ?? reason.cell}` : ''}
                  {' — '}{reason.message}{' '}
                  <Badge tone={cause.tone}>{cause.label}</Badge>
                  <div className="wf-basis">{cause.note}</div>
                </li>
              );
            })}
          </ul>
        </Alert>
      ) : null}

      {bm ? (
        <div className="ui-card bm-verdict">
          <h3>판정</h3>
          <Badge tone={DECISION_VIEW[bm.decision]?.tone ?? 'neutral'}>
            {DECISION_VIEW[bm.decision]?.label ?? bm.decision}
          </Badge>
          <p>{bm.consistencySummary ?? bm.summary}</p>
        </div>
      ) : null}

      {/* 칸별 세부는 뺐다 — 칸이 가진 것은 한두 줄이라 아래에 다시 펴도 새로 아는 게 없다.
          근거의 «정체»(값·기간·등급·출처)는 시장 분석 과목 쪽에서 편다. */}
      {result.canvas ? <BmCanvas cells={result.canvas} onJump={NOOP} /> : null}

      <div className="bm-swr">
        <SwrBox title="강점" items={bm?.strengths ?? []} tone="var(--color-status-success)" />
        <SwrBox title="약점" items={bm?.weaknesses ?? []} tone="var(--color-status-warning)" />
        <SwrBox title="위험" items={bm?.risks ?? []} tone="var(--color-status-danger)" />
      </div>

      <div className="mr-actions">
        <Button onClick={onNext}>다음 — 다듬어진 컨셉</Button>
      </div>
    </>
  );
}

/**
 * 성적표 한 과목 — <b>눌러서 근거를 편다.</b>
 *
 * <p>왜 여기인가. 「채워짐」과 한 줄 요약만으로는 <b>무엇을 얼마나 찾았는지</b>가 안 보인다.
 * 조사는 많이 해 놓고 화면의 정보량이 요약 한 줄뿐이면 「많이 찾았다」가 증명되지 않는다.
 * 값·기간·등급·<b>출처 링크</b>는 BM 이 아니라 그것을 관측한 이 자리에 붙는다.
 */
function Subject({ subject, n, row, open, focused, onToggle }) {
  const view = row ? SCORE_STATE_VIEW[row.state] : null;
  const items = (SUBJECT_EVIDENCE[subject] ?? [])
    .map((id) => result.evidenceById.get(id))
    .filter(Boolean);
  const blocks = subject === 'NOT_FOUND' ? (result.market?.notFound ?? []) : [];
  const openable = items.length > 0 || blocks.length > 0;

  return (
    <div id={`sec-${subject}`} className={`wf-sub${focused ? ' is-on' : ''}`}>
      <button type="button" className="wf-sub__h" onClick={openable ? onToggle : NOOP}
        aria-expanded={openable ? open : undefined} disabled={!openable}>
        <span className="wf__n num">{n}</span>
        {/* 목차 제목은 목표 보고서 것 — 화면과 같은 표를 본다. */}
        <b>{SECTION_TITLE[subject] ?? SUBJECT_LABEL[subject] ?? subject}</b>
        {view ? <Badge tone={view.tone}>{view.label}</Badge> : null}
        <span className="wf-sub__d">{row?.detail ?? '이 과목은 결과에 없어요.'}</span>
        {openable ? (
          <span className="wf-sub__c" aria-hidden="true">
            {open ? '접기' : `근거 ${items.length || blocks.length}건 보기`}
          </span>
        ) : null}
      </button>

      {open ? (
        <div className="wf-sub__b">
          {items.length > 0 ? (
            <table className="mr-table">
              <thead>
                <tr><th>값</th><th>항목</th><th>기간</th><th>등급</th><th>출처</th></tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td className="v num">{formatValue(item.value, item.unit)}</td>
                    <td>
                      <code className="wf-ev">{item.id}</code> {item.subject} · {item.metric}
                      {item.quote ? <div className="mr-quote">“{item.quote}”</div> : null}
                      {item.formula ? <div className="wf-basis">{item.formula}</div> : null}
                      {item.assumptions.map((line) => (
                        <div key={line} className="mr-caveat"><Emphasis text={line} /></div>
                      ))}
                      {item.caveats.map((line) => (
                        <div key={line} className="mr-caveat"><Emphasis text={line} /></div>
                      ))}
                    </td>
                    <td className="p num">{item.period ?? '—'}</td>
                    <td><GradeBadge grade={item.grade} /></td>
                    <td className="s"><SourceLink item={item} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
          {blocks.map((block) => (
            <div key={block.key} className="wf-sub__nf">
              <b>{block.label}<small className="num"> {block.count}건</small></b>
              <ul>{block.entries.map((line) => <li key={line}>{line}</li>)}</ul>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/* ══════════════ 화면 2 — 다듬어진 컨셉 ══════════════ */

/**
 * ⚠ 이 화면의 계약은 **아직 없다**(계획서 3-5). 자리와 읽는 순서만 세운 것이다.
 * 바뀐 곳 → 왜 → 그 근거가 있는 화면 1 의 과목, 세 걸음이 끊기면 「우리가 만든 방식의
 * 다 패스」와 구분이 안 된다.
 */
function Revision({ onBack, onJump }) {
  const [active, setActive] = useState(null);
  const go = (id) => {
    setActive(id);
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  return (
    <>
      <div className="pipeline-page-heading">
        <p>3. 사업 검증</p>
        <h2>다듬어진 컨셉</h2>
        <span>
          검증 결과를 반영해서 컨셉을 이렇게 고쳤어요. 초록색으로 표시한 곳이 바뀐
          부분이고, 번호를 누르면 왜 그렇게 고쳤는지 볼 수 있어요.
        </span>
      </div>


      <Card>
        <p className="wf-doc">
          {SAMPLE_REVISION.parts.map((part, index) => (
            part.ref ? (
              <button key={index} type="button" className="wf-chg"
                onClick={() => go(`why-${part.ref}`)}>
                {part.text}<sup>{part.ref}</sup>
              </button>
            ) : <span key={index}>{part.text}</span>
          ))}
        </p>
      </Card>

      <Card title="어디를, 왜 고쳤나요">
        {SAMPLE_REVISION.changes.map((change) => (
          <div key={change.ref} id={`why-${change.ref}`}
            className={`wf__row wf__row--why${active === `why-${change.ref}` ? ' is-on' : ''}`}>
            <span className="wf-tag">{change.ref}</span>
            <b>{change.title}</b>
            <p>
              <span className="wf-old">{change.before}</span>
              {' → '}<b>{change.after}</b>
            </p>
            <p>{change.why}</p>
            <p>
              <button type="button" className="wf-from" onClick={() => (
                change.from.law ? go(`law-${change.from.law}`) : onJump(change.from.subject)
              )}>
                근거 보기 — {change.from.label}
                {(change.from.evidenceIds ?? []).map((id) => (
                  <code key={id} className="wf-ev">{id}</code>
                ))}
              </button>
            </p>
          </div>
        ))}
      </Card>

      <Card title="법률 검토">
        <p className="market-note">
          <Emphasis text="**법률 자문이 아니에요.** 판매하기 전에 직접 확인해야 할 자리를 짚어 드려요." />
        </p>
        {SAMPLE_REVISION.laws.map((law) => (
          <div key={law.id} id={`law-${law.id}`}
            className={`wf__row${active === `law-${law.id}` ? ' is-on' : ''}`}>
            <span className="wf__n num">{law.id.slice(1)}</span>
            <b>{law.name}</b>
            <Badge tone={law.tone}>{law.status}</Badge>
            <p><code className="wf-ev">{law.clause}</code></p>
            <p>{law.note}</p>
          </div>
        ))}
      </Card>

      <div className="mr-actions">
        <Button variant="ghost" onClick={onBack}>이전 — 사업 검증</Button>
        <Button>이 컨셉으로 확정하기</Button>
      </div>
    </>
  );
}

/* ══════════════ 조각 ══════════════ */

function Kpi({ label, figure }) {
  if (!figure) return null;
  const short = figure.unit === 'KRW' ? abbreviateKrw(figure.value) : null;
  const full = formatValue(figure.value, figure.unit);
  return (
    <div className="mr-kpi">
      <span>{label}</span>
      <b className="num">{short ?? full}</b>
      <small className="num">{short ? full : ''}</small>
      <GradeBadge grade={figure.grade} />
    </div>
  );
}

function SwrBox({ title, items, tone }) {
  return (
    <div>
      <h4 style={{ color: tone }}>{title}</h4>
      <ul>
        {items.length > 0
          ? items.map((line) => <li key={line}>{line}</li>)
          : <li className="bm-swr__none">없음</li>}
      </ul>
    </div>
  );
}

