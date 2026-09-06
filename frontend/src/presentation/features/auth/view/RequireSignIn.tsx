import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { useAuthSessionViewModel } from '../viewmodel/useAuthSessionViewModel'

/**
 * 로그인이 필요한 화면을 감쌉니다. 세션 확인 전에는 아무것도 그리지 않고, 비로그인이면 로그인 화면으로 보내며
 * 로그인 뒤 원래 경로로 돌아옵니다. 실제 권한은 서버가 다시 확인합니다.
 */
export function RequireSignIn({ children }: { children: ReactNode }) {
  const session = useAuthSessionViewModel()
  const location = useLocation()

  if (session.status === 'unknown') {
    return <p className="p-8 text-sm text-[#6d7898]">로그인 상태를 확인하고 있습니다.</p>
  }
  if (!session.isAuthenticated) {
    return <Navigate replace to="/login" state={{ from: `${location.pathname}${location.search}` }} />
  }
  return children
}
