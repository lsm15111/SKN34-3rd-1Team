import type { ReactNode } from 'react'
import { Link, Navigate } from 'react-router'

import { useAuthSessionViewModel } from '../../auth/viewmodel/useAuthSessionViewModel'
import { adminShellStyles } from './AdminShell.styles'

type AdminShellProps = {
  activeMenu: 'accounts' | 'recruitment-posts'
  eyebrow: string
  title: string
  headerNote?: string
  children: ReactNode
}

/**
 * 운영 콘솔의 공통 셸입니다. 세션 확인 전에는 아무것도 그리지 않고, 관리자가 아니면 홈으로 보냅니다.
 * 실제 권한은 서버가 다시 확인하므로 이 가드는 화면 안내용입니다.
 */
export function AdminShell({ activeMenu, eyebrow, title, headerNote, children }: AdminShellProps) {
  const session = useAuthSessionViewModel()

  if (session.status === 'unknown') {
    return <p className="p-8 text-sm text-[#6d7898]">권한을 확인하고 있습니다.</p>
  }
  if (!session.isAdmin) {
    return <Navigate replace to="/" />
  }

  return (
    <main className={adminShellStyles.page}>
      <aside className={adminShellStyles.sidebar} aria-label="운영 메뉴">
        <div className={adminShellStyles.brand}>
          <span className={adminShellStyles.brandMark}>G</span>
          <div>
            <strong className={adminShellStyles.brandTitle}>GovBiz</strong>
            <span className={adminShellStyles.brandSubtitle}>ADMIN</span>
          </div>
        </div>
        <nav className={adminShellStyles.menu} aria-label="운영 화면">
          <p className={adminShellStyles.menuTitle}>운영</p>
          <Link
            className={`${adminShellStyles.menuLink} ${activeMenu === 'accounts' ? adminShellStyles.menuLinkActive : ''}`}
            to="/admin/accounts"
            aria-current={activeMenu === 'accounts' ? 'page' : undefined}
          >
            회원·기업
          </Link>
          <Link
            className={`${adminShellStyles.menuLink} ${activeMenu === 'recruitment-posts' ? adminShellStyles.menuLinkActive : ''}`}
            to="/admin/recruitment-posts"
            aria-current={activeMenu === 'recruitment-posts' ? 'page' : undefined}
          >
            모집글
          </Link>
        </nav>
        <Link className={adminShellStyles.backLink} to="/">← 검색 화면으로</Link>
      </aside>
      <section className={adminShellStyles.workspace}>
        <header className={adminShellStyles.header}>
          <div>
            <p className={adminShellStyles.eyebrow}>{eyebrow}</p>
            <h1 className={adminShellStyles.title}>{title}</h1>
          </div>
          {headerNote ? <span className={adminShellStyles.headerNote}>{headerNote}</span> : null}
        </header>
        {children}
      </section>
    </main>
  )
}
