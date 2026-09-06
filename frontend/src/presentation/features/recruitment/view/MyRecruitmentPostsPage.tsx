import { Link } from 'react-router'

import { RequireSignIn } from '../../auth/view/RequireSignIn'
import { useMyRecruitmentPostsViewModel } from '../viewmodel/useMyRecruitmentPostsViewModel'
import { RecruitmentPostCard } from './RecruitmentPostCard'
import { recruitmentStyles } from './Recruitment.styles'

/** 내 기업이 쓴 모집글을 종료·숨김 상태까지 모두 보여 줍니다. */
export function MyRecruitmentPostsPage() {
  return (
    <RequireSignIn>
      <MyRecruitmentPostsContent />
    </RequireSignIn>
  )
}

function MyRecruitmentPostsContent() {
  const { isLoading, loadFailed, posts, reload } = useMyRecruitmentPostsViewModel()

  return (
    <main className={recruitmentStyles.page}>
      <header className={recruitmentStyles.header}>
        <div>
          <Link className={recruitmentStyles.backLink} to="/partners">← 파트너 모집 목록</Link>
          <p className={recruitmentStyles.eyebrow}>파트너 모집</p>
          <h1 className={recruitmentStyles.title}>내 모집글</h1>
          <p className={recruitmentStyles.description}>종료·숨김된 글도 여기서는 볼 수 있습니다.</p>
        </div>
        <div className={recruitmentStyles.headerActions}>
          <Link className={recruitmentStyles.primaryButton} to="/partners/new">모집글 작성</Link>
        </div>
      </header>
      {loadFailed ? (
        <p className={recruitmentStyles.error} role="alert">
          내 모집글을 불러오지 못했습니다.{' '}
          <button type="button" className={recruitmentStyles.backLink} onClick={() => void reload()}>다시 시도</button>
        </p>
      ) : null}
      {posts.length === 0 ? (
        <p className={recruitmentStyles.empty}>
          {isLoading ? '불러오는 중입니다.' : '아직 작성한 모집글이 없습니다.'}
        </p>
      ) : (
        <div className={recruitmentStyles.cardList}>
          {posts.map((post) => <RecruitmentPostCard key={post.id} post={post} />)}
        </div>
      )}
    </main>
  )
}
