export function safeReturnTo(value, fallback = '/app') {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  ) {
    return fallback;
  }
  try {
    const parsed = new URL(value, 'https://app.local');
    return parsed.origin === 'https://app.local'
      ? `${parsed.pathname}${parsed.search}${parsed.hash}`
      : fallback;
  } catch {
    return fallback;
  }
}

export function safeReturnToForRole(value, role) {
  const fallback = role === 'ADMIN' ? '/admin' : '/app';
  const destination = safeReturnTo(value, fallback);
  if (role !== 'ADMIN' && (destination === '/admin' || destination.startsWith('/admin/'))) {
    return '/app';
  }
  return destination;
}
