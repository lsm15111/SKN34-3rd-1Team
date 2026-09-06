import { Link, useSearchParams } from 'react-router'

import { RequireSignIn } from '../../auth/view/RequireSignIn'
import { useMyRecruitmentPostsViewModel } from '../viewmodel/useMyRecruitmentPostsViewModel'
import { RecruitmentPostCard } from './RecruitmentPostCard'
import { recruitmentStyles } from './Recruitment.styles'
import { SentProposalsList } from './SentProposalsList'

/** 내 기업의 파트너 모집 활동입니다. 탭은 URL `?tab=posts|sent`로 구분해 새로고침·링크 뒤에도 유지됩니다. */
export function MyRecruitmentPostsPage() {
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('tab') === 'sent' ? 'sent' : 'posts'

  return (
    <RequireSignIn>
      <main className={recruitmentStyles.page}>
        <header className={recruitmentStyles.header}>
          <div>
            <Link className={recruitmentStyles.backLink} to="/partners">← 파트너 모집 목록</Link>
            <p className={recruitmentStyles.eyebrow}>파트너 모집</p>
            <h1 className={recruitmentStyles.title}>{tab === 'sent' ? '보낸 제안' : '내 모집글'}</h1>
            <p className={recruitmentStyles.description}>
              {tab === 'sent'
                ? '내 기업이 보낸 제안과 결정 결과입니다. 수락된 제안에는 작성 담당자 이메일이 보입니다.'
                : '종료·숨김된 글도 여기서는 볼 수 있습니다. 받은 제안은 각 글의 상세에서 확인하세요.'}
            </p>
          </div>
          <div className={recruitmentStyles.headerActions}>
            <Link className={recruitmentStyles.primaryButton} to="/partners/new">모집글 작성</Link>
          </div>
        </header>
        <nav className={recruitmentStyles.tabs} aria-label="내 활동">
          <Link className={`${recruitmentStyles.tab} ${tab === 'posts' ? recruitmentStyles.tabActive : ''}`} to="/partners/mine" aria-current={tab === 'posts' ? 'page' : undefined}>
            내 모집글
          </Link>
          <Link className={`${recruitmentStyles.tab} ${tab === 'sent' ? recruitmentStyles.tabActive : ''}`} to="/partners/mine?tab=sent" aria-current={tab === 'sent' ? 'page' : undefined}>
            보낸 제안
          </Link>
        </nav>
        {tab === 'sent' ? <SentProposalsList /> : <MyPostsList />}
      </main>
    </RequireSignIn>
  )
}

function MyPostsList() {
  const { isLoading, loadFailed, posts, reload } = useMyRecruitmentPostsViewModel()

  return (
    <>
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
    </>
  )
}
