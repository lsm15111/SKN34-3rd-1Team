import { Link } from 'react-router'

import { ResultsRegion } from '../../../shared/data/ResultsRegion'
import { workspacePageStyles, workspaceTagClassName } from '../../../shared/workspace/WorkspacePage.styles'
import { WorkspacePageHeader } from '../../../shared/workspace/WorkspacePageHeader'
import { useAdminAccountListViewModel } from '../viewmodel/useAdminAccountListViewModel'
import { adminAccountsPageStyles as styles, adminStatValueClassName } from './AdminAccountsPage.styles'

/** 관리자 계정 목록입니다. 검색·필터로 계정을 찾고, 정지·강제 로그아웃 같은 조치는 상세에서 합니다. */
export function AdminAccountsPage() {
  const vm = useAdminAccountListViewModel()
  const filters = [
    { label: '상태', value: vm.status, options: vm.statusOptions, onChange: vm.selectStatus },
    { label: '권한', value: vm.role, options: vm.roleOptions, onChange: vm.selectRole },
    { label: '로그인 방법', value: vm.loginMethod, options: vm.loginMethodOptions, onChange: vm.selectLoginMethod },
    { label: '정렬', value: vm.sort, options: vm.sortOptions, onChange: vm.selectSort },
  ]

  return (
    <>
      <WorkspacePageHeader title="계정 관리" />

      <div className={workspacePageStyles.content}>
        {vm.stats ? (
          <div className={styles.statRow} role="group" aria-label="계정 요약">
            {vm.stats.map((stat) => (
              <div className={styles.statCell} key={stat.label}>
                <strong className={adminStatValueClassName(stat.tone)}>{stat.value}</strong>
                <span className={styles.statLabel}>{stat.label}</span>
              </div>
            ))}
          </div>
        ) : null}

        <section className={workspacePageStyles.card} aria-label="계정 목록">
          {/* 검색어는 조회를 눌러야 적용됩니다. 이메일·기업명, 숫자면 사업자등록번호 일부로도 찾습니다. */}
          <form
            className={styles.searchRow}
            aria-label="계정 검색"
            onSubmit={(event) => {
              event.preventDefault()
              vm.submitSearch()
            }}
          >
            <label className={styles.search}>
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <circle cx="11" cy="11" r="8" />
                <path d="M21 21l-4.35-4.35" />
              </svg>
              <span className="sr-only">계정 검색</span>
              <input
                className={styles.searchInput}
                type="search"
                name="keyword"
                maxLength={100}
                placeholder="이메일, 기업명, 사업자등록번호"
                value={vm.keyword}
                onChange={(event) => vm.updateKeyword(event.target.value)}
              />
            </label>
            <button className={workspacePageStyles.primaryButton} type="submit">조회</button>
          </form>

          <div className={styles.toolbar}>
            <h2 className={styles.resultCount} aria-live="polite">
              {vm.resultTotal === null
                ? '검색 결과'
                : <>검색 결과 <span className={styles.resultTotal}>{vm.resultTotal.toLocaleString()}건</span></>}
            </h2>
            <div className={styles.filters}>
              {filters.map((filter) => (
                <label className={styles.filterLabel} key={filter.label}>{filter.label}
                  <select
                    className={styles.filterSelect}
                    aria-label={filter.label}
                    value={filter.value}
                    onChange={(event) => filter.onChange(event.target.value)}
                  >
                    {filter.options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                  </select>
                </label>
              ))}
              {vm.hasFilters ? (
                <button className={workspacePageStyles.quietLink} type="button" onClick={vm.resetFilters}>조건 초기화</button>
              ) : null}
            </div>
          </div>

          <ResultsRegion phase={vm.phase} hasData={vm.hasPage} loadingLabel="계정 목록을 불러오는 중입니다." onRetry={vm.retry}
            failedLabel="새 조건의 계정 목록을 불러오지 못했습니다. 이전 결과를 보여 드리고 있어요."
            skeleton={<AdminAccountTableSkeleton />}
            error={<p className={workspacePageStyles.emptyNote}>
              계정 목록을 불러오지 못했습니다.{' '}
              <button className={workspacePageStyles.quietLink} type="button" onClick={vm.retry}>다시 시도</button>
            </p>}>
          {vm.rows.length === 0 ? (
            <p className={workspacePageStyles.emptyNote}>조건에 맞는 계정이 없습니다.</p>
          ) : (
            <div className={styles.tableScroll} role="region" aria-label="계정 표 가로 스크롤" tabIndex={0}>
              <table className={workspacePageStyles.table}>
                <thead>
                  <tr>
                    <th className={workspacePageStyles.tableHeadCell}>이메일</th>
                    <th className={workspacePageStyles.tableHeadCell}>기업</th>
                    <th className={workspacePageStyles.tableHeadCell}>권한</th>
                    <th className={workspacePageStyles.tableHeadCell}>로그인 방법</th>
                    <th className={workspacePageStyles.tableHeadCell}>인증</th>
                    <th className={workspacePageStyles.tableHeadCell}>가입일</th>
                    <th className={workspacePageStyles.tableHeadCell}>최근 로그인</th>
                    <th className={workspacePageStyles.tableHeadCell}>상태</th>
                  </tr>
                </thead>
                <tbody>
                  {vm.rows.map((row) => (
                    <tr className={row.isSuspended ? workspacePageStyles.dangerRow : ''} key={row.id}>
                      <td className={workspacePageStyles.tableCell}>
                        <Link className={styles.emailLink} to={row.detailPath}>{row.email}</Link>
                      </td>
                      <td className={workspacePageStyles.tableCell}>
                        {row.companyName ?? <span className={styles.mutedText}>미등록</span>}
                      </td>
                      <td className={workspacePageStyles.tableCell}>
                        <span className={workspaceTagClassName(row.isAdmin ? 'info' : 'muted')}>{row.roleLabel}</span>
                      </td>
                      <td className={workspacePageStyles.tableCell}>
                        <span className={styles.tagList}>
                          {row.loginMethodLabels.map((label) => <span className={workspaceTagClassName('muted')} key={label}>{label}</span>)}
                        </span>
                      </td>
                      <td className={workspacePageStyles.tableCell}>
                        <span className={workspaceTagClassName(row.emailVerified ? 'ok' : 'warn')}>
                          {row.emailVerified ? '인증됨' : '미인증'}
                        </span>
                      </td>
                      <td className={`${workspacePageStyles.tableCell} ${styles.nowrapCell}`}>{row.createdOn}</td>
                      <td className={`${workspacePageStyles.tableCell} ${styles.nowrapCell}`}>{row.lastLogin}</td>
                      <td className={workspacePageStyles.tableCell}>
                        <span className={workspaceTagClassName(row.isSuspended ? 'danger' : 'ok')}>{row.statusLabel}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          </ResultsRegion>

          {vm.totalPages > 1 ? (
            <nav className={styles.pagination} aria-label="계정 페이지">
              <button className={workspacePageStyles.secondaryButton} type="button" disabled={vm.currentPage <= 1} onClick={() => vm.goToPage(vm.currentPage - 1)}>
                이전
              </button>
              <span className={styles.pageLabel}>{vm.currentPage} / {vm.totalPages}</span>
              <button className={workspacePageStyles.secondaryButton} type="button" disabled={vm.currentPage >= vm.totalPages} onClick={() => vm.goToPage(vm.currentPage + 1)}>
                다음
              </button>
            </nav>
          ) : null}
        </section>
      </div>
    </>
  )
}

/** 계정 표의 자리 표시입니다. 첫 진입에 표가 생길 자리를 미리 차지해 화면이 튀지 않게 합니다. */
function AdminAccountTableSkeleton({ rows = 6 }: { rows?: number }) {
  const bar = 'block h-3 rounded-full bg-[#eceff1] motion-safe:animate-pulse'
  return <div className="grid gap-3 rounded-[1rem] border border-sample-border bg-white p-4">
    {Array.from({ length: rows }, (_, index) => <div key={index} className="grid grid-cols-[minmax(0,2fr)_repeat(3,minmax(0,1fr))] gap-4">
      <span className={`${bar} w-4/5`} /><span className={`${bar} w-2/3`} /><span className={`${bar} w-1/2`} /><span className={`${bar} w-1/2`} />
    </div>)}
  </div>
}
