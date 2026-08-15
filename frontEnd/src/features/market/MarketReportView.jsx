import Markdown from './markdown.jsx';
import {
  MarketResultBody, PrescriptionCard, PriceScale, SynthesisCard,
} from './MarketResultBody.jsx';
import {
  SECTION_ORDER, SECTION_TITLE, SUBJECT_LABEL,
  factName, formatValue, headFacts, hostOf, sectionEvidence,
} from './marketResult.js';
import './marketReport.css';

/**
 * <b>시장조사 «보고서» 화면.</b> 목표 문서
 * `docs/market-research-redesign/market-report.html` 의 구조를 그대로 가진 한 문서다 —
 * 머리 → 절(번호+제목 → 큰 수 카드 → 글 → 표) → 꼬리.
 *
 * <p>★ <b>주인공은 글이다.</b> 절의 답을 사업가가 읽는 자리이고, 표는 그 글을 검산하는
 * 자리다. 그래서 <b>등급·인용 대조·경계 표시가 붙은 기존 화면</b>(`MarketResultBody`)은
 * 지우지 않고 <b>맨 아래 「근거로 검산하기」로 접어</b> 둔다 — 그것은 목표 보고서에
 * «없는» 우리 값어치다.
 *
 * <p>⚠ <b>봉투에 `report` 가 없으면 기존 화면을 그대로 낸다.</b> 글이 없는데 문서 틀만
 * 세우면 빈 절 아홉 개가 선다.
 */
export function MarketReportView({ result, activeId, onJump }) {
  const report = result.report;
  if (!report) return <MarketResultBody result={result} activeId={activeId} onJump={onJump} />;

  // 절 머리 카드는 «절 사실»에서 온다 — 서버가 정한 배치를 그대로 쓴다.
  const bag = sectionEvidence(result);
  // 글이 온 절만 세운다. 순서는 화면 목차 정본(`SECTION_ORDER`)이고,
  // 거기 없는 subject 가 오면 **버리지 않고** 뒤에 붙인다.
  const known = SECTION_ORDER.filter((subject) => proseOf(report, subject));
  const extra = report.sections
    .map((section) => section.subject)
    .filter((subject) => proseOf(report, subject) && !SECTION_ORDER.includes(subject));
  const subjects = [...known, ...new Set(extra)];

  return (
    <>
      <article className="mreport">
        <header className="top">
          <div className="eyebrow">시장조사 보고서</div>
          <h1>이 사업안이 들어가려는 시장</h1>
          <p>
            {[
              `근거 ${result.evidence.length}건`,
              typeof result.judgment?.price === 'number'
                ? `컨셉 판매가 ${Math.round(result.judgment.price).toLocaleString('ko-KR')}원`
                : null,
              result.asOf ? `조사 종료 ${result.asOf}` : null,
            ].filter(Boolean).join(' · ')}
          </p>
        </header>

        {/* ★ **경고는 여기 산다. 절대 빼지 않는다.** 아래 글은 전부 이 상자 뒤에 온다. */}
        <ReportOrigin report={report} evidenceCount={result.evidence.length} />

        {/* ★ **절마다 접히는 상자 하나.** 아홉 절을 한 장에 펼치면 스크롤이 열 화면을
            넘어가고, 그러면 사업가는 첫 절만 읽고 닫는다 — 실제로 그랬다. 상자로 접으면
            **목차와 본문이 한 물건**이 되어, 무엇이 있는지 먼저 보고 필요한 것만 연다.
            ⚠ 첫 절만 열어 둔다. 전부 접으면 「빈 화면」으로 읽힌다. */}
        {subjects.map((subject, index) => (
          <section key={subject}>
            <details className="secbox" open={index === 0}>
              <summary>
                <h2>
                  <span className="n">{index + 1}</span>
                  {SECTION_TITLE[subject] ?? SUBJECT_LABEL[subject] ?? subject}
                </h2>
                {/* 닫혀 있어도 **그 절에 무엇이 있는지**는 보이게 한다. */}
                <span className="peek">{peekOf(bag[subject] ?? [])}</span>
              </summary>
              <KeyBoxes rows={headFacts(bag[subject] ?? [])} />
              {/* 2절은 눈금자로 연다 — 「6,513원의 1.37배」는 글로 읽는 것과 자 위에서
                  보는 것이 다르다(목표 보고서 §2). 값은 이미 봉투에 있다. */}
              {subject === 'PRICE'
                ? <PriceScale judgment={result.judgment} rows={bag.PRICE ?? []} />
                : null}
              {/* ★ **8·9절은 «두 벌»이다.** 기계가 원장에서 뽑아 인용 대조를 거친 것과
                  모델이 쓴 글은 <b>다른 물건</b>이다 — 섞어 그리면 「기계가 판정했다」와
                  「모델이 썼다」가 구분되지 않는다. 이 서비스의 값어치가 그 구분이다.
                  그래서 <b>기계 것을 먼저</b> 세우고 모델 글에는 이름을 붙인다. */}
              {subject === 'GAPS' ? <PrescriptionCard rows={result.prescriptions} bare /> : null}
              {subject === 'SYNTHESIS' ? <SynthesisCard rows={result.synthesis} bare /> : null}
              {MACHINE_FIRST.has(subject) ? <ByAiNote /> : null}
              <Markdown text={proseOf(report, subject)} />
            </details>
          </section>
        ))}

        <footer>
          {/* 꼬리말은 지금 늘 `null` 이다 — 오면 저절로 뜬다. */}
          {report.tail ? <Markdown text={report.tail} /> : null}
          <p>
            재료 — 이번 조사가 모은 근거 <b>{result.evidence.length}건</b>
            {report.writtenBy ? <> · 글을 쓴 모델 <b>{report.writtenBy}</b></> : null}
          </p>
          <p>주요 출처 — {topHosts(result.evidence).join(' · ') || '출처 도메인을 세지 못했어요'}</p>
          <p>
            못 구한 것 · 등급 판정 · 인용 대조는 아래 <b>「근거로 검산하기」</b>에 그대로 있어요.
          </p>
        </footer>
      </article>

      {/* ⚠ **지우지 않는다.** 인용 대조·등급·출처 링크·경계 표시가 여기 있고,
          그것이 목표 보고서에 «없는» 우리 값어치다. 첫 화면의 주인공만 글에 내준다. */}
      <details className="mreport-check">
        <summary><b>근거로 검산하기</b> — 과목별 판정 · 근거 표 · 못 구한 것</summary>
        <MarketResultBody result={result} activeId={activeId} onJump={onJump} />
      </details>
    </>
  );
}

/**
 * <b>기계 것이 먼저 서는 절.</b> 8절 처방(`prescriptions`)과 9절 지지/흔듦(`synthesis`)은
 * 원장에서 뽑아 인용 대조를 거친 값이고, 같은 이름의 <b>모델 글</b>은 대조를 안 거쳤다.
 */
const MACHINE_FIRST = new Set(['GAPS', 'SYNTHESIS']);

/**
 * ★ <b>여기서부터는 모델이 쓴 글</b>이라고 이름을 붙인다.
 * 위(기계)와 아래(모델)를 사용자가 <b>한눈에</b> 갈라 볼 수 있어야 한다.
 */
function ByAiNote() {
  return (
    <p className="byai">
      <b>↓ 여기부터는 AI 가 쓴 정리</b>입니다 — 위는 원장에서 뽑아 <b>인용 대조를 거친</b> 값이고,
      아래 글은 <b>대조를 거치지 않았어요.</b>
    </p>
  );
}

/** 그 절의 글. 없으면 `null`. */
function proseOf(report, subject) {
  const found = report.sections.find((section) => section.subject === subject);
  return found && found.markdown ? found.markdown : null;
}

/**
 * ★ <b>이 글의 출처와 검증 상태.</b> 목표 보고서 머리의 `.origin` 자리다.
 *
 * <p>⚠ <b>경고를 빼지 않는다.</b> 이 글은 «검증을 통과한 값만 고른 것»이 아니라
 * 모델이 재료를 읽고 쓴 산문이다. 재료에 없던 수(유령 수)와 <b>사업가가 «입력한» 가정</b>이
 * 조사 결과인 척 섞여 들어온다 — 두 수는 봉투가 세어서 보내 준다.
 */
function ReportOrigin({ report, evidenceCount }) {
  const 유령 = report.unverifiedNumbers;
  const 누출 = report.conceptLeaks;
  return (
    <div className="origin">
      <p>
        <b>이 보고서의 출처</b> — 이번 조사가 모은 근거 <b>{evidenceCount}건</b>을
        {report.writtenBy ? <> AI(<b>{report.writtenBy}</b>)가</> : <> AI 가</>} 읽고 쓴 글이에요.
        새로 검색하지 않았어요.
      </p>
      <p>
        <b>⚠ 이 글은 AI 가 조사 결과를 읽고 쓴 것입니다.</b>
        {유령 > 0
          ? <> <b>재료에 없는 수가 {유령}개</b> 섞여 있어요 — 인용 전에 원문을 확인하세요.</>
          : <> 인용 전에 원문을 확인하세요.</>}
      </p>
      {누출 > 0 ? (
        <p>
          <b>사업가가 «입력한» 가정이 조사 결과처럼 섞인 것 {누출}개</b> — 그 수는
          조사 결과가 아니에요.
        </p>
      ) : null}
      {/* ⚠ **머리말도 경계 표시다.** 재료 건수·쓴 모델·인용 대조 여부가 여기 있다 —
          봉투가 보낸 문장을 화면이 다시 쓰지 않고 **그대로** 낸다. */}
      {report.lead ? <Markdown text={report.lead} className="origin__lead" /> : null}
    </div>
  );
}

/**
 * 절을 여는 <b>큰 수 카드 셋</b> — 목표 보고서의 `.kpi .box` 규격.
 * <p>⚠ 여기서 <b>새 판정을 만들지 않는다</b> — 절 머리에 이미 앞서 있는 셋을 낼 뿐이다.
 */
function KeyBoxes({ rows }) {
  const top = rows.slice(0, 3);
  if (top.length === 0) return null;
  return (
    <div className="kpi">
      {top.map((item) => (
        <div key={item.id} className="box">
          <div className="lab">{factName(item)}</div>
          <div className="big">{item.raw || formatValue(item.value, item.unit)}</div>
          <p className="sub">
            {[item.period, item.issuer, hostOf(item.sourceUrl)].filter(Boolean).join(' · ')}
          </p>
        </div>
      ))}
    </div>
  );
}

/**
 * 접힌 상자에 붙는 <b>미리보기 한 줄</b> — 그 절 첫 두 사실의 값이다.
 * <p>⚠ <b>여기서 요약하지 않는다.</b> 값을 그대로 옮길 뿐이고, 없으면 건수만 말한다 —
 * 닫힌 상자에 「좋아 보인다」류의 말이 붙으면 그것이 판정으로 읽힌다.
 */
function peekOf(rows) {
  const head = headFacts(rows).slice(0, 2)
    .map((item) => item.raw || formatValue(item.value, item.unit))
    .filter(Boolean);
  if (head.length > 0) return head.join(' · ');
  return rows.length > 0 ? `근거 ${rows.length}건` : '';
}

/** 근거가 많이 온 출처 도메인 여섯. 꼬리에 「어디서 온 글인가」를 한 줄로 남긴다. */
function topHosts(evidence) {
  const count = new Map();
  for (const item of evidence) {
    const host = hostOf(item.sourceUrl);
    if (!host) continue;
    count.set(host, (count.get(host) ?? 0) + 1);
  }
  return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6)
    .map(([host, n]) => `${host} (${n})`);
}

export default MarketReportView;
