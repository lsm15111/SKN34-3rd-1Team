import { Link } from 'react-router'

import { useAuthSessionViewModel } from '../../auth/viewmodel/useAuthSessionViewModel'
import { useRecruitmentPostListViewModel } from '../viewmodel/useRecruitmentPostListViewModel'
import { RecruitmentPostCard } from './RecruitmentPostCard'
import { recruitmentStyles } from './Recruitment.styles'

/** 모집 중인 파트너 모집글 목록입니다. 공고 필터는 URL 검색 매개변수로 받습니다. */
export function RecruitmentPostListPage() {
  const session = useAuthSessionViewModel()
  const {
    canGoNext,
    canGoPrevious,
    clearProgramFilter,
    goToPage,
    isLoading,
    loadFailed,
    pageIndex,
    posts,
    programFilter,
    reload,
    totalCount,
  } = useRecruitmentPostListViewModel()

  return (
    <main className={recruitmentStyles.page}>
      <header className={recruitmentStyles.header}>
        <div>
          <Link className={recruitmentStyles.backLink} to="/">← 검색으로 돌아가기</Link>
          <p className={recruitmentStyles.eyebrow}>파트너 모집</p>
          <h1 className={recruitmentStyles.title}>함께 신청할 기업 찾기</h1>
          <p className={recruitmentStyles.description}>
            모든 모집글은 공식 공고 하나에 묶입니다. 공고가 마감되면 모집도 자동으로 종료됩니다.
          </p>
        </div>
        <div className={recruitmentStyles.headerActions}>
          {session.isAuthenticated ? (
            <Link className={recruitmentStyles.secondaryButton} to="/partners/mine">내 모집글</Link>
          ) : null}
          <Link className={recruitmentStyles.primaryButton} to="/partners/new">모집글 작성</Link>
        </div>
      </header>

      {programFilter ? (
        <p className={recruitmentStyles.notice} role="status">
          선택한 공고({programFilter.sourceProgramId})의 모집글만 보고 있습니다.{' '}
          <button type="button" className={recruitmentStyles.backLink} onClick={clearProgramFilter}>전체 보기</button>
        </p>
      ) : null}
      {loadFailed ? (
        <p className={recruitmentStyles.error} role="alert">
          모집글을 불러오지 못했습니다.{' '}
          <button type="button" className={recruitmentStyles.backLink} onClick={() => void reload()}>다시 시도</button>
        </p>
      ) : null}

      <p className={recruitmentStyles.hint}>모집 중 {totalCount}건 · 마감 임박순</p>
      {posts.length === 0 ? (
        <p className={recruitmentStyles.empty}>
          {isLoading ? '모집글을 불러오는 중입니다.' : '아직 모집 중인 글이 없습니다. 첫 모집글을 올려 보세요.'}
        </p>
      ) : (
        <div className={recruitmentStyles.cardList}>
          {posts.map((post) => <RecruitmentPostCard key={post.id} post={post} />)}
        </div>
      )}

      <nav className={recruitmentStyles.pagination} aria-label="페이지">
        <button type="button" className={recruitmentStyles.pageButton} onClick={() => goToPage(pageIndex - 1)} disabled={!canGoPrevious || isLoading}>
          이전
        </button>
        <span>{pageIndex + 1} 페이지</span>
        <button type="button" className={recruitmentStyles.pageButton} onClick={() => goToPage(pageIndex + 1)} disabled={!canGoNext || isLoading}>
          다음
        </button>
      </nav>
    </main>
  )
}
