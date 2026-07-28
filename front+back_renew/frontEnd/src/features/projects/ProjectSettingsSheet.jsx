import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { Alert, AppIcon, Button, Dialog, SideSheet, TextInput, Textarea } from '../../shared/ui/index.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createProjectApi } from './api/projectApi.js';
import { useProjectContext } from './ProjectContext.jsx';
import { appRoutes, projectRoutes } from './routing/projectRoutes.js';
import './projects.css';

export function ProjectActionMenu({ project, onDelete }) {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const close = () => setOpen(false);
  return <div className="project-action-menu"><Button type="button" variant="ghost" className="project-action-menu__trigger" aria-label={`${project.name} 프로젝트 메뉴`} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((value) => !value)}><AppIcon name="more" /></Button>{open && <div className="project-action-menu__panel" role="menu"><button type="button" role="menuitem" onClick={() => { navigate(projectRoutes.overview(project.projectId)); close(); }}>프로젝트 열기</button><button type="button" role="menuitem" onClick={() => { navigate(projectRoutes.settings(project.projectId)); close(); }}>프로젝트 설정</button><button type="button" role="menuitem" className="is-danger" onClick={() => { onDelete?.(); close(); }}><AppIcon name="trash" />프로젝트 삭제</button></div>}</div>;
}

export default function ProjectSettingsSheet() {
  const client = useApiClient();
  const navigate = useNavigate();
  const { project, retry } = useProjectContext();
  const [values, setValues] = useState({ title: project.name, industryCategory: project.industryCategory || '', description: project.description || '' });
  const [saving, setSaving] = useState(false); const [message, setMessage] = useState(''); const [error, setError] = useState('');
  const [confirmingDelete, setConfirmingDelete] = useState(false); const [confirmation, setConfirmation] = useState(''); const [deleting, setDeleting] = useState(false); const [deleteError, setDeleteError] = useState('');
  const update = (field) => (event) => setValues((current) => ({ ...current, [field]: event.target.value }));
  const close = () => navigate(projectRoutes.overview(project.projectId));
  const save = async (event) => { event.preventDefault(); if (saving || !values.title.trim()) return; setSaving(true); setError(''); setMessage(''); try { await createProjectApi(client).update(project.projectId, { title: values.title.trim(), industryCategory: values.industryCategory.trim() || null, description: values.description.trim() || null }); await retry(); setMessage('변경사항을 저장했습니다.'); } catch (nextError) { setError(getUserErrorMessage(nextError)); } finally { setSaving(false); } };
  const remove = async () => { if (confirmation !== project.name || deleting) return; setDeleting(true); setDeleteError(''); try { await createProjectApi(client).remove(project.projectId); navigate(appRoutes.projects, { replace: true }); } catch (nextError) { setDeleteError(getUserErrorMessage(nextError)); setDeleting(false); } };
  return <><SideSheet open title="Project Settings" label="프로젝트 설정" onClose={saving ? () => {} : close} footer={<><Button variant="outline" disabled={saving} onClick={close}>취소</Button><Button type="submit" form="project-settings-form" loading={saving} disabled={saving}>변경사항 저장</Button></>}><p className="project-sheet__description">프로젝트 기본 정보와 삭제를 관리합니다.</p><form id="project-settings-form" className="project-sheet__form" onSubmit={save}>{error && <Alert tone="danger" title="저장하지 못했습니다.">{error}</Alert>}{message && <Alert tone="success" title="저장됨">{message}</Alert>}<TextInput id="project-settings-title" label="프로젝트 이름" value={values.title} maxLength="150" required onChange={update('title')} /><TextInput id="project-settings-industry" label="사업 분야" value={values.industryCategory} maxLength="100" onChange={update('industryCategory')} /><Textarea id="project-settings-description" label="프로젝트 설명" value={values.description} maxLength="10000" onChange={update('description')} /></form><section className="project-sheet__danger"><div><AppIcon name="trash" /><h2>프로젝트 삭제</h2><p>연결된 문서와 분석 결과를 더 이상 사용할 수 없습니다.</p></div><Button variant="danger" onClick={() => setConfirmingDelete(true)}><AppIcon name="trash" />프로젝트 삭제</Button></section></SideSheet><Dialog open={confirmingDelete} onClose={() => !deleting && setConfirmingDelete(false)} title="프로젝트를 삭제할까요?"><p>이 작업은 되돌릴 수 없습니다. 확인을 위해 <strong>{project.name}</strong>을(를) 입력하세요.</p>{deleteError && <Alert tone="danger" title="삭제하지 못했습니다.">{deleteError}</Alert>}<TextInput id="delete-project-confirmation" label="프로젝트 이름" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} /><div className="dialog-actions"><Button variant="outline" disabled={deleting} onClick={() => setConfirmingDelete(false)}>취소</Button><Button variant="danger" loading={deleting} disabled={confirmation !== project.name || deleting} onClick={remove}>영구 삭제</Button></div></Dialog></>;
}
