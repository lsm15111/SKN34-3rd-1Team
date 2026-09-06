import { Link } from 'react-router'

import {
  recruitmentPostStatusLabels,
  recruitmentRoleLabels,
  type RecruitmentPost,
} from '../../../../domain/entities/RecruitmentPost'
import { formatDeadline } from '../viewmodel/recruitmentDates'
import { recruitmentStatusClassName, recruitmentStyles } from './Recruitment.styles'

/** 목록·내 모집글이 함께 쓰는 모집글 카드입니다. 내 글은 점선 테두리로 구분합니다. */
export function RecruitmentPostCard({ post }: { post: RecruitmentPost }) {
  return (
    <article className={`${recruitmentStyles.card} ${post.viewer.isOwner ? recruitmentStyles.cardOwn : ''}`}>
      <div className={recruitmentStyles.cardTop}>
        <span className={recruitmentStyles.programTag}>
          {post.program ? '기업마당 공고' : '공고 종료'}
        </span>
        <span className={post.status === 'OPEN' ? recruitmentStyles.deadline : recruitmentStatusClassName(post.status)}>
          {post.status === 'OPEN' ? formatDeadline(post.closesOn) : recruitmentPostStatusLabels[post.status]}
        </span>
      </div>
      <h2 className={recruitmentStyles.cardTitle}>
        <Link className={recruitmentStyles.cardTitleLink} to={`/partners/${post.id}`}>{post.title}</Link>
      </h2>
      <p className={recruitmentStyles.cardProgram}>
        {post.program
          ? `${post.program.title} · ${post.program.organization}${post.program.applicationEndDate ? ` · 공고 마감 ${post.program.applicationEndDate}` : ''}`
          : '연결된 공고가 더 이상 공개되지 않습니다.'}
      </p>
      <div className={recruitmentStyles.cardCompany}>
        <span className={recruitmentStyles.companyMark} aria-hidden="true">{post.company.companyName.slice(0, 1)}</span>
        <span>{post.company.companyName}</span>
        <span className={recruitmentStyles.chip}>{recruitmentRoleLabels[post.ourRole]}</span>
        {post.viewer.isOwner ? <span className={recruitmentStyles.chipAccent}>내 모집글</span> : null}
      </div>
      <div className={recruitmentStyles.chipRow}>
        <span className={recruitmentStyles.chip}>찾는 역할 · {recruitmentRoleLabels[post.wantedRole]} {post.wantedCompanyCount}곳</span>
        {post.wantedRegion ? <span className={recruitmentStyles.chip}>지역 · {post.wantedRegion}</span> : null}
        {post.requiredCapabilities.length > 0
          ? <span className={recruitmentStyles.chip}>역량 · {post.requiredCapabilities.join(', ')}</span>
          : null}
      </div>
      <div className={recruitmentStyles.cardFooter}>
        <span>제안 {post.proposalCount}건</span>
        <Link className={recruitmentStyles.linkButton} to={`/partners/${post.id}`}>자세히 보기</Link>
      </div>
    </article>
  )
}
