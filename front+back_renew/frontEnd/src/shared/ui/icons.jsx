const PATHS = {
  home: 'M3 11.5 12 4l9 7.5v8a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  project: 'M3.5 6.5h6l1.8 2H20a1 1 0 0 1 1 1v8.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7.5a1 1 0 0 1 .5-1z',
  plus: 'M12 5v14M5 12h14',
  search: 'm20 20-4.5-4.5M10.75 18a7.25 7.25 0 1 1 0-14.5 7.25 7.25 0 0 1 0 14.5z',
  user: 'M20 21a8 8 0 0 0-16 0M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9z',
  settings: 'M12 15.2A3.2 3.2 0 1 0 12 8.8a3.2 3.2 0 0 0 0 6.4zm0-12.2v2m0 13.8v2m9-9h-2M5 12H3m15.36-6.36-1.42 1.42M7.06 16.94l-1.42 1.42m0-12.72 1.42 1.42m9.88 9.88 1.42 1.42',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  upload: 'M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5M5 19.5h14',
  file: 'M6 3h8l4 4v14H6zM14 3v5h5',
  download: 'M12 3v12m0 0 4-4m-4 4-4-4M5 21h14',
  trash: 'M4 7h16m-10 4v6m4-6v6M9 7l1-3h4l1 3m-9 0 1 14h10l1-14',
  chevronRight: 'm9 18 6-6-6-6',
  close: 'm6 6 12 12M18 6 6 18',
  check: 'm5 12 4.2 4.2L19 6.5',
  clock: 'M12 7v5l3.5 2',
  alert: 'M12 3 2.8 20h18.4zM12 9v4m0 3h.01',
  sparkles: 'm12 3 1.3 5.7L19 10l-5.7 1.3L12 17l-1.3-5.7L5 10l5.7-1.3zM19 16l.5 2.5L22 19l-2.5.5L19 22l-.5-2.5L16 19l2.5-.5z',
  lock: 'M6 10h12v10H6zM8.5 10V7a3.5 3.5 0 0 1 7 0v3',
};

export function AppIcon({ name, size = 18, strokeWidth = 1.8, className = '' }) {
  const path = PATHS[name] ?? PATHS.sparkles;
  return <svg className={`app-icon ${className}`} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={path} /></svg>;
}
