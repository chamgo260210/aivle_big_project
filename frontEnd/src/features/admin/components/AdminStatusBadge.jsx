import { adminStatusLabel } from '../model/adminLabels.js';

export default function AdminStatusBadge({ value }) {
  const normalized = String(value || 'UNKNOWN').toUpperCase();
  return (
    <span className={`admin-status admin-status--${normalized.toLowerCase()}`}>
      {adminStatusLabel(normalized)}
    </span>
  );
}
