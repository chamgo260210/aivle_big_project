import BmCanvas from './BmCanvas.jsx';
import { Alert, Badge } from '../../shared/ui';
import { CANVAS_CELL_LABEL, DECISION_VIEW, GATE_CAUSE_VIEW, GATE_TITLE } from './marketResult.js';
import './market.css';

/** 신뢰도 코드 → 사람이 읽는 말. 모르는 코드는 원문 그대로 통과시킨다. */
const CONFIDENCE_VIEW = { HIGH: '확신 높음', MEDIUM: '확신 중간', LOW: '확신 낮음' };

/** 부분 검사 판정. 모르는 코드는 원문 그대로 — 조용히 「통과」로 읽히면 안 된다. */
const CHECK_VIEW = { PASS: '통과', PARTIAL: '일부만', FAIL: '못 미침' };

/** 부분 검사 하나 — <b>라벨과 판정을 붙여서</b> 그린다. 판정만 떼면 무엇의 판정인지 사라진다. */
function Check({ label, status }) {
  if (!status) return <>{label} 미기재</>;
  return <>{label} <strong>{CHECK_VIEW[status] ?? status}</strong></>;
}

/**
 * 사업 검증의 <b>둘째 걸음</b> — BM 캔버스. 첫째 걸음(시장조사)의 결과를 근거로 채운다.
 *
 * <p>읽는 순서: 사유 → 판정 → 9칸 요약 → 강점·약점·위험.
 *
 * <p>⚠ <b>「칸별 세부」는 그리지 않는다.</b> 칸이 가진 것은 한두 줄이라 캔버스 밑에 다시
 * 펴도 새로 아는 것이 없고, 근거표가 9칸 × 전 항목으로 되풀이돼 화면이 통째로 길어진다
 * (2026-08-13 실측: 같은 근거 줄이 칸마다 다시 나왔다). 근거의 «정체»(값·기간·등급·출처)는
 * 시장 분석 과목 쪽에서 편다 — 그것을 «관측한» 자리가 거기다.
 * `BmCanvas.BmCellDetails` 는 남아 있지만 이 화면은 쓰지 않는다.
 *
 * <p>셸(제목·실행 버튼·진행 표시)은 갖지 않는다. `BusinessValidationPage` 가 갖는다.
 */
export function BmResultBody({ result }) {
  const bm = result?.bm ?? null;
  const decision = bm ? DECISION_VIEW[bm.decision] : null;

  return (
    <>
      <GateReasons reasons={bm?.gateReasons} />

      {bm ? (
        /* 목업 그대로: **큰 배지 + 한 줄 + 흐린 확신도**. 「판정」이라는 머리말은 두지
           않는다 — 배지가 곧 판정이라 같은 말을 두 번 하는 셈이다.

           ⚠ **봉투의 세 요약(`summary`·`marketFitSummary`·`consistencySummary`)을 여기에
           붙이지 마라.** 그것들은 «부분 검사»의 말이라 판정과 정면으로 어긋난다 —
           2026-08-13 실측: 판정 배지가 「수정 필요」인데 그 옆에 「구성 요소 간의 내부
           일관성이 유지되고 있다」가 붙어 있었다(둘 다 사실이다. 판정을 가른 것은
           게이트고, 부분 검사는 통과했다). 게다가 AI 산문은 반말이라 존댓말 화면과도
           갈린다. 그래서 부분 검사는 **라벨 붙은 사실**로만 그린다 — 판정과 어긋날 수
           있는 문장을 아예 만들지 않는다. 「왜 수정 필요인가」는 바로 위 게이트가 말한다. */
        <div className="ui-card bm-verdict">
          {decision ? <Badge tone={decision.tone}>{decision.label}</Badge> : null}
          <p>
            <Check label="시장 적합성" status={bm.marketFitStatus} />
            {' · '}
            <Check label="내부 일관성" status={bm.consistencyStatus} />
            {' · '}
            {/* 목업은 「확신도 72%」다. 봉투는 HIGH/MEDIUM/LOW 만 주므로 퍼센트를
                지어내지 않고 그 말을 그대로 쓴다. */}
            <span className="bm-verdict__conf">
              {CONFIDENCE_VIEW[bm.confidence] ?? bm.confidence ?? '신뢰도 미기재'}
            </span>
          </p>
        </div>
      ) : (
        <Alert tone="warning">
          BM 판정이 오지 않았어요 — 시장조사 결과는 그대로 유효해요. 다시 만들어 볼 수 있어요.
        </Alert>
      )}

      {result.canvas ? <BmCanvas cells={result.canvas} /> : null}

      {bm ? (
        <div className="bm-swr">
          {/* 제목의 이모지는 목업 그대로다(💪 🩹 ⚡). 셋을 색으로만 가르면 색을 못 보는
              사람에게 같은 상자 셋이 된다. */}
          <SwrBox title="💪 강점" items={bm.strengths} tone="var(--color-status-success)" />
          <SwrBox title="🩹 약점" items={bm.weaknesses} tone="var(--color-status-warning)" />
          <SwrBox title="⚡ 위험" items={bm.risks} tone="var(--color-status-danger)" />
        </div>
      ) : null}
    </>
  );
}

/**
 * 판정 게이트가 남긴 반증 사유 — <b>판정보다 먼저</b> 읽혀야 한다.
 *
 * <p>왜 맨 위인가. 판정 badge 만 보면 「조건부」가 「거의 다 됐다」로 읽힌다. 실제로는
 * 채널 칸의 자료가 0건이라 확인할 방법 자체가 없는 것일 수 있다. 그 사실이 판정 옆이 아니라
 * <b>앞에</b> 서야 한다.
 *
 * <p>비어 있으면 아무것도 그리지 않는다 — 「이유 0건」은 「검사를 안 했다」로 오해된다.
 *
 * <p><b>갈래(`cause`)를 같이 보인다.</b> 「못 찾음」은 컨셉을 고쳐도 안 고쳐지고,
 * 「연결 안 됨」은 사용자가 할 일이 없다. 이 둘이 한 덩어리로 보이면 사용자가 컨셉을 다듬어
 * 수집 실패를 통과시키는 길이 열린다 — 그게 우리가 만든 방식의 「다 패스」다.
 */
function GateReasons({ reasons }) {
  if (!reasons?.length) return null;
  return (
    <Alert tone="danger">
      <strong>아직 판매할 수 없어요 — 해결할 문제 {reasons.length}가지</strong>
      <ul>
        {reasons.map((reason, index) => {
          const cause = GATE_CAUSE_VIEW[reason.cause] ?? GATE_CAUSE_VIEW.UNMAPPED;
          return (
            <li key={`${reason.code}-${reason.cell ?? index}`}>
              {GATE_TITLE[reason.code] ?? reason.code}
              {reason.cell ? ` · ${CANVAS_CELL_LABEL[reason.cell] ?? reason.cell}` : ''}
              {' — '}
              {reason.message}
              {reason.evidenceIds.length
                ? ` (근거 ${reason.evidenceIds.join(', ')})`
                : ''}
              {' '}
              <Badge tone={cause.tone}>{cause.label}</Badge>
              <div className="market-note">{cause.note}</div>
            </li>
          );
        })}
      </ul>
    </Alert>
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
