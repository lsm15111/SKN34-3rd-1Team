import type { FormEvent } from 'react'

import type { AdminAccount } from '../../../../domain/entities/AdminAccount'
import { useAdminAccountsViewModel } from '../viewmodel/useAdminAccountsViewModel'
import { AdminShell } from './AdminShell'
import { adminShellStyles } from './AdminShell.styles'

/** 운영자가 회원·기업을 검색하고 문제 계정의 세션을 즉시 종료하는 화면입니다. */
export function AdminAccountsPage() {
  return (
    <AdminShell
      activeMenu="accounts"
      eyebrow="운영 콘솔"
      title="회원·기업"
      headerNote="비밀번호·세션 정보는 표시하지 않습니다. 관리자 지정은 DB에서만 합니다."
    >
      <AdminAccountsContent />
    </AdminShell>
  )
}

function AdminAccountsContent() {
  const {
    accounts,
    canGoNext,
    canGoPrevious,
    emailInput,
    error,
    goToPage,
    isLoading,
    notice,
    pageIndex,
    revokeSessions,
    revokingAccountId,
    search,
    setEmailInput,
    totalCount,
    totalPages,
  } = useAdminAccountsViewModel()

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    search()
  }

  function handleRevoke(account: AdminAccount) {
    if (!window.confirm(`${account.email} 계정의 모든 세션을 종료할까요? 즉시 로그아웃됩니다.`)) return
    void revokeSessions(account)
  }

  return (
    <>
      <form className={adminShellStyles.searchForm} onSubmit={handleSearch} role="search">
        <input
          className={adminShellStyles.searchInput}
          type="search"
          aria-label="이메일 검색"
          placeholder="이메일 일부로 검색"
          value={emailInput}
          onChange={(event) => setEmailInput(event.target.value)}
        />
        <button className={adminShellStyles.searchButton} type="submit" disabled={isLoading}>
          검색
        </button>
        <span className={adminShellStyles.headerNote}>총 {totalCount}명</span>
      </form>
      {notice ? <p className={adminShellStyles.notice} role="status">{notice}</p> : null}
      {error ? <p className={adminShellStyles.error} role="alert">{error}</p> : null}
      <div className={adminShellStyles.tableWrap}>
        <table className={adminShellStyles.table}>
          <thead>
            <tr>
              <th className={adminShellStyles.headCell} scope="col">이메일</th>
              <th className={adminShellStyles.headCell} scope="col">역할</th>
              <th className={adminShellStyles.headCell} scope="col">기업</th>
              <th className={adminShellStyles.headCell} scope="col">사업자등록번호</th>
              <th className={adminShellStyles.headCell} scope="col">사업자 상태</th>
              <th className={adminShellStyles.headCell} scope="col">가입일</th>
              <th className={adminShellStyles.headCell} scope="col">조치</th>
            </tr>
          </thead>
          <tbody>
            {accounts.length === 0 ? (
              <tr>
                <td className={adminShellStyles.empty} colSpan={7}>
                  {isLoading ? '불러오는 중입니다.' : '조건에 맞는 회원이 없습니다.'}
                </td>
              </tr>
            ) : accounts.map((account) => (
              <tr key={account.id}>
                <td className={adminShellStyles.cell}>{account.email}</td>
                <td className={adminShellStyles.cell}>
                  <span className={adminShellStyles.roleBadge}>{account.role === 'ADMIN' ? '관리자' : '회원'}</span>
                </td>
                <td className={adminShellStyles.cell}>{account.company.companyName}</td>
                <td className={adminShellStyles.cellMuted}>{formatBusinessNumber(account.company.businessNumber)}</td>
                <td className={adminShellStyles.cellMuted}>{account.company.businessStatus || '정보 없음'}</td>
                <td className={adminShellStyles.cellMuted}>{formatDate(account.createdAt)}</td>
                <td className={adminShellStyles.cell}>
                  <button
                    type="button"
                    className={adminShellStyles.actionButton}
                    onClick={() => handleRevoke(account)}
                    disabled={revokingAccountId !== null}
                  >
                    {revokingAccountId === account.id ? '종료 중…' : '세션 종료'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav className={adminShellStyles.pagination} aria-label="페이지">
        <button
          type="button"
          className={adminShellStyles.pageButton}
          onClick={() => goToPage(pageIndex - 1)}
          disabled={!canGoPrevious || isLoading}
        >
          이전
        </button>
        <span>{pageIndex + 1} / {totalPages} 페이지</span>
        <button
          type="button"
          className={adminShellStyles.pageButton}
          onClick={() => goToPage(pageIndex + 1)}
          disabled={!canGoNext || isLoading}
        >
          다음
        </button>
      </nav>
    </>
  )
}

function formatBusinessNumber(businessNumber: string) {
  return businessNumber.length === 10
    ? `${businessNumber.slice(0, 3)}-${businessNumber.slice(3, 5)}-${businessNumber.slice(5)}`
    : businessNumber
}

function formatDate(isoDateTime: string) {
  const date = new Date(isoDateTime)
  return Number.isNaN(date.getTime())
    ? isoDateTime
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeZone: 'Asia/Seoul' }).format(date)
}
