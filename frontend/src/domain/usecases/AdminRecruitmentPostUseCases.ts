import type { AdminRecruitmentPostPage } from '../entities/AdminRecruitmentPost'
import type {
  AdminRecruitmentPostQuery,
  AdminRepository,
  ModerateRecruitmentPostResult,
} from '../repositories/AdminRepository'

/** 운영자가 모든 상태의 모집글을 한 페이지 조회합니다. */
export class ListAdminRecruitmentPostsUseCase {
  private readonly repository: Pick<AdminRepository, 'listRecruitmentPosts'>

  constructor(repository: Pick<AdminRepository, 'listRecruitmentPosts'>) {
    this.repository = repository
  }

  execute(query: AdminRecruitmentPostQuery, signal?: AbortSignal): Promise<AdminRecruitmentPostPage> {
    return this.repository.listRecruitmentPosts({ ...query, page: Math.max(0, query.page) }, signal)
  }
}

/** 사유를 정리해 모집글을 숨깁니다. 사유 길이 규칙은 폼과 서버가 검사합니다. */
export class HideRecruitmentPostUseCase {
  private readonly repository: Pick<AdminRepository, 'hideRecruitmentPost'>

  constructor(repository: Pick<AdminRepository, 'hideRecruitmentPost'>) {
    this.repository = repository
  }

  execute(postId: number, reason: string, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult> {
    return this.repository.hideRecruitmentPost(postId, reason.trim(), signal)
  }
}

export class UnhideRecruitmentPostUseCase {
  private readonly repository: Pick<AdminRepository, 'unhideRecruitmentPost'>

  constructor(repository: Pick<AdminRepository, 'unhideRecruitmentPost'>) {
    this.repository = repository
  }

  execute(postId: number, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult> {
    return this.repository.unhideRecruitmentPost(postId, signal)
  }
}

/** 운영자 강제 마감입니다. 사유는 서버 로그에만 남습니다. */
export class CloseRecruitmentPostByAdminUseCase {
  private readonly repository: Pick<AdminRepository, 'closeRecruitmentPost'>

  constructor(repository: Pick<AdminRepository, 'closeRecruitmentPost'>) {
    this.repository = repository
  }

  execute(postId: number, reason: string, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult> {
    return this.repository.closeRecruitmentPost(postId, reason.trim(), signal)
  }
}
