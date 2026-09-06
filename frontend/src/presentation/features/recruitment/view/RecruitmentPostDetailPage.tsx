import { Link, useParams } from 'react-router'

import {
  recruitmentPostStatusLabels,
  recruitmentRoleLabels,
  type RecruitmentPost,
} from '../../../../domain/entities/RecruitmentPost'
import { formatDate, formatDeadline } from '../viewmodel/recruitmentDates'
import { useRecruitmentPostDetailViewModel } from '../viewmodel/useRecruitmentPostDetailViewModel'
import { recruitmentStatusClassName, recruitmentStyles } from './Recruitment.styles'

const programStatusLabels = { OPEN: '접수 중', UPCOMING: '접수 예정', CLOSED: '접수 종료', UNKNOWN: '기간 확인 필요' } as const

/** URL의 모집글 ID로 상세를 조회하는 화면입니다. 작성 기업에게는 수정·조기 마감이 보입니다. */
export function RecruitmentPostDetailPage() {
  const params = useParams()
  const postId = Number.parseInt(params.postId ?? '', 10)

  if (!Number.isInteger(postId) || postId <= 0) {
    return <UnavailablePost title="모집글을 찾을 수 없습니다" description="주소가 올바르지 않습니다. 목록에서 다시 선택해 주세요." />
  }
  return <RecruitmentPostDetailContent key={postId} postId={postId} />
}

function RecruitmentPostDetailContent({ postId }: { postId: number }) {
  const { closeEarly, isClosing, notice, state } = useRecruitmentPostDetailViewModel(postId)

  if (state.status === 'loading') {
    return <main className={recruitmentStyles.narrowPage}><p className={recruitmentStyles.hint}>모집글을 불러오는 중입니다.</p></main>
  }
  if (state.status === 'not-found') {
    return <UnavailablePost title="모집글을 찾을 수 없습니다" description="삭제됐거나 종료되어 더 이상 볼 수 없는 모집글입니다." />
  }
  if (state.status === 'failed') {
    return <UnavailablePost title="모집글을 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." />
  }

  const post = state.post

  function handleClose() {
    if (!window.confirm('모집을 지금 마감할까요? 마감하면 목록에서 사라지고 다시 열 수 없습니다.')) return
    void closeEarly()
  }

  return (
    <main className={recruitmentStyles.page}>
      <header className={recruitmentStyles.header}>
        <div>
          <Link className={recruitmentStyles.backLink} to="/partners">← 파트너 모집 목록</Link>
          <p className={recruitmentStyles.eyebrow}>모집글 상세</p>
          <h1 className={recruitmentStyles.title}>{post.title}</h1>
          <p className={recruitmentStyles.description}>
            <span className={recruitmentStatusClassName(post.status)}>{recruitmentPostStatusLabels[post.status]}</span>
            {' '}· {post.status === 'OPEN' ? formatDeadline(post.closesOn) : `모집 마감일 ${post.closesOn}`}
            {' '}· 작성 {formatDate(post.createdAt)}
          </p>
        </div>
        {post.viewer.isOwner ? (
          <div className={recruitmentStyles.headerActions}>
            <Link className={recruitmentStyles.secondaryButton} to={`/partners/${post.id}/edit`} aria-disabled={post.status !== 'OPEN'}>
              수정
            </Link>
            <button type="button" className={recruitmentStyles.dangerButton} onClick={handleClose} disabled={post.status !== 'OPEN' || isClosing}>
              {isClosing ? '마감 중…' : '조기 마감'}
            </button>
          </div>
        ) : null}
      </header>

      {notice ? <p className={recruitmentStyles.notice} role="status">{notice}</p> : null}

      <div className={recruitmentStyles.detailLayout}>
        <div className="grid gap-6">
          <section className={recruitmentStyles.section} aria-labelledby="conditions-title">
            <h2 id="conditions-title" className={recruitmentStyles.sectionTitle}>모집 조건</h2>
            <PostConditions post={post} />
          </section>
          <section className={recruitmentStyles.section} aria-labelledby="body-title">
            <h2 id="body-title" className={recruitmentStyles.sectionTitle}>모집 소개</h2>
            <p className={recruitmentStyles.body}>{post.body}</p>
            <p className={recruitmentStyles.hint}>
              모집글의 내용은 작성 기업이 직접 입력한 것이며 GovBiz가 검증하지 않습니다. 공고 요건은 공식 원문에서 확인하세요.
            </p>
          </section>
        </div>
        <aside className="grid content-start gap-6">
          <section className={recruitmentStyles.section} aria-labelledby="company-title">
            <h2 id="company-title" className={recruitmentStyles.sectionTitle}>작성 기업</h2>
            <div className={recruitmentStyles.cardCompany}>
              <span className={recruitmentStyles.companyMark} aria-hidden="true">{post.company.companyName.slice(0, 1)}</span>
              <span>{post.company.companyName}</span>
            </div>
            <p className={recruitmentStyles.hint}>
              사업자등록번호 {post.company.businessNumber} · {post.company.businessStatus || '상태 정보 없음'} · 이 과제의 {recruitmentRoleLabels[post.ourRole]}
            </p>
          </section>
          <section className={recruitmentStyles.section} aria-labelledby="program-title">
            <h2 id="program-title" className={recruitmentStyles.sectionTitle}>연결된 공고</h2>
            {post.program ? (
              <div className={recruitmentStyles.programCard}>
                <span className={recruitmentStyles.programTag}>{programStatusLabels[post.program.status]}</span>
                <p className={recruitmentStyles.programTitle}>{post.program.title}</p>
                <p className={recruitmentStyles.programMeta}>
                  {post.program.organization}
                  <br />신청 기간 · {post.program.applicationPeriod}
                  <br />지원 대상 · {post.program.targetDescription}
                </p>
                <div className={recruitmentStyles.programLinks}>
                  <Link
                    className={recruitmentStyles.linkButton}
                    to={`/support-programs/detail?sourceCode=${encodeURIComponent(post.program.sourceCode)}&sourceProgramId=${encodeURIComponent(post.program.sourceProgramId)}`}
                  >
                    공고 상세 보기
                  </Link>
                  <a className={recruitmentStyles.linkButton} href={post.program.sourceUrl} target="_blank" rel="noreferrer">
                    공식 원문 보기 ↗
                  </a>
                </div>
              </div>
            ) : (
              <p className={recruitmentStyles.hint}>연결된 공고가 더 이상 공개되지 않아 모집이 종료되었습니다.</p>
            )}
          </section>
        </aside>
      </div>
    </main>
  )
}

function PostConditions({ post }: { post: RecruitmentPost }) {
  return (
    <div className={recruitmentStyles.conditionGrid}>
      <Condition label="우리 기업의 역할" value={recruitmentRoleLabels[post.ourRole]} />
      <Condition label="찾는 역할" value={`${recruitmentRoleLabels[post.wantedRole]} ${post.wantedCompanyCount}곳`} />
      <Condition label="희망 지역" value={post.wantedRegion || '무관'} />
      <Condition label="필요 역량" value={post.requiredCapabilities.length > 0 ? post.requiredCapabilities.join(', ') : '특별한 요건 없음'} />
      <Condition label="모집 마감일" value={post.closesOn} />
      <Condition label="받은 제안" value={`${post.proposalCount}건`} />
    </div>
  )
}

function Condition({ label, value }: { label: string; value: string }) {
  return (
    <div className={recruitmentStyles.conditionItem}>
      <p className={recruitmentStyles.conditionLabel}>{label}</p>
      <p className={recruitmentStyles.conditionValue}>{value}</p>
    </div>
  )
}

function UnavailablePost({ title, description }: { title: string; description: string }) {
  return (
    <main className={recruitmentStyles.narrowPage}>
      <Link className={recruitmentStyles.backLink} to="/partners">← 파트너 모집 목록</Link>
      <h1 className={recruitmentStyles.title}>{title}</h1>
      <p className={recruitmentStyles.description}>{description}</p>
    </main>
  )
}
