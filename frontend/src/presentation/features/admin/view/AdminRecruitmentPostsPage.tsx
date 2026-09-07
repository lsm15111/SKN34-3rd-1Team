import { Link } from 'react-router'

import type { AdminRecruitmentPost } from '../../../../domain/entities/AdminRecruitmentPost'
import { recruitmentPostStatusLabels, recruitmentRoleLabels } from '../../../../domain/entities/RecruitmentPost'
import {
  ADMIN_REASON_MAX_LENGTH,
  useAdminRecruitmentPostsViewModel,
  type AdminStatusFilter,
} from '../viewmodel/useAdminRecruitmentPostsViewModel'
import { AdminShell } from './AdminShell'
import { adminShellStyles } from './AdminShell.styles'

const statusFilterOptions: { value: AdminStatusFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'OPEN', label: '모집 중' },
  { value: 'CLOSED', label: '모집 종료' },
  { value: 'HIDDEN', label: '운영자 숨김' },
]

/** 운영자가 모든 모집글을 보고 숨김·해제·강제 마감하는 화면입니다. 숨김·마감은 사유를 받은 뒤 실행합니다. */
export function AdminRecruitmentPostsPage() {
  return (
    <AdminShell
      activeMenu="recruitment-posts"
      eyebrow="운영 콘솔"
      title="모집글"
      headerNote="숨긴 글은 작성 기업 외에는 보이지 않고 제안도 받지 않습니다. 조치는 서버 로그에 남습니다."
    >
      <AdminRecruitmentPostsContent />
    </AdminShell>
  )
}

function AdminRecruitmentPostsContent() {
  const viewModel = useAdminRecruitmentPostsViewModel()
  const {
    actingPostId, beginAction, cancelAction, canGoNext, canGoPrevious, changeStatusFilter, confirmAction, error,
    goToPage, isLoading, notice, pageIndex, pendingAction, posts, reason, reasonError, setReason, statusFilter,
    totalCount, totalPages, unhide,
  } = viewModel

  return (
    <>
      <div className={adminShellStyles.searchForm}>
        <label className={adminShellStyles.headerNote} htmlFor="status-filter">상태</label>
        <select
          id="status-filter"
          className={adminShellStyles.select}
          value={statusFilter}
          onChange={(event) => changeStatusFilter(event.target.value as AdminStatusFilter)}
        >
          {statusFilterOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <span className={adminShellStyles.headerNote}>총 {totalCount}건</span>
      </div>
      {notice ? <p className={adminShellStyles.notice} role="status">{notice}</p> : null}
      {error ? <p className={adminShellStyles.error} role="alert">{error}</p> : null}
      {pendingAction ? (
        <section className={adminShellStyles.actionPanel} aria-labelledby="action-title">
          <h2 id="action-title" className={adminShellStyles.actionTitle}>
            {pendingAction.action === 'hide' ? '모집글 숨김' : '모집글 강제 마감'} · {pendingAction.post.title}
          </h2>
          <p className={adminShellStyles.headerNote}>
            {pendingAction.action === 'hide'
              ? '사유는 모집글에 남고 작성 기업의 내 모집글에서 상태로 보입니다.'
              : '사유는 서버 로그에만 남습니다. 마감하면 작성 기업도 다시 열 수 없습니다.'}
          </p>
          <label className={adminShellStyles.reasonLabel} htmlFor="action-reason">
            사유
            <textarea
              id="action-reason"
              className={adminShellStyles.reasonInput}
              maxLength={ADMIN_REASON_MAX_LENGTH}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              aria-invalid={reasonError ? true : undefined}
            />
            {reasonError ? <span className={adminShellStyles.reasonError}>{reasonError}</span> : null}
          </label>
          <div className={adminShellStyles.actionButtons}>
            <button type="button" className={adminShellStyles.searchButton} onClick={() => void confirmAction()} disabled={actingPostId !== null}>
              {actingPostId !== null ? '처리 중…' : pendingAction.action === 'hide' ? '숨기기' : '마감하기'}
            </button>
            <button type="button" className={adminShellStyles.pageButton} onClick={cancelAction} disabled={actingPostId !== null}>
              취소
            </button>
          </div>
        </section>
      ) : null}
      <div className={adminShellStyles.tableWrap}>
        <table className={adminShellStyles.table}>
          <thead>
            <tr>
              <th className={adminShellStyles.headCell} scope="col">제목</th>
              <th className={adminShellStyles.headCell} scope="col">작성 기업</th>
              <th className={adminShellStyles.headCell} scope="col">연결 공고</th>
              <th className={adminShellStyles.headCell} scope="col">상태</th>
              <th className={adminShellStyles.headCell} scope="col">제안</th>
              <th className={adminShellStyles.headCell} scope="col">숨김 사유</th>
              <th className={adminShellStyles.headCell} scope="col">조치</th>
            </tr>
          </thead>
          <tbody>
            {posts.length === 0 ? (
              <tr>
                <td className={adminShellStyles.empty} colSpan={7}>
                  {isLoading ? '불러오는 중입니다.' : '조건에 맞는 모집글이 없습니다.'}
                </td>
              </tr>
            ) : posts.map((post) => (
              <tr key={post.id}>
                <td className={adminShellStyles.cell}>
                  <Link className={adminShellStyles.cellLink} to={`/partners/${post.id}`}>{post.title}</Link>
                  <span className={adminShellStyles.cellSub}>
                    {recruitmentRoleLabels[post.ourRole]} → {recruitmentRoleLabels[post.wantedRole]} · 마감 {post.closesOn} · 작성 {formatDate(post.createdAt)}
                  </span>
                </td>
                <td className={adminShellStyles.cell}>{post.company.companyName}</td>
                <td className={adminShellStyles.cellMuted}>{post.program ? post.program.title : '공고 미공개'}</td>
                <td className={adminShellStyles.cell}>
                  <span className={adminShellStyles.roleBadge}>{recruitmentPostStatusLabels[post.status]}</span>
                </td>
                <td className={adminShellStyles.cellMuted}>{post.proposalCount}건</td>
                <td className={adminShellStyles.cellMuted}>{post.hiddenReason ?? '—'}</td>
                <td className={adminShellStyles.cell}>
                  <PostActions post={post} actingPostId={actingPostId} onHide={() => beginAction(post, 'hide')} onUnhide={() => void unhide(post)} onClose={() => beginAction(post, 'close')} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav className={adminShellStyles.pagination} aria-label="페이지">
        <button type="button" className={adminShellStyles.pageButton} onClick={() => goToPage(pageIndex - 1)} disabled={!canGoPrevious || isLoading}>
          이전
        </button>
        <span>{pageIndex + 1} / {totalPages} 페이지</span>
        <button type="button" className={adminShellStyles.pageButton} onClick={() => goToPage(pageIndex + 1)} disabled={!canGoNext || isLoading}>
          다음
        </button>
      </nav>
    </>
  )
}

function PostActions({
  post, actingPostId, onHide, onUnhide, onClose,
}: {
  post: AdminRecruitmentPost
  actingPostId: number | null
  onHide: () => void
  onUnhide: () => void
  onClose: () => void
}) {
  const busy = actingPostId !== null
  return (
    <div className={adminShellStyles.actionButtons}>
      {post.status === 'HIDDEN' ? (
        <button type="button" className={adminShellStyles.pageButton} onClick={onUnhide} disabled={busy} aria-label={`${post.title} 숨김 해제`}>
          {actingPostId === post.id ? '처리 중…' : '숨김 해제'}
        </button>
      ) : (
        <button type="button" className={adminShellStyles.actionButton} onClick={onHide} disabled={busy} aria-label={`${post.title} 숨기기`}>
          숨기기
        </button>
      )}
      {post.status === 'OPEN' ? (
        <button type="button" className={adminShellStyles.actionButton} onClick={onClose} disabled={busy} aria-label={`${post.title} 강제 마감`}>
          강제 마감
        </button>
      ) : null}
    </div>
  )
}

function formatDate(isoDateTime: string) {
  const date = new Date(isoDateTime)
  return Number.isNaN(date.getTime())
    ? isoDateTime
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeZone: 'Asia/Seoul' }).format(date)
}
