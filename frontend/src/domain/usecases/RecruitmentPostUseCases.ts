import type {
  RecruitmentPost,
  RecruitmentPostDraft,
  RecruitmentPostPage,
} from '../entities/RecruitmentPost'
import type {
  CloseRecruitmentPostResult,
  RecruitmentPostQuery,
  RecruitmentRepository,
  SaveRecruitmentPostResult,
} from '../repositories/RecruitmentRepository'
import type { SupportProgramIdentity } from '../repositories/SupportProgramRepository'

/** 모집 중인 글을 마감 임박순으로 한 페이지 조회합니다. */
export class ListRecruitmentPostsUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'listOpen'>

  constructor(repository: Pick<RecruitmentRepository, 'listOpen'>) {
    this.repository = repository
  }

  execute(query: RecruitmentPostQuery, signal?: AbortSignal): Promise<RecruitmentPostPage> {
    return this.repository.listOpen({ ...query, page: Math.max(0, query.page) }, signal)
  }
}

export class GetRecruitmentPostUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'get'>

  constructor(repository: Pick<RecruitmentRepository, 'get'>) {
    this.repository = repository
  }

  execute(postId: number, signal?: AbortSignal): Promise<RecruitmentPost | null> {
    return this.repository.get(postId, signal)
  }
}

/** 화면 입력을 서버 규칙과 같은 표기로 정리한 뒤 등록합니다. */
export class CreateRecruitmentPostUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'create'>

  constructor(repository: Pick<RecruitmentRepository, 'create'>) {
    this.repository = repository
  }

  execute(
    program: SupportProgramIdentity,
    draft: RecruitmentPostDraft,
    signal?: AbortSignal,
  ): Promise<SaveRecruitmentPostResult> {
    return this.repository.create(program, normalizeRecruitmentPostDraft(draft), signal)
  }
}

export class UpdateRecruitmentPostUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'update'>

  constructor(repository: Pick<RecruitmentRepository, 'update'>) {
    this.repository = repository
  }

  execute(postId: number, draft: RecruitmentPostDraft, signal?: AbortSignal): Promise<SaveRecruitmentPostResult> {
    return this.repository.update(postId, normalizeRecruitmentPostDraft(draft), signal)
  }
}

export class CloseRecruitmentPostUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'closeEarly'>

  constructor(repository: Pick<RecruitmentRepository, 'closeEarly'>) {
    this.repository = repository
  }

  execute(postId: number, signal?: AbortSignal): Promise<CloseRecruitmentPostResult> {
    return this.repository.closeEarly(postId, signal)
  }
}

export class ListMyRecruitmentPostsUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'listMine'>

  constructor(repository: Pick<RecruitmentRepository, 'listMine'>) {
    this.repository = repository
  }

  execute(signal?: AbortSignal): Promise<RecruitmentPost[]> {
    return this.repository.listMine(signal)
  }
}

/** 앞뒤 공백을 제거하고 역량 목록의 빈 값·중복을 걷어냅니다. 길이·개수 규칙은 폼과 서버가 검사합니다. */
export function normalizeRecruitmentPostDraft(draft: RecruitmentPostDraft): RecruitmentPostDraft {
  const capabilities = draft.requiredCapabilities.map((value) => value.trim()).filter((value) => value.length > 0)
  return {
    ...draft,
    title: draft.title.trim(),
    body: draft.body.trim(),
    wantedRegion: draft.wantedRegion.trim(),
    requiredCapabilities: Array.from(new Set(capabilities)),
  }
}
