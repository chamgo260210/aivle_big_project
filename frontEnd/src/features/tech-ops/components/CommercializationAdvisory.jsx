const priorityClass = (value) => String(value || 'MEDIUM').toLowerCase();

const areaLabel = {
  MARKET_BM: '시장·BM 가설 검증',
  PRODUCT_TECH: '제품·기술 구현 전략',
  OPERATIONS: '운영모델·책임 구조',
  RISK_GATE: '출시 게이트·상용화 리스크',
  PARTNER_SUPPLY: '파트너·공급 구조',
  PILOT: '파일럿·검증 설계',
  SCALE: '확장 준비도',
};

const topicLabel = {
  DATA_AI: '데이터·AI 운영 준비도',
  CUSTOMER_TRUST: '고객 경험·신뢰 설계',
  OBSERVABILITY_SLA: '운영 관측성·SLA',
  SCALABILITY: '확장 준비도',
};

const costBehaviorLabel = {
  FIXED: '고정형',
  VARIABLE: '변동형',
  STEP: '단계형',
  UNKNOWN: '산정 방식 확인 필요',
};

function Basis({ ids = [] }) {
  if (!ids.length) return null;
  return <small className="commercialization-advisory__basis">근거: {ids.join(', ')}</small>;
}

function List({ items = [] }) {
  return items.length ? <ul>{items.map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}</ul> : null;
}

function brief(text, maxLength = 170) {
  const value = String(text || '').trim();
  const sentences = value.match(/[^.!?。]+[.!?。]?/g) ?? [value];
  const result = (sentences.slice(0, 2).join('').trim() || value);
  return result.length > maxLength ? `${result.slice(0, maxLength).trim()}…` : result;
}

export default function CommercializationAdvisory({ report }) {
  if (!report?.result) return null;
  const value = report.result;
  const facts = value.layer1Facts || [];
  const evidence = value.layer2Evidence || [];

  return <section className="commercialization-advisory" aria-live="polite">
    <header>
      <p>기술·운영 상용화 검증 · 상용화 판정 <span className={`commercialization-advisory__decision ${priorityClass(value.decision)}`}>{value.decision}</span></p>
      <h1>{value.productName || '상용화 검증 대상'}</h1>
      <strong>{value.summary}</strong>
      <small>Layer 1 사실 {facts.length}건 · Layer 2 외부 근거 {evidence.length}건 · Layer 3 상용화 조언 {value.advice?.length || 0}건</small>
    </header>

    <section>
      <h2>상용화 조언</h2>
      <div className="commercialization-advisory__grid">
        {value.advice?.map((item, index) => <article key={`${item.area}-${index}`} className={priorityClass(item.priority)}>
          <span>{item.priority}</span><h3>{areaLabel[item.area] ?? item.area}</h3>
          <p>{item.advice}</p><small><b>검증 방법</b> {item.validationMethod}</small><Basis ids={item.basisIds} />
        </article>)}
      </div>
    </section>

    {value.pilotPlan && <section className="commercialization-advisory__pilot">
      <h2>파일럿 실행 설계</h2>
      <article>
        <h3>{value.pilotPlan.objective}</h3>
        <div className="commercialization-advisory__pilot-grid">
          <div><b>범위</b><List items={value.pilotPlan.scope} /></div>
          <div><b>핵심 측정지표</b><List items={value.pilotPlan.metrics} /></div>
          <div><b>중단 조건</b><List items={value.pilotPlan.stopConditions} /></div>
          <div><b>확대 조건</b><List items={value.pilotPlan.scaleConditions} /></div>
        </div>
      </article>
    </section>}

    <section>
      <h2>파일럿에서 계측할 운영비용 구조</h2>
      <p className="commercialization-advisory__note">금액 예측이 아니라, 실제 견적·계약·청구서·파일럿 로그로 비용을 확정하기 위한 계측 설계입니다.</p>
      <div className="commercialization-advisory__grid commercialization-advisory__cost-grid">
        {value.operatingCosts?.map((item, index) => <article key={`${item.category}-${index}`}>
          <h3>{item.category}</h3><p><b>비용 유발 요인</b> {item.driver}</p><p><b>발생 조건</b> {item.trigger}</p>
          <p><b>측정 단위</b> {item.measurementUnit} · {costBehaviorLabel[item.behavior] ?? '산정 방식 확인 필요'}</p><small>{item.pilotMeasurement}</small><Basis ids={item.basisIds} />
        </article>)}
      </div>
    </section>

    <section>
      <h2>추가 상용화 준비도</h2>
      <div className="commercialization-advisory__grid commercialization-advisory__readiness-grid">
        {value.readiness?.map((item) => <article key={item.topic} className={priorityClass(item.priority)}>
          <span>{item.priority}</span><h3>{topicLabel[item.topic] ?? item.topic}</h3><p>{brief(item.assessment)}</p>
          <small><b>검증</b> {brief(item.validationMethod, 100)}</small>
          <details className="commercialization-advisory__details"><summary>주의사항·권장 통제 보기</summary><b>주의사항</b><List items={item.watchouts} /><b>권장 통제</b><List items={item.controls} /></details>
          <Basis ids={item.basisIds} />
        </article>)}
      </div>
    </section>

    <section>
      <h2>출시 전 게이트</h2>
      <div className="commercialization-advisory__gates">
        {value.gates?.map((item, index) => <article key={`${item.title}-${index}`}>
          <div className="commercialization-advisory__gate-head"><span>{item.status}</span><div><h3>{item.title}</h3><p><b>책임자</b> {item.owner}</p></div></div>
          <p className="commercialization-advisory__gate-criteria">{item.exitCriteria}</p><Basis ids={item.basisIds} />
        </article>)}
      </div>
    </section>

    {(facts.length > 0 || evidence.length > 0) && <section className="commercialization-advisory__evidence">
      <h2>분석 근거</h2>
      <div className="commercialization-advisory__evidence-grid">
        <article><h3>Layer 1 · 시장/BM 확인 사실</h3>
          <ul>{facts.slice(0, 12).map((fact) => <li key={fact.factId}><b>{fact.factId}</b> · {fact.path}: {fact.value}</li>)}</ul>
          {facts.length > 12 && <small>총 {facts.length}건 중 주요 12건 표시</small>}
        </article>
        <article><h3>Layer 2 · 상위 분석의 출처 링크</h3>
          {evidence.length ? <ul>{evidence.map((item) => <li key={item.evidenceId}><b>{item.evidenceId}</b> · <a href={item.url} target="_blank" rel="noreferrer">{item.title}</a></li>)}</ul> : <p>상위 시장/BM 결과에 URL 출처가 아직 포함되지 않았습니다.</p>}
        </article>
      </div>
    </section>}
  </section>;
}
