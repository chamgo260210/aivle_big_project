import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { createJourneyApi } from './journeyApi.js';
import './journey.css';

const readinessLabel = {
  UNDER_SPECIFIED: '정보 보완 필요', APPROPRIATE: '검토 준비됨', OVER_SPECIFIED: '범위 정리 필요',
};
const legalLabel = {
  PASS: '통과', PASS_WITH_CONDITIONS: '조건부 통과', REVISION_REQUIRED: '수정 필요',
  PROHIBITED: '진행 금지', INSUFFICIENT_INFORMATION: '정보 부족', EXPERT_REVIEW_REQUIRED: '전문가 검토 필요',
};

function ErrorBanner({ message }) {
  return message ? <div className="journey-error" role="alert"><strong>요청을 완료하지 못했습니다.</strong><span>{message}</span><button type="button" onClick={() => window.location.reload()}>현재 단계 다시 불러오기</button></div> : null;
}

function BusyOverlay({ label }) {
  return <div className="journey-overlay" role="status" aria-live="polite"><span className="journey-spinner" /><strong>{label}</strong><p>AI 응답을 기다리는 동안 이 창을 닫지 마세요.</p></div>;
}

function ResultList({ title, items }) {
  return <section className="journey-result-section"><h3>{title}</h3>{items?.length
    ? <ul>{items.map((item, index) => <li key={`${title}-${index}`}>{typeof item === 'string' ? item : Object.entries(item || {}).map(([key, value]) => `${key}: ${Array.isArray(value) ? value.join(', ') : String(value)}`).join(' · ')}</li>)}</ul>
    : <p className="journey-muted">해당 항목이 없습니다.</p>}</section>;
}

export function IdeaJourneyPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createJourneyApi(client, projectId), [client, projectId]);
  const [tab, setTab] = useState('text');
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');
  const [file, setFile] = useState(null);
  const [source, setSource] = useState(null);
  const [interpretation, setInterpretation] = useState(null);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all([api.currentIdea(), api.currentInterpretation()]).then(([saved, run]) => {
      if (!active) return;
      setSource(saved); setInterpretation(run?.ideaSourceId === saved?.id ? run : null);
      if (saved) { setTitle(saved.title || ''); setText(saved.originalText || ''); setTab(saved.sourceType === 'FILE' ? 'file' : 'text'); }
    }).catch((failure) => active && setError(getUserErrorMessage(failure)));
    return () => { active = false; };
  }, [api]);

  async function save() {
    setError(''); setBusy('아이디어를 저장하고 있습니다');
    try {
      const saved = tab === 'file' ? await api.saveFile(title.trim(), file) : await api.saveText({ title: title.trim() || null, text });
      setSource(saved); setInterpretation(null); return saved;
    } catch (failure) { setError(getUserErrorMessage(failure)); throw failure; }
    finally { setBusy(''); }
  }

  async function interpret() {
    let activeSource = source;
    try {
      if (!source || (tab === 'text' && source.originalText !== text) || (tab === 'file' && file)) activeSource = await save();
      setError(''); setBusy('AI가 아이디어를 해석하고 있습니다');
      setInterpretation(await api.interpret());
    } catch (failure) {
      if (failure?.code === 'RESOURCE_VERSION_CONFLICT') {
        try {
          const recovered = await api.currentInterpretation();
          if (recovered?.state === 'SUCCEEDED' && recovered?.result
              && recovered?.ideaSourceId === activeSource?.id) {
            setInterpretation(recovered);
            setError('');
            return;
          }
        } catch {
          // Keep the original conflict because it is the actionable failure.
        }
      }
      setError(getUserErrorMessage(failure));
    }
    finally { setBusy(''); }
  }

  async function confirm() {
    setError(''); setBusy('아이디어를 확정하고 있습니다');
    try {
      const ideaVersion = await api.confirm(interpretation.ideaVersion.id);
      setInterpretation((current) => ({ ...current, ideaVersion }));
    } catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(''); }
  }

  const result = interpretation?.result;
  const confirmed = interpretation?.ideaVersion?.confirmed;
  return <div className="journey-page">
    {busy && <BusyOverlay label={busy} />}
    <header className="journey-page__heading"><div><span>1단계 · 아이디어</span><h2>아이디어를 명확한 검토 입력으로 만드세요</h2><p>원문을 저장한 뒤 실제 AI가 사실, 가정, 제약과 추가 질문을 분리합니다.</p></div><span className={`journey-save-state ${source ? 'is-saved' : ''}`}>{source ? '저장됨' : '저장 전'}</span></header>
    <ErrorBanner message={error} />
    <section className="journey-card journey-intake">
      <div className="journey-tabs" role="tablist" aria-label="아이디어 입력 방식">
        <button type="button" role="tab" aria-selected={tab === 'text'} onClick={() => setTab('text')}>텍스트 입력</button>
        <button type="button" role="tab" aria-selected={tab === 'file'} onClick={() => setTab('file')}>파일 업로드</button>
      </div>
      <label>아이디어 제목 또는 요약<input value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} placeholder="예: 소상공인 재고 예측 서비스" /></label>
      {tab === 'text' ? <label>아이디어 내용<textarea value={text} maxLength={200000} rows={10} onChange={(event) => setText(event.target.value)} placeholder="누구의 어떤 문제를 어떻게 해결하는지 적어 주세요." /><small>{text.length.toLocaleString()} / 200,000자</small></label>
        : <label>DOCX 또는 TXT 파일<input type="file" accept=".docx,.txt,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setFile(event.target.files?.[0] || null)} /><small>{file?.name || source?.originalFileReference || '선택된 파일 없음'}</small></label>}
      <div className="journey-actions"><button className="journey-button secondary" type="button" disabled={busy || (tab === 'text' ? !text.trim() : !file)} onClick={() => void save()}>아이디어 저장</button><button className="journey-button" type="button" disabled={busy || (!source && (tab === 'text' ? !text.trim() : !file))} onClick={() => void interpret()}>AI 해석 실행</button></div>
    </section>
    {!result ? <section className="journey-empty"><span>AI</span><h3>아직 해석 결과가 없습니다.</h3><p>아이디어를 저장하고 AI 해석을 실행하면 구조화된 결과가 여기에 표시됩니다.</p></section> : <section className="journey-card journey-result">
      <div className="journey-result__header"><div><span className={`journey-badge ${result.readiness?.toLowerCase()}`}>{readinessLabel[result.readiness] || result.readiness}</span><h2>아이디어 해석 결과</h2></div><button type="button" className="journey-text-button" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>원문 수정</button></div>
      <section className="journey-highlight"><h3>원문 요약</h3><p>{result.originalSourceSummary}</p></section>
      <section className="journey-highlight"><h3>정규화된 아이디어</h3><p>{result.normalizedDescription}</p></section>
      <div className="journey-result-grid"><ResultList title="사실" items={result.facts} /><ResultList title="가정" items={result.assumptions} /><ResultList title="제약" items={result.constraints} /><ResultList title="추가 질문" items={result.openQuestions} /><ResultList title="주의 사항" items={result.warnings} /><ResultList title="근거 보완" items={result.evidenceNeeds} /></div>
      <div className="journey-next"><div><strong>{confirmed ? '아이디어가 확정되었습니다.' : '법률 검토 전에 이 버전을 확정하세요.'}</strong><p>확정 후에는 현재 버전을 기준으로 법률 사전 검토를 실행합니다.</p></div>{confirmed ? <Link className="journey-button" to={`/app/projects/${projectId}/legal`}>법률 검토로 이동</Link> : <button className="journey-button" type="button" onClick={() => void confirm()}>이 아이디어 확정</button>}</div>
    </section>}
  </div>;
}

export function LegalJourneyPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createJourneyApi(client, projectId), [client, projectId]);
  const [interpretation, setInterpretation] = useState(null);
  const [review, setReview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { let active = true; Promise.all([api.currentIdea(), api.currentInterpretation(), api.currentLegal()]).then(([source, idea, legal]) => { if (active) { const currentIdea = idea?.ideaSourceId === source?.id && idea?.state === 'SUCCEEDED' ? idea : null; setInterpretation(currentIdea); setReview(legal?.ideaVersionId === currentIdea?.ideaVersion?.id ? legal : null); } }).catch((failure) => active && setError(getUserErrorMessage(failure))); return () => { active = false; }; }, [api]);
  const confirmed = interpretation?.state === 'SUCCEEDED' && interpretation?.ideaVersion?.confirmed;
  async function run() {
    setBusy(true); setError('');
    try {
      setReview(await api.legalReview());
    } catch (failure) {
      if (failure?.code === 'RESOURCE_VERSION_CONFLICT') {
        try {
          const recovered = await api.currentLegal();
          if (recovered?.state === 'SUCCEEDED' && recovered?.result
              && recovered?.ideaVersionId === interpretation?.ideaVersion?.id) {
            setReview(recovered);
            setError('');
            return;
          }
        } catch {
          // One recovery read only; preserve the original conflict if it does not recover.
        }
      }
      setError(getUserErrorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  const result = review?.result;
  const revise = ['REVISION_REQUIRED', 'PROHIBITED'].includes(result?.status);
  const advance = ['PASS', 'PASS_WITH_CONDITIONS'].includes(result?.status);
  return <div className="journey-page">
    {busy && <BusyOverlay label="AI가 법률·규제 위험을 사전 검토하고 있습니다" />}
    <header className="journey-page__heading"><div><span>2단계 · 법률 검토</span><h2>사업 진행 전 잠재 법률·규제 이슈를 확인하세요</h2><p>법제처 또는 공식 법률 데이터가 연결되지 않은 AI 사전 검토입니다.</p></div><span className="journey-save-state is-saved">자동 저장</span></header>
    <ErrorBanner message={error} />
    <aside className="journey-legal-notice"><strong>AI 사전 검토 · 공식 법률 자문 아님</strong><p>출처 검증 상태: 미검증(sourceVerified=false). 실제 의사결정 전에 전문가와 최신 법령을 추가 확인하세요.</p></aside>
    {!confirmed ? <section className="journey-empty"><h3>확정된 아이디어가 필요합니다.</h3><p>아이디어 해석 결과를 확인하고 현재 버전을 확정해야 법률 검토를 실행할 수 있습니다.</p><Link className="journey-button" to={`/app/projects/${projectId}`}>아이디어로 돌아가기</Link></section> : <>
      <section className="journey-card journey-run-card"><div><h3>확정 아이디어 v{interpretation.ideaVersion.versionNumber}</h3><p>{interpretation.ideaVersion.normalizedDescription}</p></div><button className="journey-button" type="button" disabled={busy} onClick={() => void run()}>{result ? '법률 검토 다시 실행' : '법률 검토 실행'}</button></section>
      {!result ? <section className="journey-empty"><h3>아직 법률 검토 결과가 없습니다.</h3><p>실행하면 결과가 DB에 저장되며 새로고침 후에도 복원됩니다.</p></section> : <section className="journey-card journey-result legal">
        <div className="journey-result__header"><div><span className={`journey-badge legal-${result.status?.toLowerCase()}`}>{legalLabel[result.status] || result.status}</span><h2>법률 사전 검토 결과</h2></div><span className="journey-unverified">출처 미검증</span></div>
        <section className="journey-highlight"><h3>요약</h3><p>{result.summary}</p></section>
        <div className="journey-result-grid"><ResultList title="주요 이슈" items={result.issues} /><ResultList title="진행 조건" items={result.conditions} /><ResultList title="금지 요소" items={result.prohibitedElements} /><ResultList title="추가 조사 필요" items={result.researchNeeds} /></div>
        <p className="journey-disclaimer">{result.disclaimer}</p>
        <div className="journey-next"><div><strong>{revise ? '아이디어 수정이 필요합니다.' : advance ? '다음 단계로 진행할 수 있습니다.' : '추가 확인 후 진행 여부를 결정하세요.'}</strong><p>이 결과는 공식 법률 자문이 아니며 sourceVerified=false입니다.</p></div>{revise ? <Link className="journey-button secondary" to={`/app/projects/${projectId}`}>아이디어 수정</Link> : <Link className={`journey-button ${advance ? '' : 'disabled'}`} aria-disabled={!advance} to={advance ? `/app/projects/${projectId}/journey/concept` : '#'}>콘셉트 생성으로</Link>}</div>
      </section>}
    </>}
  </div>;
}

export function LockedJourneyPage({ title }) {
  const { projectId } = useParams();
  return <div className="journey-page"><header className="journey-page__heading"><div><span>준비 중인 단계</span><h2>{title}</h2><p>앞 단계의 실제 결과가 준비된 뒤 사용할 수 있습니다.</p></div><span className="journey-save-state">잠김</span></header><section className="journey-empty locked"><span>🔒</span><h3>{title} 단계는 아직 잠겨 있습니다.</h3><p>가짜 결과는 표시하지 않습니다. 아이디어 확정과 법률 검토를 먼저 완료해 주세요.</p><Link className="journey-button secondary" to={`/app/projects/${projectId}`}>현재 단계 확인</Link></section></div>;
}
