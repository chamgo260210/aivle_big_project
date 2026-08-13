import { useCallback, useMemo, useState } from 'react';
import { Alert, Badge, Button, Card } from '../../shared/ui';
import Emphasis from './emphasis.jsx';
import { SUBJECT_LABEL, subjectNumber } from './marketResult.js';
import {
  REVISION_FIELD_LABEL, conceptDocument, highlightChanges, narrativeParts, normalizeDeltaLegal,
} from './conceptRevision.js';

/**
 * 사업 검증의 <b>둘째 화면 — 「다듬어진 컨셉」</b>.
 *
 * <p>라운드 이력은 보이지 않는다. 3라운드의 시행착오를 그대로 늘어놓으면 결론이 묻힌다.
 * 다만 <b>못 푼 것은 반드시 보인다</b> — 수렴 못 한 채 끝난 것을 성공처럼 보이면
 * 그것이 조용한 거짓말이다.
 *
 * <p>읽는 순서가 이 화면의 전부다: <b>컨셉 원문 → 무엇이 왜 바뀌었나 → 그 근거가 있는
 * 화면 1 의 과목 → 법률 검토</b>. 세 걸음이 끊기면 「우리가 만든 방식의 다 패스」와
 * 구분이 안 된다.
 */
const OUTCOME_VIEW = {
  CONVERGED: { label: '다듬기 완료', tone: 'success',
    note: '법률 검토까지 통과했어요.' },
  NOTHING_TO_FIX: { label: '고칠 것 없음', tone: 'neutral',
    note: '시장 근거로 바꿀 것이 나오지 않았어요 — 컨셉은 그대로예요.' },
  ROUND_LIMIT: { label: '못 푼 것이 남았어요', tone: 'warning',
    note: '3라운드 안에 수렴하지 못했어요. 아래 「못 푼 것」이 그 사유예요.' },
  RUNNING: { label: '다듬는 중', tone: 'info', note: '법률 검토를 기다리고 있어요.' },
  NOT_STARTED: { label: '아직 안 함', tone: 'neutral',
    note: '아직 다듬은 결과가 없어요 — 아래는 확정된 사업안 원문이에요.' },
};

export default function RefinementSummary({
  result, concept,
  evidenceSubjects, onJumpSubject, onBack, onFinalize, finalizing = false, error = null,
}) {
  const view = OUTCOME_VIEW[result?.outcome] ?? OUTCOME_VIEW.NOT_STARTED;
  const changes = result?.changes ?? [];
  const blocks = useMemo(() => conceptDocument(concept), [concept]);
  // 서술문이 있으면 한 문단으로 읽힌다. 없으면(아직 안 썼거나 검증을 못 통과했으면)
  // 칸 나열로 폴백한다 — 반쯤 맞는 문장을 컨셉 원문 자리에 세우지 않는다.
  const narrative = useMemo(() => narrativeParts(result?.narrative), [result?.narrative]);
  // 델타만. 전체 보고서는 읽지도 않는다 — `useRevision` 주석 참고.
  const legal = useMemo(() => normalizeDeltaLegal(result?.deltaLegal), [result?.deltaLegal]);

  // 카드 안 점프(원문 → 변경 항목)는 화면을 넘지 않는다. 착지만 잠깐 물들인다.
  const [landed, setLanded] = useState(null);
  const jump = useCallback((id) => {
    setLanded(id);
    document.getElementById(id)?.scrollIntoView({ block: 'center' });
  }, []);

  return (
    <>
      <div className="pipeline-page-heading">
        <p>3. 사업 검증</p>
        <h2>다듬어진 컨셉</h2>
        <span>
          검증 결과를 반영해 컨셉을 이렇게 고쳤어요. 초록색 부분이 바뀐 곳이에요.
          번호를 누르면 이유를 볼 수 있어요.
        </span>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}

      <p className="cr-outcome">
        <Badge tone={view.tone}>{view.label}</Badge>
        {' '}<span className="market-note">{view.note}</span>
      </p>

      {/* 컨셉 원문 — 문서처럼 읽히도록 15px / 줄간 2.0.
          ⚠ **화면이 칸을 접착제 문장으로 잇지 않는다.** 한 문단으로 읽히는 것은 서버가
          검증한 서술문이 있을 때뿐이고, 그 검증은 「바뀐 조각이 정말 그 값을 담았나」다.
          없으면 칸 나열로 떨어진다 — 지어낸 접착제는 아무도 쓴 적 없는 말이다. */}
      <Card>
        {narrative ? (
          <div className="cr-doc cr-doc--flow">
            <p>{narrative.map((part, index) => (
              part.ref ? (
                <button key={`nr-${index}`} type="button" className="cr-chg"
                  onClick={() => jump(`cr-why-${part.ref}`)}>
                  {part.text}<sup>{part.ref}</sup>
                </button>
              ) : <span key={`nr-${index}`}>{part.text}</span>
            ))}</p>
          </div>
        ) : blocks.length > 0 ? (
          <div className="cr-doc">
            {blocks.map((block) => (
              <p key={block.key}>
                <span className="cr-doc__k">{block.label}</span>
                {highlightChanges(block.text, changes).map((part, index) => (
                  part.ref ? (
                    <button key={`${block.key}-${index}`} type="button" className="cr-chg"
                      onClick={() => jump(`cr-why-${part.ref}`)}>
                      {part.text}<sup>{part.ref}</sup>
                    </button>
                  ) : <span key={`${block.key}-${index}`}>{part.text}</span>
                ))}
              </p>
            ))}
          </div>
        ) : (
          <p className="bm-cell__none">확정된 사업안 원문을 아직 불러오지 못했어요.</p>
        )}
      </Card>

      <Card title="무엇이, 왜 바뀌었나요">
        <p className="market-note">바뀐 곳마다 어떤 조사 결과 때문인지 이어 두었어요.</p>
        {changes.length > 0 ? changes.map((change, index) => (
          <Change key={`${change.round}-${change.field}-${index}`} change={change} no={index + 1}
            landed={landed === `cr-why-${index + 1}`} legal={legal}
            evidenceSubjects={evidenceSubjects} onJumpSubject={onJumpSubject} onJumpClause={jump} />
        )) : <p className="bm-cell__none">바뀐 칸이 없어요.</p>}
      </Card>

      {/* 막힌 것은 접지 않는다 — 접으면 「다 됐다」로 읽힌다. */}
      {result?.unresolved?.length ? (
        <Alert tone="warning">
          <strong>못 푼 것 {result.unresolved.length}</strong>
          <ul>{result.unresolved.map((line) => <li key={line}>{line}</li>)}</ul>
        </Alert>
      ) : null}

      <Card title="법률 검토">
        {/* ⚠ 이 문장을 빼지 마라. 경계 표시다. */}
        <p className="market-note">
          <Emphasis text="**법률 자문이 아니에요.** 판매하기 전에 직접 확인해야 할 자리를 짚어 드려요." />
        </p>
        {/* 없으면 «아직 안 돌았다»는 뜻이다 — 조회 실패가 아니다. 전체 보고서로 대신
            채우지 않는다. 그러면 부분 검사가 전체 검사로 보인다. */}
        {legal ? <LegalBody legal={legal} landed={landed} /> : (
          <p className="bm-cell__none">
            아직 다시 본 법이 없어요 — 컨셉이 바뀌면 바뀐 것에 걸리는 법만 다시 봐요.
          </p>
        )}
      </Card>

      {/* ⚠ **수렴 못 했어도 막지 않는다.** 못 푼 것을 위에 보인 채로 확정할 수 있어야
          사용자가 자기 사업안을 앞으로 끌고 갈 수 있다. 막으면 길이 끊긴다. */}
      <div className="bv-foot">
        <Button variant="ghost" onClick={onBack}>← 검증 결과로</Button>
        {onFinalize && result?.outcome !== 'RUNNING' ? (
          <Button onClick={onFinalize} disabled={finalizing}>
            {finalizing ? '확정하는 중…' : '이 컨셉으로 확정하기'}
          </Button>
        ) : null}
      </div>
      <p className="market-note cr-foot-note">
        확정하면 이것이 최종 컨셉이고, 기술·운영·재무·마케팅이 이것을 읽어요.
      </p>
    </>
  );
}

/**
 * 변경 한 항목 — <b>무엇을 했나 → 옛 문구 → 새 문구 → 이유 → 그 근거</b>.
 *
 * <p>근거는 두 갈래다. <b>시장 근거</b>면 화면 1 의 과목으로, <b>법률</b>이면 아래 법률
 * 검토의 그 조항으로 간다. 어느 쪽도 없으면 배지로 그렇게 말한다 — 근거 없는 변경을
 * 이유만으로 그리면 「조사가 시킨 일」처럼 읽힌다.
 */
function Change({ change, no, landed, legal, evidenceSubjects, onJumpSubject, onJumpClause }) {
  const ids = change.evidenceIds ?? [];
  const subject = ids.map((id) => evidenceSubjects?.get(id)).find(Boolean) ?? null;
  const number = subject ? subjectNumber(subject) : null;
  // 법률이 시킨 변경은 조항을 가리킨다. 번호는 아래 법률 카드의 순서와 **같은 번호**여야
  // 한다 — 다르면 「법률 검토 1」이 어느 줄인지 알 수 없다.
  const clauseAt = change.source === 'LEGAL' && change.legalRef
    ? (legal?.clauses ?? []).findIndex((clause) => change.legalRef.includes(clause.lawName))
    : -1;

  return (
    <div id={`cr-why-${no}`} className={`cr-why${landed ? ' is-on' : ''}`}>
      <p className="cr-why__h">
        <span className="cr-tag">{no}</span>
        {/* 제목이 없으면(이 칸이 생기기 전 라운드) 필드 라벨로 떨어진다 — 거짓이 되지 않는다. */}
        <b>{change.title || REVISION_FIELD_LABEL[change.field] || change.field}</b>
      </p>
      <p className="cr-diff">
        <span className="cr-old">{change.before || '(비어 있었어요)'}</span>
        {' → '}<b>{change.after}</b>
      </p>
      <p className="cr-why__r">{change.reason}</p>
      {clauseAt >= 0 ? (
        <button type="button" className="cr-from"
          onClick={() => onJumpClause(`cr-law-${legal.clauses[clauseAt].key}`)}>
          근거 보기 — 법률 검토 {clauseAt + 1}
        </button>
      ) : ids.length === 0 ? (
        <Badge tone="warning">근거 없음</Badge>
      ) : subject ? (
        <button type="button" className="cr-from" onClick={() => onJumpSubject(subject)}>
          근거 보기 — 시장 분석 {number}. {SUBJECT_LABEL[subject] ?? subject}
          {ids.map((id) => <code key={id} className="cr-ev">{id}</code>)}
        </button>
      ) : (
        <p className="cr-why__r">
          {ids.map((id) => <code key={id} className="cr-ev">{id}</code>)}
          {' '}이 근거는 지금 화면의 조사 결과에 없어요.
        </p>
      )}
    </div>
  );
}

/**
 * 델타 법률 검토 본문 — <b>바뀐 것에 걸리는 법만</b>.
 *
 * <p>⚠ <b>법 조문 해설을 싣지 않는다.</b> 「기준·규격이 정하여지지 아니한 화학적 합성품
 * 등의 판매 금지에 대한 의무」는 제6조의 제목이지 이 컨셉의 이야기가 아니다. 싣는 것은
 * 검토가 조항마다 이어 둔 <b>소견</b>(`findings`)이다 — 그것이 「이 컨셉이 왜 걸리는가」다.
 *
 * <p>⚠ 조항마다 상태 배지를 <b>지어내지 않는다</b> — 없는 판정을 그리면 그것이 곧 근거 없는
 * 「확인됨」이다. 서버가 준 값이 없으면 배지 없이 그린다.
 */
function LegalBody({ legal, landed }) {
  return (
    <>
      <p className="cr-law__sum">
        {legal.status ? <Badge tone={legal.status.tone}>{legal.status.label}</Badge> : null}
        {/* 이것이 «부분 검사»임을 말하는 유일한 자리다. 안 적으면 전체를 다시 본 것으로 읽힌다. */}
        {legal.changed.length > 0 ? (
          <span> 이번에 바뀐 <b>{legal.changed.join(' · ')}</b>에 걸리는 법만 다시 봤어요.</span>
        ) : null}
      </p>

      {legal.clauses.map((clause, index) => (
        <div key={clause.key} id={`cr-law-${clause.key}`}
          className={`cr-law${landed === `cr-law-${clause.key}` ? ' is-on' : ''}`}>
          <p className="cr-law__h">
            <b>{index + 1}. {clause.lawName}</b>
            {clause.status ? <Badge tone={clause.status.tone}>{clause.status.label}</Badge> : null}
            {/* 주제는 검토가 이 컨셉의 말로 적은 것이다. 없으면 조문 번호로 떨어진다 —
                조문 «제목»으로 대신 채우지 않는다. 그건 법 설명이다. */}
            {clause.findings[0]?.topic
              ? <span className="cr-topic">{clause.findings[0].topic}</span>
              : clause.article ? <code className="cr-clause">{clause.article}</code> : null}
            {clause.url ? (
              <a href={clause.url} target="_blank" rel="noreferrer">법령 원문</a>
            ) : null}
          </p>
          {clause.findings.map((finding, at) => (
            <p key={`${clause.key}-f${at}`} className="cr-law__r">{finding.text}</p>
          ))}
        </div>
      ))}

      {legal.clauses.length === 0 ? (
        <p className="bm-cell__none">이번에 새로 걸린 법이 없어요.</p>
      ) : null}
    </>
  );
}
