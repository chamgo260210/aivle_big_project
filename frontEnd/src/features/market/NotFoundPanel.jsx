import { Alert, Badge, Card } from '../../shared/ui';
import { NOT_FOUND_GROUP } from './marketResult.js';

const ORDER = ['NOT_YET', 'ASSUMED', 'CONFIRMED_ABSENT', 'SCREENED_OUT', 'DIVERGED'];

/** 목록에 묻으면 안 되는 진단 — 방법론 판정이라 따로 세운다. */
const CALLOUT = 'independent_topdown_blocked';

/**
 * 「못 찾은 것」 — **한 덩이가 아니라 갈래별로** 보인다.
 *
 * 왜 가르는가: 사용자의 다음 행동이 전혀 다르기 때문이다.
 *   더 찾아라 / 가정을 세워라 / 그만 찾아라 / 값이 갈렸으니 골라라.
 * 한 목록에 섞으면 「11건 못 찾음」이라는 한 덩이 실패로만 읽힌다.
 */
export default function NotFoundPanel({ notFound, coverageCaveat }) {
  const callout = notFound.find((block) => block.key === CALLOUT);
  const blocks = notFound.filter((block) => block.key !== CALLOUT);
  const unknown = blocks.filter((block) => !block.group);

  return (
    <>
      {/* GMV≠매출 같은 창업자 함정 경고가 여기 들어 있다. 목록에 묻지 않는다. */}
      {callout ? (
        <Alert tone="warning" title={callout.label}>
          <ul className="market-notfound__callout">
            {callout.entries.map((line) => <li key={line}>{line}</li>)}
          </ul>
        </Alert>
      ) : null}

      <Card title="못 찾은 것">
        <p className="market-note">
          없는 것을 없다고 적는다. <strong>갈래마다 다음에 할 일이 다르다.</strong>
        </p>

        {blocks.length === 0 && !callout ? <p>보고된 공백이 없다.</p> : null}

        {ORDER.map((group) => {
          const rows = blocks.filter((block) => block.group === group);
          if (rows.length === 0) return null;
          const view = NOT_FOUND_GROUP[group];
          const total = rows.reduce((sum, row) => sum + row.count, 0);
          return (
            <section key={group} className="market-notfound__group">
              <h4>
                {view.label}
                <Badge tone={view.tone}>{total}건</Badge>
                <span className="market-notfound__note">{view.note}</span>
              </h4>
              {rows.map((row) => (
                <div key={row.key} className="market-notfound__block">
                  <strong>{row.label}</strong>
                  <ul>{row.entries.map((line) => <li key={line}>{line}</li>)}</ul>
                </div>
              ))}
            </section>
          );
        })}

        {/* 모르는 진단 키를 조용히 삼키지 않는다 — 다음 판에 잘못된 서랍에서 잠든다. */}
        {unknown.length > 0 ? (
          <Alert tone="danger" title="분류하지 못한 진단">
            {unknown.map((row) => (
              <div key={row.key}>
                <code>{row.key}</code>
                <ul>{row.entries.map((line) => <li key={line}>{line}</li>)}</ul>
              </div>
            ))}
          </Alert>
        ) : null}

        {coverageCaveat ? <Alert tone="warning">{coverageCaveat}</Alert> : null}
      </Card>
    </>
  );
}
