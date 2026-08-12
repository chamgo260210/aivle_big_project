import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, TextInput } from '../../shared/ui';

/**
 * 경쟁 씨앗 — <b>「우리와 겹치는 것」을 사용자가 적는 칸.</b>
 *
 * <p>왜 묻는가: 슬롯 하네스가 경쟁 슬롯의 조사 대상을 여기서 가져간다. 비워 두면 모델이
 * 경쟁사 <b>실명을 지어내거나</b> 자리표시자를 만들고, 그것이 공시 대조에서 전부 죽는다
 * (2026-08-08 실측). 사용자가 아는 이름 두엇이 관측의 출발점이 된다.
 *
 * <p>⚠ <b>비워도 막지 않는다.</b> 경고만 보여 준다 — 경쟁을 모르는 것도 사업 초기의
 * 정상 상태이고, 그때는 조사가 업종 카테고리로 대신 선다.
 *
 * <p>⚠ 「씨앗이지 진실이 아니다」를 화면에서 지운다면 사용자는 이 목록을 <b>조사 결과</b>로
 * 읽는다. 엔진이 발굴한 경쟁과 병합해 같은 잣대로 검증한다는 사실이 여기 서 있어야 한다.
 */
const EMPTY_ROW = { name: '', reason: '', operatorName: '' };
const MAX_ROWS = 8;

export default function CompetitorSeedForm({ api, disabled }) {
  const [rows, setRows] = useState([EMPTY_ROW]);
  const [warning, setWarning] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);

  const apply = useCallback((view) => {
    const next = (view?.seeds ?? []).map((seed) => ({
      name: seed.name ?? '',
      reason: seed.reason ?? '',
      operatorName: seed.operatorName ?? '',
    }));
    setRows(next.length ? next : [EMPTY_ROW]);
    setWarning(view?.warning ?? null);
  }, []);

  useEffect(() => {
    let alive = true;
    api.currentCompetitorSeeds()
      .then((view) => { if (alive) apply(view); })
      .catch(() => { /* 아직 적은 적이 없으면 빈 칸으로 시작한다 */ });
    return () => { alive = false; };
  }, [api, apply]);

  const set = (index, key) => (event) => {
    const value = event.target.value;
    setSaved(false);
    setRows((current) => current.map((row, i) => (i === index ? { ...row, [key]: value } : row)));
  };

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      // 빈 줄은 보내도 서버가 떨어뜨린다 — 「비었다」의 정의를 화면이 또 갖지 않는다.
      apply(await api.saveCompetitorSeeds(rows));
      setSaved(true);
    } catch (failure) {
      setError(failure?.message ?? '경쟁 씨앗을 저장하지 못했다.');
    } finally {
      setBusy(false);
    }
  };

  const locked = disabled || busy;

  return (
    <form className="competitor-seeds" onSubmit={submit}>
      <p className="competitor-seeds__why">
        이미 이 문제를 풀고 있는 서비스를 아는 만큼 적어라. 조사가 <strong>경쟁을 찾는
        출발점</strong>으로 쓴다. <strong>씨앗이지 조사 결과가 아니다</strong> — 조사가 스스로
        찾은 것과 합쳐 같은 잣대로 검증한다.
      </p>

      {rows.map((row, index) => (
        <div key={index} className="competitor-seeds__row">
          <TextInput
            label="이름"
            value={row.name}
            onChange={set(index, 'name')}
            disabled={locked}
            placeholder="예: 공비서"
          />
          <TextInput
            label="왜 경쟁인가"
            value={row.reason}
            onChange={set(index, 'reason')}
            disabled={locked}
            placeholder="예: 예약금으로 노쇼를 막는다 — 우리 차별점과 정면으로 겹친다"
          />
          <TextInput
            label="운영사 (선택)"
            value={row.operatorName}
            onChange={set(index, 'operatorName')}
            disabled={locked}
            placeholder="법인명. 모르면 비워라"
          />
        </div>
      ))}

      <p className="competitor-seeds__hint">
        운영사는 <strong>법인명</strong>이다(서비스 이름이 아니다). 공시 자료 조회가 법인명으로만
        되기 때문이고, <strong>모르면 비우는 편이 낫다</strong> — 틀린 이름으로 조회하면 다른
        회사의 숫자가 붙는다.
      </p>

      <div className="competitor-seeds__actions">
        <Button
          type="button"
          variant="secondary"
          disabled={locked || rows.length >= MAX_ROWS}
          onClick={() => setRows((current) => [...current, EMPTY_ROW])}
        >
          줄 추가
        </Button>
        <Button type="submit" disabled={locked}>{busy ? '저장 중…' : '저장'}</Button>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {saved && !error ? <Alert tone="success">저장했다.</Alert> : null}
      {/* 막지 않는 경고다. 서버가 문구의 정본이라 화면이 다시 쓰지 않는다. */}
      {warning ? <Alert tone="warning">{warning}</Alert> : null}
    </form>
  );
}
