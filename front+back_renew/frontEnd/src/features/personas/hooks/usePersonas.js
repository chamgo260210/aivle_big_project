import { useCallback, useEffect, useRef, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createPersonaApi } from '../api/personaApi.js';

const ACTIVE = new Set(['QUEUED', 'RUNNING']);
const SUCCESS = new Set(['SUCCEEDED', 'PARTIAL']);
const FEASIBILITY_READY = new Set(['COMPLETED', 'NEEDS_VALIDATION']);

export function usePersonas(projectId) {
  const client = useApiClient();
  const mounted = useRef(false);
  const timer = useRef(null);
  const aborter = useRef(null);
  const [state, setState] = useState({
    status: 'loading', catalog: [], recommendation: null,
    feasibility: null, job: null, error: null,
  });

  const clear = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    aborter.current?.abort();
  }, []);

  const loadResult = useCallback(async (signal, catalog) => {
    const recommendation = await createPersonaApi(client).latest(projectId, { signal });
    if (mounted.current) {
      setState((current) => ({
        ...current, status: 'result', recommendation,
        catalog: catalog ?? current.catalog, error: null,
      }));
    }
  }, [client, projectId]);

  const poll = useCallback(async (jobId) => {
    aborter.current = new AbortController();
    try {
      const job = await createPersonaApi(client).job(jobId, { signal: aborter.current.signal });
      if (!mounted.current) return;
      if (ACTIVE.has(job.status)) {
        setState((current) => ({ ...current, status: 'processing', job, error: null }));
        timer.current = setTimeout(() => poll(jobId), document.hidden ? 5000 : 2000);
      } else if (SUCCESS.has(job.status)) {
        await loadResult(aborter.current.signal);
      } else {
        setState((current) => ({ ...current, status: 'failed', job, error: null }));
      }
    } catch (error) {
      if (mounted.current && error.code !== 'REQUEST_ABORTED') {
        setState((current) => ({ ...current, status: 'error', error }));
      }
    }
  }, [client, loadResult]);

  const recover = useCallback(async () => {
    clear();
    setState((current) => ({ ...current, status: 'loading', error: null }));
    aborter.current = new AbortController();
    const api = createPersonaApi(client);
    let catalog = [];
    try {
      catalog = await api.catalog({ signal: aborter.current.signal });
      await loadResult(aborter.current.signal, catalog);
      return;
    } catch (error) {
      if (error.status !== 404) {
        if (mounted.current) setState((current) => ({ ...current, status: 'error', error }));
        return;
      }
    }
    try {
      const job = await api.latestJob(projectId, { signal: aborter.current.signal });
      if (ACTIVE.has(job.status)) {
        if (mounted.current) {
          setState((current) => ({ ...current, status: 'processing', catalog, job }));
          timer.current = setTimeout(() => poll(job.jobId), 0);
        }
        return;
      }
      if (SUCCESS.has(job.status)) {
        await loadResult(aborter.current.signal, catalog);
        return;
      }
    } catch (error) {
      if (error.status !== 404) {
        if (mounted.current) setState((current) => ({ ...current, status: 'error', error }));
        return;
      }
    }
    try {
      const feasibility = await api.latestFeasibility(
        projectId, { signal: aborter.current.signal },
      );
      if (mounted.current) {
        setState({
          status: FEASIBILITY_READY.has(feasibility.status) ? 'ready' : 'not-ready',
          catalog, recommendation: null, feasibility, job: null, error: null,
        });
      }
    } catch (error) {
      if (mounted.current) {
        setState({
          status: error.status === 404 ? 'not-ready' : 'error',
          catalog, recommendation: null, feasibility: null, job: null,
          error: error.status === 404 ? null : error,
        });
      }
    }
  }, [clear, client, loadResult, poll, projectId]);

  const start = useCallback(async () => {
    clear();
    setState((current) => ({ ...current, status: 'starting', error: null }));
    try {
      const accepted = await createPersonaApi(client).start(projectId);
      if (!mounted.current) return;
      setState((current) => ({
        ...current, status: 'processing',
        job: { jobId: accepted.jobId, status: accepted.status, progress: 0 },
      }));
      timer.current = setTimeout(() => poll(accepted.jobId), 0);
    } catch (error) {
      if (mounted.current) setState((current) => ({ ...current, status: 'error', error }));
    }
  }, [clear, client, poll, projectId]);

  useEffect(() => {
    mounted.current = true;
    recover();
    const visible = () => { if (!document.hidden) recover(); };
    document.addEventListener('visibilitychange', visible);
    return () => {
      mounted.current = false;
      document.removeEventListener('visibilitychange', visible);
      clear();
    };
  }, [clear, recover]);

  return { ...state, start, retry: recover };
}
