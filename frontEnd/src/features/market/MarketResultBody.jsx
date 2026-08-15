import { useState } from 'react';
import { Alert, Badge, Card } from '../../shared/ui';
import { GradeBadge, SourceLink } from './BmCanvas.jsx';
import AssumptionLedger from './AssumptionLedger.jsx';
import Emphasis from './emphasis.jsx';
import {
  NOT_FOUND_GROUP, SCORE_STATE_VIEW, SHORTFALL_VIEW, SUBJECT_LABEL,
  abbreviateKrw, competitorGaps, formatValue, hostOf, sectionEvidence,
} from './marketResult.js';
import './market.css';

/**
 * 사업 검증의 <b>첫째 걸음</b> — 시장조사 결과.
 *
 * <p><b>성적표 과목이 곧 목차다.</b> 성적표를 맨 아래 접어 두면 「무엇을 쟀나」와
 * 「무엇이 나왔나」가 따로 놀아, 읽는 사람이 빠진 과목을 못 본다.
 *
 * <p>과목은 <b>카드 하나 안의 접히는 줄</b>이다(와이어프레임 정본). 줄은 번호·제목·
 * 상태·한 줄 요약·「근거 N건」이고, 펼치면 지금까지의 표·근거·출처가 그 자리에 나온다.
 * 과목마다 카드를 세우면 첫 화면이 열 장으로 불어나 목차 구실을 못 한다.
 *
 * <p>판 ㊸ 에서 <b>7 → 10 과목</b>이 됐다(채널·원가/수익성·규제). 그 아래에 사람 보고서의
 * 2·8·9절이 선다 — 가격 판단 · 처방 · 지지/흔듦.
 *
 * <p>셸(제목·실행 버튼·진행 표시)은 갖지 않는다. `BusinessValidationPage` 가 갖는다.
 */
export function MarketResultBody({ result, activeId, onJump }) {
  const market = result.market ?? {};
  // 절 배치는 **서버가 정한다**(판 ㊸). 옛 결과만 화면이 옛 셈으로 물러선다.
  const bag = sectionEvidence(result);
  const score = Object.fromEntries((result.scorecard ?? []).map((row) => [row.subject, row]));
  const cited = (ids) => ids.map((id) => result.evidenceById.get(id)).filter(Boolean);
  const priceCited = cited(market.price?.evidenceIds ?? []);
  const notFound = market.notFound ?? [];

  // 한 번에 한 과목만 편다. **KPI 착지가 그 과목을 «펼치면서» 내려앉아야** 하므로
  // 펼침 상태는 바깥의 `activeId` 와 묶인다 — 착지했는데 접혀 있으면 아무 일도 안 한 것처럼 보인다.
  // ⚠ effect 로 맞추지 않는다(렌더 → effect → 재렌더로 한 프레임 늦게 열린다).
  //    렌더 중 조정은 React 가 권하는 «prop 이 바뀔 때 state 조정» 패턴이다.
  const [open, setOpen] = useState(null);
  const [seenActive, setSeenActive] = useState(null);
  if (activeId !== seenActive) {
    setSeenActive(activeId);
    if (activeId) setOpen(activeId);
  }

  // 본문은 «열렸을 때만» 만든다 — 열 과목의 표를 늘 그려 두면 접힌 화면이 그만큼 무거워진다.
  const sections = [
    {
      subject: 'MARKET_SIZE',
      count: bag.MARKET_SIZE.length,
      body: () => (bag.MARKET_SIZE.length > 0
        ? <EvidenceTable rows={bag.MARKET_SIZE} />
        : <p className="bm-cell__none">모집단 관측이 없어요.</p>),
    },
    {
      subject: 'GROWTH',
      count: bag.GROWTH.length,
      openable: Boolean(market.growth),
      body: () => <GrowthBody growth={market.growth} rows={bag.GROWTH} />,
    },
    {
      subject: 'COMPETITOR',
      count: bag.COMPETITOR.length,
      // ⚠ **승격 카드를 `CompetitorBody` 에 넣지 않는다.** 그 부품은 `subject` 를 **회사
      //    이름**으로 보고 카드를 세우는데, 절 사실의 `subject` 는 「2025년 당기 매출액
      //    (오뚜기제유)」 같은 **계량 서술**이다 — 그대로 넣으면 41장짜리 **가짜 회사 목록**이
      //    선다. 승격분은 표로 그리고 발행사로 묶는다.
      body: () => (
        <>
          <CompetitorBody rows={bag.COMPETITOR.filter((r) => !승격(r))}
            gaps={competitorGaps(notFound)} />
          <IssuerTables rows={bag.COMPETITOR.filter(승격)} />
        </>
      ),
    },
    {
      subject: 'PRICE',
      // ⚠ 승격된 가격 사실을 안 그리면 **2절 판단의 근거를 검산할 자리가 없다** —
      //    「6,513원의 1.37배」의 그 6,513원이 바로 이 목록 안에 있다.
      count: priceCited.length + bag.PRICE.filter(승격).length,
      openable: Boolean(market.price) || bag.PRICE.some(승격),
      body: () => (
        <>
          <PriceBody price={market.price} cited={priceCited} />
          <TableAwareBody rows={bag.PRICE.filter(승격)} empty="" />
        </>
      ),
    },
    {
      subject: 'DEMAND',
      count: bag.DEMAND.length,
      body: () => <EvidenceTable rows={bag.DEMAND} quote />,
    },
    {
      subject: 'CALCULATION',
      count: bag.CALCULATION.length,
      // 「이 숫자를 읽는 조건」(가정 원장)이 이 과목 안에 산다 — TAM/SAM 계산의 가정이라
      // 자리가 맞다. 계산 카드가 0장이어도 원장이 있으면 펼 것이 있다.
      openable: bag.CALCULATION.length > 0 || hasLedger(market),
      body: () => (
        <>
          <AssumptionLedger market={market} />
          <CalcBody cards={bag.CALCULATION} />
        </>
      ),
    },
    // ── 판 ㊸ — 절 체인이 채우는 세 과목 ────────────────────────
    // ⚠ 표를 **찢지 않는다.** 구성비 표는 합이 100인데 절반만 보이면 1위가 뒤바뀐다 —
    //    실측으로 채널 절 합이 47%였고 숨은 특약점 29.65%가 1위 대형마트 31.05%와 대등했다.
    {
      subject: 'CHANNEL',
      count: bag.CHANNEL.length,
      body: () => <TableAwareBody rows={bag.CHANNEL} empty="채널별 비중을 못 구했어요." />,
    },
    {
      subject: 'UNIT_ECONOMICS',
      count: bag.UNIT_ECONOMICS.length,
      body: () => <TableAwareBody rows={bag.UNIT_ECONOMICS} empty="원가·수익성 사실을 못 구했어요." />,
    },
    {
      subject: 'REGULATION',
      count: bag.REGULATION.length,
      body: () => <TableAwareBody rows={bag.REGULATION} empty="지켜야 할 기준치를 못 구했어요." />,
    },
    {
      // 10과목인데 9줄만 세우면 성적표의 마지막 줄이 화면에 없다 —
      // 「찾지 못한 것」은 이 조사에서 **항상 나가는 칸**이라 더더욱 그렇다.
      subject: 'NOT_FOUND',
      count: notFound.reduce((sum, block) => sum + block.count, 0),
      body: () => <NotFoundBody blocks={notFound} />,
    },
  ];

  return (
    <>
      <Kpis market={market} onJump={onJump} />

      {/* 「이 숫자의 기준」 — 결론 숫자 바로 밑에 한 줄로 선다.
          ⚠ 과목 표 사이에 묻어 두면 KPI 를 읽은 사람에게 닿지 않는다. 경계 문장이다. */}
      {market.coverageCaveat ? (
        <Card className="mr-basis">
          <p><b>이 숫자의 기준</b> — <Emphasis text={market.coverageCaveat} /></p>
        </Card>
      ) : null}

      {/* ⚠ **「덜 조사됐다」는 경계 문장이다.** 접이식 안에 넣지 않는다
          (`AssumptionLedger.test.jsx:80-84` — 「경계 문장을 접지 않는다」). */}
      <Shortfalls degradations={result.degradations} />

      {/* 2절 — 값을 보여 주는 것과 **「그래서 어디에 서 있나」를 말해 주는 것**은 다른 일이다.
          과목 표 위에 세운다: 사업가가 표를 다 읽고 나서야 판단을 만나면 늦다. */}
      <JudgmentCard judgment={result.judgment} />

      <Card className="mr-subs">
        {sections.map((section, index) => {
          const openable = section.openable ?? section.count > 0;
          const isOpen = openable && open === section.subject;
          return (
            <Subject
              key={section.subject}
              n={index + 1}
              subject={section.subject}
              row={score[section.subject]}
              count={section.count}
              openable={openable}
              open={isOpen}
              focused={activeId === section.subject}
              onToggle={() => setOpen((current) => (current === section.subject ? null : section.subject))}
            >
              {isOpen ? section.body() : null}
            </Subject>
          );
        })}
      </Card>

      {/* 8절 — 「못 구했다」로 끝내면 사업가는 거기서 멈춘다. **어디서 구하는지**까지 적는다. */}
      <PrescriptionCard rows={result.prescriptions} />

      {/* 9절 — 사업가가 돈을 내는 자리. 사실이 이 사업안을 **미는지 흔드는지**를 말한다. */}
      <SynthesisCard rows={result.synthesis} />

      {/* 요약은 예산이 모자라면 오지 않는다. 왔을 때만 그린다 — 건너뛴 사유는 실행 기록에 있다. */}
      {result.summary?.length ? (
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
      ) : null}
    </>
  );
}

/**
 * <b>이 조사가 덜 된 자리.</b> 봉투 `degradations` 를 <b>처음으로 화면에 세운다.</b>
 *
 * <p>⚠ 이것이 없으면 예산이 끊겨 절이 빈 것과 <b>정말 자료가 없는 것</b>이 화면에서
 * 같아 보인다. 사업가는 「없다」로 읽고 그 사업안을 접을 수도 있다.
 */
function Shortfalls({ degradations }) {
  const 줄 = (degradations ?? [])
    .filter((item) => SHORTFALL_VIEW[item?.code])
    // 같은 코드가 여러 걸음에서 나면 한 번만 말한다.
    .filter((item, i, all) => all.findIndex((x) => x.code === item.code) === i);
  if (줄.length === 0) return null;
  return (
    <Alert tone="warning">
      <strong>이 조사가 다 돌지 못했어요</strong>
      <ul>
        {줄.map((item) => (
          <li key={item.code}>
            <Emphasis text={SHORTFALL_VIEW[item.code]} />
            {item.detail ? <div className="mr-shortfall__d">{item.detail}</div> : null}
          </li>
        ))}
      </ul>
    </Alert>
  );
}

/**
 * 2절 — <b>가격 판단.</b> 기계가 계산한 문장이고 모델이 쓴 것이 아니다.
 *
 * <p>⚠ <b>결론을 빼지 마라.</b> 계산식만 남으면 「1.37배」에서 끝나고 사업가가 사는 것인
 * 「그래서 어느 쪽으로 팔라」가 사라진다. 비교쌍이 안 갖춰져 <b>못 쓴 갈래</b>도 같이
 * 세운다 — 침묵을 「해당 없음」으로 읽히게 두지 않는다.
 */
export function JudgmentCard({ judgment }) {
  if (!judgment) return null;
  return (
    <Card title="이 가격이 시장 어디에 서 있나" className="mr-judgment">
      {judgment.conclusion ? (
        <Alert tone="info">
          <Emphasis text={judgment.conclusion} />
          {/* ⚠ **결론 자리에 연도가 없으면 오늘 값처럼 읽힌다.** 「배달과 8% 근소」의
              자장면값이 2018년인데, 그 사실이 작은 근거 줄에만 있으면 사업가는 못 본다.
              새 판정이 아니라 **이미 있는 값을 결론 옆으로 옮기는 것**이다. */}
          <ConclusionYears lines={judgment.lines} />
        </Alert>
      ) : (
        <p className="bm-cell__none">
          비교쌍이 갖춰지지 않아 결론을 쓰지 않았어요. <b>지어내지 않습니다.</b>
        </p>
      )}
      <ul className="mr-judgment__lines">
        {judgment.lines.map((line) => (
          <li key={line.what}>
            <b>{line.what}</b>
            {line.sentence ? (
              <>
                <p><Emphasis text={line.sentence} /></p>
                {line.formula ? <p className="mr-judgment__calc num">계산: {line.formula}</p> : null}
              </>
            ) : (
              <p className="bm-cell__none">
                (안 씁니다) <Emphasis text={line.silentBecause ?? ''} />
              </p>
            )}
            {line.sources.map((s) => (
              <p key={`${s.raw}-${s.subject}`} className="mr-judgment__src">
                <span className="num">{s.raw}</span> «{s.subject}
                {s.period ? ` · ${s.period}` : ' · 연도 없음'}»
                {s.url ? <> <a href={s.url} target="_blank" rel="noreferrer">출처</a></> : null}
              </p>
            ))}
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** 8절 — <b>처방.</b> 셋째 열(「어디서」)이 이 표의 값어치다. */
export function PrescriptionCard({ rows }) {
  if (!rows || rows.length === 0) return null;
  return (
    <Card title="못 구한 것 — 어디서 구하나" className="mr-rx">
      <table className="mr-table">
        <thead>
          <tr><th>과목</th><th>무엇을 못 구했나</th><th>왜 필요한가</th><th>어디서 구하나</th></tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={`${row.section}-${row.what}`}>
              <td>
                <b>{SUBJECT_LABEL[row.section] ?? row.section}</b>
                <div className="mr-rx__kind">{row.kindLabel}</div>
              </td>
              <td><Emphasis text={row.what} /></td>
              {/* ⚠ 셋 중 이 칸만 `Emphasis` 를 안 거쳐 **별표가 글자로 찍혔다**(화면 실측
                  2026-08-15): 「**어디를 볼지 적는다**」. 3층 테스트는 문자열을 그대로
                  비교하므로 이 부류를 **구조적으로 못 잡는다** — 눈으로만 잡힌다. */}
              <td><Emphasis text={row.why} /></td>
              <td><Emphasis text={row.where} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

/**
 * 9절 — <b>지지 / 흔듦.</b> 갈래와 근거는 기계가 정하고 모델은 문장만 쓴다.
 * 검사에서 버려진 문장은 서버에서 이미 빠진다 — <b>버린 것을 여기 올리면
 * 「검사를 했다」가 「검사를 통과했다」로 읽힌다.</b>
 */
export function SynthesisCard({ rows }) {
  if (!rows || rows.length === 0) return null;
  const 지지 = rows.filter((row) => row.stance === '지지');
  const 흔듦 = rows.filter((row) => row.stance !== '지지');
  // ⚠ **빈 갈래를 지우지 않는다.** 지우면 「흔드는 사실이 0건이었다」와 「흔듦을 아예
  //    안 쟀다」가 화면에서 같아 보인다 — 성적표 수요 줄에서 방금 고친 것과 **같은 병**이고,
  //    9절은 사업가가 돈을 내는 자리라 더 나쁘다. 실측(2026-08-15): 이 실행의 9절은
  //    「미는 것 3 · 흔드는 것 0」인데 화면에는 미는 것만 서서 **한쪽 말만 들렸다.**
  const 빈무리 = (title, tone) => (
    <div className={`mr-synth__g mr-synth__g--${tone}`}>
      <h4>{title} <span className="num">0</span></h4>
      <p className="mr-synth__none">
        이번 조사에서 <b>{title}</b>에 해당하는 사실은 <b>한 건도 없었어요.</b>
        {' '}못 찾은 것이지 없다는 뜻은 아니에요 — 위 과목별 「미확보」를 같이 보세요.
      </p>
    </div>
  );
  const 무리 = (title, mine, tone) => (mine.length === 0 ? 빈무리(title, tone) : (
    <div className={`mr-synth__g mr-synth__g--${tone}`}>
      <h4>{title} <span className="num">{mine.length}</span></h4>
      <ul>
        {mine.map((row) => (
          <li key={row.key}>
            <Emphasis text={row.sentence} />
            {/* ⚠ **연도를 2절에만 찍고 9절에 안 찍으면** 같은 화면의 두 블록이
                같은 수를 다른 정직도로 말한다. 둘은 같은 뿌리를 공유한다. */}
            <p className="mr-synth__src">
              {row.sources.map((s) => (
                `${s.raw} «${s.subject}${s.period ? ` · ${s.period}` : ' · 연도 없음'}»`
              )).join(' · ')}
            </p>
          </li>
        ))}
      </ul>
    </div>
  ));
  return (
    <Card title="이 사업안을 미는 것과 흔드는 것" className="mr-synth">
      {무리('미는 것', 지지, 'ok')}
      {무리('흔드는 것', 흔듦, 'warn')}
    </Card>
  );
}

/**
 * 절 사실 표 — <b>같은 표의 행을 붙여 세우고 합계를 말한다.</b>
 *
 * <p>⚠ 이것이 `tableKey` 가 봉투에 실린 이유다. 구성비 표는 <b>합이 100인데 절반만 보이면
 * 1위가 뒤바뀐다</b>. 실측: 채널 절 합이 47%였고 숨은 특약점 29.65%가 1위 대형마트
 * 31.05%와 대등했다. <b>빈칸은 「못 구했다」고 말하지만 반쪽 표는 아무 말도 안 한다.</b>
 */
function TableAwareBody({ rows, empty }) {
  // `empty` 가 비었으면 **아무것도 안 그린다** — 「없음」 문구를 두 번 세우지 않는다.
  if (!rows || rows.length === 0) return empty ? <p className="bm-cell__none">{empty}</p> : null;
  const 표 = new Map();
  const 낱개 = [];
  for (const row of rows) {
    if (!row.tableKey) { 낱개.push(row); continue; }
    if (!표.has(row.tableKey)) 표.set(row.tableKey, []);
    표.get(row.tableKey).push(row);
  }
  return (
    <>
      {[...표.entries()].map(([key, group]) => {
        // **묶음 전체에 공통인 경계는 머리에 한 번만 세운다.** 22행짜리 표에서
        // 「이 값은 「매출처별 판매비중」 표의 한 행이다」가 22번 반복되면, 경고가 흔해져
        // 「상한으로만」 같은 **진짜 경고가 안 읽힌다.**
        // ⚠ 지우는 것이 아니라 **한 번만 그리는** 것이다 — 문장은 그대로 다 나온다.
        const 공통 = group[0].caveats.filter((line) => group.every((r) => r.caveats.includes(line)));
        const 남 = group.map((r) => ({ ...r, caveats: r.caveats.filter((l) => !공통.includes(l)) }));
        return (
          <div key={key} className="mr-tablegroup">
            {공통.map((line) => (
              <p key={line} className="mr-caveat"><Emphasis text={line} /></p>
            ))}
            <EvidenceTable rows={남} quote />
            <PercentSum rows={group} />
          </div>
        );
      })}
      {낱개.length > 0 ? <EvidenceTable rows={낱개} quote /> : null}
    </>
  );
}

/**
 * 결론이 선 <b>근거들의 연도</b>. 셈이 아니라 <b>옮기기</b>다 — 판정을 새로 하지 않는다.
 *
 * <p>왜 결론 옆인가: 「배달과 8% 차이로 근소하다」의 배달값이 2018년 자장면이다.
 * 지금 배달 한 끼가 12,000원대면 그 결론은 <b>「배달보다 확실히 싸다」로 뒤집힌다.</b>
 * 연도가 작은 근거 줄에만 있으면 사업가는 결론만 읽고 지나간다.
 */
function ConclusionYears({ lines }) {
  const years = [...new Set(lines.flatMap((line) => line.sources.map((s) => s.period)))];
  if (years.length === 0) return null;
  const 있 = years.filter(Boolean).sort();
  const 없 = years.some((y) => !y);
  const 문장 = `이 판단이 선 근거의 연도 — ${있.join(' · ') || '없음'}`
    + (없 ? ' · ⚠ **연도를 모르는 근거가 섞여 있다**' : '');
  return <p className="mr-judgment__years"><Emphasis text={문장} /></p>;
}

/** 승격 카드인가. **`placement` 는 절 체인만 붙인다** — 슬롯 카드엔 없다. */
const 승격 = (row) => Boolean(row.placement);

/**
 * 승격된 경쟁사 사실을 <b>발행사로 묶어</b> 그린다.
 *
 * <p>⚠ 묶지 않으면 「이 시장에 경쟁사가 41곳」처럼 읽힌다 — 실제로는 한 회사의 공시
 * 한 건에서 나온 계열사 매출 여러 줄이다. <b>회사 수와 사실 수는 다른 수다.</b>
 */
function IssuerTables({ rows }) {
  if (!rows || rows.length === 0) return null;
  const 묶음 = new Map();
  for (const row of rows) {
    const key = row.issuer ?? '(발행사 미상)';
    if (!묶음.has(key)) 묶음.set(key, []);
    묶음.get(key).push(row);
  }
  return (
    <>
      {[...묶음.entries()].map(([issuer, group]) => (
        <div key={issuer} className="mr-issuergroup">
          <h4>{issuer} <span className="num">{group.length}</span>건</h4>
          <TableAwareBody rows={group} empty="" />
        </div>
      ))}
    </>
  );
}

/**
 * 구성비 표의 합. <b>100%가 아니면 그렇다고 말한다</b> — 침묵하면 반쪽 표가 전체로 읽힌다.
 * ⚠ 백분율 행이 아니면 아무 말도 안 한다. 「배달 비용을 둘러싼 갈등 116.1%」처럼
 *   합이 뜻 없는 표에 합계를 찍으면 그것이 새 거짓말이 된다(판 ㊷ 실측).
 */
function PercentSum({ rows }) {
  const pct = rows.filter((row) => row.unit === '%' && typeof row.value === 'number');
  if (pct.length < 3) return null;
  const sum = pct.reduce((total, row) => total + row.value, 0);
  const 온전 = sum >= 99 && sum <= 101;
  const 문장 = 온전
    ? `이 표의 ${pct.length}행 합계 ${sum.toFixed(1)}% — 표가 온전하다.`
    : `⚠ 이 표의 ${pct.length}행 합계는 ${sum.toFixed(1)}% 로 **100%가 아니다.** `
      + '보이지 않는 행이 있고, 그것이 1위일 수도 있다.';
  return (
    <p className={온전 ? 'mr-caveat mr-caveat--ok' : 'mr-caveat'}>
      <Emphasis text={문장} />
    </p>
  );
}

/** 가정 원장이 그릴 것이 있는가. `AssumptionLedger` 의 판단과 같은 조건이다. */
function hasLedger(market) {
  const figures = [market.tam, market.sam, market.growth];
  if (figures.some((figure) => figure && (figure.factors.length > 0 || figure.assumptions.length > 0))) return true;
  return Boolean(market.price?.baseNote) || !market.som;
}

/** 결론 숫자를 등급과 **동시에** 준다. 숫자만 먼저 보이면 확정으로 읽힌다. */
function Kpis({ market, onJump }) {
  const price = market.price;
  const tile = (label, figure, to, sub) => {
    if (!figure) return null;
    const short = figure.unit === 'KRW' ? abbreviateKrw(figure.value) : null;
    const full = formatValue(figure.value, figure.unit);
    return (
      <button key={label} type="button" className="mr-kpi" onClick={() => onJump(to)}>
        <span>{label}</span>
        <b className="num">{short ?? full}</b>
        <small className="num">{sub ?? (short ? full : '')}</small>
        <GradeBadge grade={figure.grade} />
      </button>
    );
  };

  return (
    <div className="mr-kpis">
      {tile('전체 시장 (TAM)', market.tam, 'MARKET_SIZE')}
      {tile('노릴 수 있는 시장 (SAM)', market.sam, 'MARKET_SIZE')}
      {tile('연 성장률', market.growth, 'GROWTH')}
      {price ? (
        <button type="button" className="mr-kpi" onClick={() => onJump('PRICE')}>
          <span>시장 가격대</span>
          <b className="num">{abbreviateKrw(price.min) ?? formatValue(price.min, price.currency)}
            {'~'}{abbreviateKrw(price.max) ?? formatValue(price.max, price.currency)}</b>
          <small className="num">
            {formatValue(price.min, price.currency)} ~ {formatValue(price.max, price.currency)}
          </small>
          <GradeBadge grade={price.grade} />
        </button>
      ) : null}
    </div>
  );
}

/**
 * 찾지 못한 것 — **갈래로 묶는다.** 「없다」도 결과이고, 갈래마다 다음 행동이 다르다.
 * 더 찾으면 나올 것과 찾아도 없는 것을 한 무더기로 두면 둘 다 못 읽는다.
 */
function NotFoundBody({ blocks }) {
  if (!blocks || blocks.length === 0) {
    return <p className="bm-cell__none">찾지 못한 것이 기록되지 않았어요.</p>;
  }
  // 갈래 순서는 `NOT_FOUND_GROUP` 선언 순서다 — 모르는 키(group=null)는 맨 뒤에 드러낸다.
  const groups = [...Object.keys(NOT_FOUND_GROUP), null];

  return (
    <div className="mr-nf">
      {groups.map((group) => {
        const mine = blocks.filter((block) => block.group === group && block.count > 0);
        if (mine.length === 0) return null;
        const view = NOT_FOUND_GROUP[group];
        return (
          <div key={group ?? '(모르는 갈래)'} className="mr-nf__g">
            <div className="mr-nf__h">
              <Badge tone={view?.tone ?? 'danger'}>{view?.label ?? '분류하지 못한 항목'}</Badge>
              <span>{view?.note ?? '이 키를 화면이 몰라요 — 조용히 묻지 않고 드러내요'}</span>
            </div>
            {mine.map((block) => (
              <div key={block.key} className="mr-nf__b">
                <h4>{block.label}<small className="num">{block.count}건</small></h4>
                <ul>{block.entries.map((line) => <li key={line}>{line}</li>)}</ul>
              </div>
            ))}
          </div>
        );
      })}
    </div>
  );
}

/**
 * 성적표 한 과목 — <b>눌러서 근거를 편다.</b>
 *
 * <p>줄 하나가 「번호 · 제목 · 상태 · 한 줄 요약 · 근거 N건」이다. 접힌 채로도 7과목의
 * 상태가 한눈에 서고, 편 사람만 값·기간·등급·출처를 본다.
 *
 * <p>⚠ <b>근거가 0건이면 못 편다.</b> 열리는 척하고 빈 칸을 보여 주면 「조사가 부실한가」와
 * 「화면이 고장인가」가 구분되지 않는다 — 「보기」 자체를 감추고 `disabled` 로 말한다.
 */
function Subject({ n, subject, row, count, openable, open, focused, onToggle, children }) {
  const view = row ? (SCORE_STATE_VIEW[row.state] ?? { label: row.state, tone: 'neutral' }) : null;
  // ⚠ **옛 결과에는 이 과목 자체가 없다**(성적표가 7과목이던 시절). 그대로 두면 제목만
  //    있고 배지도 설명도 없는 줄이 셋 서서, 사업가는 「비었다」로 읽는다. 실제로는
  //    **그때는 재지도 않은 과목**이다 — 「0건」과 「안 쟀다」를 가르는 것이 이 줄의 일이다.
  const detail = row?.detail ?? (row ? '' : '이 조사에는 없던 과목이에요 — 다시 조사하면 채워져요');
  return (
    <div id={`sec-${subject}`} className={`mr-sub${focused ? ' is-on' : ''}`}>
      <button
        type="button"
        className="mr-sub__h"
        onClick={onToggle}
        disabled={!openable}
        aria-expanded={openable ? open : undefined}
      >
        <span className="mr-sub__n num">{n}</span>
        <b>{SUBJECT_LABEL[subject] ?? subject}</b>
        {view ? <Badge tone={view.tone}>{view.label}</Badge> : null}
        <span className="mr-sub__d"><Emphasis text={detail} /></span>
        {openable ? (
          <span className="mr-sub__c">{open ? '접기' : `근거 ${count}건 ▾`}</span>
        ) : null}
      </button>
      {open ? <div className="mr-sub__b">{children}</div> : null}
    </div>
  );
}

function EvidenceTable({ rows, quote = false }) {
  return (
    <table className="mr-table">
      <thead>
        <tr><th>값</th><th>항목</th><th>기간</th><th>등급</th><th>출처</th></tr>
      </thead>
      <tbody>
        {rows.map((item) => (
          <tr key={item.id}>
            {/* 원문 표기가 있으면 같이 준다 — 「3,674,500,000,000원」만으로는
                원문의 「36,745억원」을 되짚을 수 없다. */}
            <td className="v num">
              {formatValue(item.value, item.unit)}
              {item.raw ? <small className="mr-raw">{item.raw}</small> : null}
            </td>
            <td>
              {/* 발행사 — **두 회사의 표가 하나로 읽히는 것을 막는다.** */}
              {item.issuer ? <b className="mr-issuer">경쟁사({item.issuer})</b> : null}
              {item.subject} · {item.metric}
              {quote && item.quote ? <div className="mr-quote">“{item.quote}”</div> : null}
              {/* 경계는 값과 한 몸이다. 접지 않는다. */}
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
  );
}

function GrowthBody({ growth, rows }) {
  if (!growth) return <p className="bm-cell__none">성장률을 산출하지 않았어요.</p>;
  return (
    <>
      <div className="mr-figs">
        <div>
          <span>연 성장률</span>
          <b className="num">{formatValue(growth.value, growth.unit)}</b>
          <small>{growth.formula ?? ''}</small>
        </div>
      </div>
      {rows.length > 0 ? <EvidenceTable rows={rows} /> : null}
      {/* 한 줄로 이어 붙이지 않는다 — 두 문장은 서로 다른 것을 말한다.
          자세한 항별 판정은 「이 숫자를 읽는 조건」의 가정 원장에 있다. */}
      {growth.assumptions.length > 0 ? (
        <div className="mr-note">
          {growth.assumptions.map((line) => <div key={line}><Emphasis text={line} /></div>)}
        </div>
      ) : null}
    </>
  );
}

/**
 * 경쟁사 — 회사별 카드. **못 찾은 슬롯도 같은 카드에 세운다.**
 * 관측된 지표만 그리면 「이 회사는 이게 전부다」로 읽힌다.
 */
function CompetitorBody({ rows, gaps }) {
  const names = [...new Set([...rows.map((item) => item.subject), ...gaps.map(([name]) => name)])];
  if (names.length === 0) return <p className="bm-cell__none">경쟁사 관측이 없어요.</p>;

  return (
    <>
      <div className="mr-comps">
        {names.map((name) => {
          const mine = rows.filter((item) => item.subject === name);
          const missing = gaps
            .filter(([subject]) => subject === name)
            .map(([, metric]) => metric)
            .filter((metric) => !mine.some((item) => item.metric === metric));
          return (
            <div key={name} className="mr-comp">
              <h4>{name}</h4>
              {mine.map((item) => (
                <div key={item.id}>
                  <span>{item.metric}</span>
                  <b className="num">{formatValue(item.value, item.unit)}</b>
                </div>
              ))}
              {missing.map((metric) => (
                <div key={metric}><span>{metric}</span><span className="none">찾지 못함</span></div>
              ))}
              {mine.length > 0 ? (
                <div className="mr-comp__src"><SourceLink item={mine[0]} /></div>
              ) : null}
              {mine.flatMap((item) => item.caveats).map((line) => (
                <div key={line} className="mr-caveat"><Emphasis text={line} /></div>
              ))}
            </div>
          );
        })}
      </div>
      <div className="mr-note">
        수집 대상은 가입 매장 수·매출액·요금 같은 숫자예요.{' '}
        <strong>기능·차별점 비교는 조사 항목에 없어요.</strong>
      </div>
    </>
  );
}

/** 가격은 밴드로 읽어야 한다. 대표값 하나만 남으면 확정 단가로 읽힌다. */
function PriceBody({ price, cited }) {
  if (!price) return <p className="bm-cell__none">표시가격 관측이 없어요.</p>;
  const hosts = new Set(cited.map((item) => hostOf(item.sourceUrl)).filter(Boolean));

  return (
    <>
      <div className="mr-figs">
        <div><span>최저</span><b className="num">{formatValue(price.min, price.currency)}</b></div>
        <div><span>대표값 (잠정)</span><b className="num">{formatValue(price.base, price.currency)}</b></div>
        <div><span>최고</span><b className="num">{formatValue(price.max, price.currency)}</b></div>
      </div>
      {cited.length > 0 ? <EvidenceTable rows={cited} /> : null}
      {/* 건수와 독립성은 다르다. 한 도메인에서 3건은 3중 확인이 아니다. */}
      {hosts.size === 1 && cited.length > 1 ? (
        <Alert tone="warning">
          {cited.length}건이지만 출처 도메인은 <strong>{[...hosts][0]} 하나</strong>예요
          {' — '}{cited.length}중 확인이 아니라 <strong>1중 확인</strong>이에요.
        </Alert>
      ) : null}
      {price.caveats.map((line) => (
        <div key={line} className="mr-caveat"><Emphasis text={line} /></div>
      ))}
    </>
  );
}

/**
 * 계산 카드 — 입력과 **그 계산이 쓴 재료 카드**를 같이 그린다.
 *
 * ⚠ 예전에는 `index < materialIds.length` 로 입력 줄마다 「뒷받침 근거 없음」 배지를
 * 달았다. 그것은 **입력 순서와 재료 순서가 같다고 가정**한 것인데 그런 보장은 없고,
 * 실제로 엉뚱한 줄에 배지가 붙었다. 대응 관계가 데이터에 없으면 **없다고 그린다** —
 * 틀린 배지는 없는 배지보다 나쁘다. 항별 관측/가정 판정은 「이 숫자를 읽는 조건」의
 * 가정 원장이 한다(그쪽은 서버가 항마다 판정을 실어 보낸다).
 */
function CalcBody({ cards }) {
  if (cards.length === 0) return <p className="bm-cell__none">계산 카드가 없어요.</p>;
  return (
    <>
      {cards.map((card) => {
        const inputs = Object.entries(card.inputs ?? {});
        return (
          <div key={card.id}>
            <div className="mr-figs">
              <div>
                <span>{card.metric}</span>
                <b className="num">{formatValue(card.value, card.unit)}</b>
                <small>{card.formula ?? ''}</small>
              </div>
            </div>
            <table className="mr-table">
              <tbody>
                {inputs.map(([name, value]) => (
                  <tr key={name}>
                    <td className="v num">
                      {typeof value === 'number' ? value.toLocaleString('ko-KR') : String(value)}
                    </td>
                    <td>{name}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="mr-note">
              <b>쓴 재료</b>{' '}
              {card.materialIds.length > 0
                ? card.materialIds.map((id) => (
                  <Badge key={id} tone="success">{id}</Badge>
                ))
                : <Badge tone="warning">관측 재료 없음 — 전부 가정으로 채운 계산이에요</Badge>}
              {card.assumptions.map((line) => (
                <div key={line}><Emphasis text={line} /></div>
              ))}
            </div>
          </div>
        );
      })}
    </>
  );
}
