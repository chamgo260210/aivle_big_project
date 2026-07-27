import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { LoadingState } from '../../shared/ui/index.js';
import { useAuth } from './AuthProvider.jsx';
import { AUTH_STATUS } from './authSession.js';
import { safeReturnTo } from './safeReturnTo.js';

export default function PublicOnlyRoute() {
  const location = useLocation();
  const { status } = useAuth();

  if (status === AUTH_STATUS.UNKNOWN || status === AUTH_STATUS.REFRESHING) {
    return <LoadingState label="로그인 상태를 확인하고 있습니다" />;
  }
  if (status === AUTH_STATUS.AUTHENTICATED) {
    return (
      <Navigate
        replace
        to={safeReturnTo(location.state?.returnTo, '/projects')}
      />
    );
  }
  return <Outlet />;
}
