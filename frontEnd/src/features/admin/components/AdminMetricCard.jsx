export default function AdminMetricCard({ label, value, description, tone = 'neutral', progress }) {
  return (
    <article className={`admin-metric admin-metric--${tone}`}>
      <span>{label}</span>
      <strong>{value ?? '—'}</strong>
      {Number.isFinite(progress) && (
        <span className="admin-metric__progress" aria-label={`${label} ${Math.round(progress)}%`}>
          <i style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
        </span>
      )}
      {description && <small>{description}</small>}
    </article>
  );
}
