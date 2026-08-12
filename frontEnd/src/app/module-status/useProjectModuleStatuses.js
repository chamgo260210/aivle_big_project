import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createProjectModuleApi } from './projectModuleApi.js';
import { normalizeProjectModuleStatuses } from './projectModuleModel.js';

const techOpsAdvisoryStorageKey = (projectId) => `aivle:tech-ops:commercialization-advisory:${projectId}`;

function hasCompletedTechOpsAdvisory(projectId) {
  try {
    const saved = JSON.parse(window.localStorage.getItem(techOpsAdvisoryStorageKey(projectId)) || 'null');
    return Boolean(saved?.result);
  } catch {
    return false;
  }
}

function applyClientCompletion(projectId, modules) {
  if (!hasCompletedTechOpsAdvisory(projectId)) return modules;
  return { ...modules, techOps: { ...(modules.techOps ?? {}), status: 'COMPLETED' } };
}

export function useProjectModuleStatuses(projectId, liveRevision = 0) {
  const client = useApiClient();
  const api = useMemo(() => createProjectModuleApi(client), [client]);
  const [refreshKey, setRefreshKey] = useState(0);
  const [state, setState] = useState({ projectId, status: 'loading', modules: {}, error: null });
  const retry = useCallback(() => {
    setState({ projectId, status: 'loading', modules: {}, error: null });
    setRefreshKey((value) => value + 1);
  }, [projectId]);

  useEffect(() => {
    const controller = new AbortController();
    api.findAll(projectId, { signal: controller.signal })
      .then((items) => {
        if (!controller.signal.aborted) {
          setState({ projectId, status: 'success', modules: applyClientCompletion(projectId, normalizeProjectModuleStatuses(items)), error: null });
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted) setState({ projectId, status: 'error', modules: {}, error });
      });
    return () => controller.abort();
  }, [api, projectId, refreshKey, liveRevision]);

  useEffect(() => {
    const onAdvisoryCompleted = (event) => {
      if (String(event.detail?.projectId) !== String(projectId)) return;
      setState((current) => current.projectId !== projectId ? current : {
        ...current,
        modules: { ...current.modules, techOps: { ...(current.modules.techOps ?? {}), status: 'COMPLETED' } },
      });
    };
    window.addEventListener('tech-ops-advisory-completed', onAdvisoryCompleted);
    return () => window.removeEventListener('tech-ops-advisory-completed', onAdvisoryCompleted);
  }, [projectId]);

  if (state.projectId !== projectId) {
    return { status: 'loading', modules: {}, error: null, retry };
  }
  return { ...state, retry };
}
