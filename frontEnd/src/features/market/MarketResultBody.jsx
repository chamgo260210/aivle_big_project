import { useState } from 'react';
import { Alert, Badge, Card } from '../../shared/ui';
import { GradeBadge, SourceLink } from './BmCanvas.jsx';
import AssumptionLedger from './AssumptionLedger.jsx';
import Emphasis from './emphasis.jsx';
import {
  NOT_FOUND_GROUP, SCORE_STATE_VIEW, SUBJECT_LABEL,
  abbreviateKrw, bucketEvidence, competitorGaps, formatValue, hostOf,
} from './marketResult.js';
import './market.css';

/**
 * 사업 검증의 <b>첫째 걸음</b> — 시장조사 결과.
 *
 * <p><b>성적표 7과목이 곧 목차다.</b> 성적표를 맨 아래 접어 두면 「무엇을 쟀나」와
 * 「무엇이 나왔나」가 따로 놀아, 읽는 사람이 빠진 과목을 못 본다.
 *
 * <p>7과목은 <b>카드 하나 안의 접히는 일곱 줄</b>이다(와이어프레임 정본). 줄은 번호·제목·
 * 상태·한 줄 요약·「근거 N건」이고, 펼치면 지금까지의 표·근거·출처가 그 자리에 나온다.
 * 과목마다 카드를 세우면 첫 화면이 7장으로 불어나 목차 구실을 못 한다.
 *
 * <p>셸(제목·실행 버튼·진행 표시)은 갖지 않는다. `BusinessValidationPage` 가 갖는다.
 */
export function MarketResultBody({ result, activeId, onJump }) {
  const market = result.market ?? {};
  const bag = bucketEvidence(result);
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

  // 본문은 «열렸을 때만» 만든다 — 7과목의 표를 늘 그려 두면 접힌 화면이 그만큼 무거워진다.
  const sections = [
    {
      subject: 'MARKET_SIZE',
      count: bag.size.length,
      body: () => (bag.size.length > 0
        ? <EvidenceTable rows={bag.size} />
        : <p className="bm-cell__none">모집단 관측이 없어요.</p>),
    },
    {
      subject: 'GROWTH',
      count: bag.grow.length,
      openable: Boolean(market.growth),
      body: () => <GrowthBody growth={market.growth} rows={bag.grow} />,
    },
    {
      subject: 'COMPETITOR',
      count: bag.comp.length,
      body: () => <CompetitorBody rows={bag.comp} gaps={competitorGaps(notFound)} />,
    },
    {
      subject: 'PRICE',
      count: priceCited.length,
      openable: Boolean(market.price),
      body: () => <PriceBody price={market.price} cited={priceCited} />,
    },
    {
      subject: 'DEMAND',
      count: bag.demand.length,
      body: () => <EvidenceTable rows={bag.demand} quote />,
    },
    {
      subject: 'CALCULATION',
      count: bag.calc.length,
      // 「이 숫자를 읽는 조건」(가정 원장)이 이 과목 안에 산다 — TAM/SAM 계산의 가정이라
      // 자리가 맞다. 계산 카드가 0장이어도 원장이 있으면 펼 것이 있다.
      openable: bag.calc.length > 0 || hasLedger(market),
      body: () => (
        <>
          <AssumptionLedger market={market} />
          <CalcBody cards={bag.calc} />
        </>
      ),
    },
    {
      // 7과목인데 6줄만 세우면 성적표의 마지막 줄이 화면에 없다 —
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
        <span className="mr-sub__d">{row?.detail ?? ''}</span>
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
            <td className="v num">{formatValue(item.value, item.unit)}</td>
            <td>
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
