import type {
  RecruitmentPost,
  RecruitmentPostDraft,
  RecruitmentPostPage,
} from '../entities/RecruitmentPost'
import type { SupportProgramIdentity } from './SupportProgramRepository'

export type RecruitmentPostQuery = {
  program?: SupportProgramIdentity
  page: number
  size: number
}

/** 등록·수정 실패 사유는 화면이 다른 안내를 보여야 하므로 예외가 아닌 결과로 구분합니다. */
export type SaveRecruitmentPostResult =
  | { outcome: 'saved'; post: RecruitmentPost }
  | { outcome: 'program-not-open' }
  | { outcome: 'closes-on-invalid' }
  | { outcome: 'contact-in-text' }
  | { outcome: 'not-owner' }
  | { outcome: 'not-open' }
  | { outcome: 'not-found' }

export type CloseRecruitmentPostResult =
  | { outcome: 'closed'; post: RecruitmentPost }
  | { outcome: 'not-owner' }
  | { outcome: 'not-open' }
  | { outcome: 'not-found' }

/** 파트너 모집 화면이 Data Layer의 HTTP 세부사항과 분리되도록 하는 Domain 포트입니다. */
export interface RecruitmentRepository {
  listOpen(query: RecruitmentPostQuery, signal?: AbortSignal): Promise<RecruitmentPostPage>
  /** 없거나 숨김·종료되어 볼 수 없는 글이면 null입니다. */
  get(postId: number, signal?: AbortSignal): Promise<RecruitmentPost | null>
  create(
    program: SupportProgramIdentity,
    draft: RecruitmentPostDraft,
    signal?: AbortSignal,
  ): Promise<SaveRecruitmentPostResult>
  update(postId: number, draft: RecruitmentPostDraft, signal?: AbortSignal): Promise<SaveRecruitmentPostResult>
  closeEarly(postId: number, signal?: AbortSignal): Promise<CloseRecruitmentPostResult>
  listMine(signal?: AbortSignal): Promise<RecruitmentPost[]>
}
