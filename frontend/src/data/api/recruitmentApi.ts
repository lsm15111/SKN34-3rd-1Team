import type { RecruitmentPostDraft } from '../../domain/entities/RecruitmentPost'
import type { RecruitmentPostQuery } from '../../domain/repositories/RecruitmentRepository'
import type { SupportProgramIdentity } from '../../domain/repositories/SupportProgramRepository'
import { AccountApiError } from './accountApi'
import { getCoreApiBaseUrl } from './coreApiConfig'
import {
  recruitmentPostDtoSchema,
  recruitmentPostListDtoSchema,
  recruitmentPostPageDtoSchema,
  type RecruitmentPostDto,
  type RecruitmentPostPageDto,
} from '../models/RecruitmentPostDto'
import {
  recruitmentProposalDtoSchema,
  recruitmentProposalListDtoSchema,
  type RecruitmentProposalDto,
} from '../models/RecruitmentProposalDto'

const RECRUITMENT_POSTS_PATH = '/api/v1/recruitment-posts'
const RECRUITMENT_PROPOSALS_PATH = '/api/v1/recruitment-proposals'

/** 모집글 목록·상세는 로그인 없이도 조회하므로 토큰이 있을 때만 Bearer 헤더를 붙입니다. */
function headers(sessionToken: string | null, withBody = false): Record<string, string> {
  const result: Record<string, string> = { Accept: 'application/json' }
  if (withBody) result['Content-Type'] = 'application/json'
  if (sessionToken) result.Authorization = `Bearer ${sessionToken}`
  return result
}

export async function listRecruitmentPostsApi(
  sessionToken: string | null,
  query: RecruitmentPostQuery,
  signal?: AbortSignal,
): Promise<RecruitmentPostPageDto> {
  const searchParams = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.program) {
    searchParams.set('sourceCode', query.program.sourceCode)
    searchParams.set('sourceProgramId', query.program.sourceProgramId)
  }
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}?${searchParams.toString()}`, {
    headers: headers(sessionToken),
    signal,
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return recruitmentPostPageDtoSchema.parse(await response.json())
}

export async function getRecruitmentPostApi(
  sessionToken: string | null,
  postId: number,
  signal?: AbortSignal,
): Promise<RecruitmentPostDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}/${postId}`, {
    headers: headers(sessionToken),
    signal,
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return recruitmentPostDtoSchema.parse(await response.json())
}

export async function createRecruitmentPostApi(
  sessionToken: string,
  program: SupportProgramIdentity,
  draft: RecruitmentPostDraft,
  signal?: AbortSignal,
): Promise<RecruitmentPostDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}`, {
    method: 'POST',
    headers: headers(sessionToken, true),
    body: JSON.stringify({ sourceCode: program.sourceCode, sourceProgramId: program.sourceProgramId, post: draft }),
    signal,
  })
  await rejectFailedResponse(response)

  return recruitmentPostDtoSchema.parse(await response.json())
}

export async function updateRecruitmentPostApi(
  sessionToken: string,
  postId: number,
  draft: RecruitmentPostDraft,
  signal?: AbortSignal,
): Promise<RecruitmentPostDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}/${postId}`, {
    method: 'PUT',
    headers: headers(sessionToken, true),
    body: JSON.stringify({ post: draft }),
    signal,
  })
  await rejectFailedResponse(response)

  return recruitmentPostDtoSchema.parse(await response.json())
}

export async function closeRecruitmentPostApi(
  sessionToken: string,
  postId: number,
  signal?: AbortSignal,
): Promise<RecruitmentPostDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}/${postId}/close`, {
    method: 'POST',
    headers: headers(sessionToken),
    signal,
  })
  await rejectFailedResponse(response)

  return recruitmentPostDtoSchema.parse(await response.json())
}

export async function listMyRecruitmentPostsApi(
  sessionToken: string,
  signal?: AbortSignal,
): Promise<RecruitmentPostDto[]> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}/mine`, {
    headers: headers(sessionToken),
    signal,
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return recruitmentPostListDtoSchema.parse(await response.json()).items
}

export async function sendProposalApi(
  sessionToken: string,
  postId: number,
  message: string,
  signal?: AbortSignal,
): Promise<RecruitmentProposalDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}/${postId}/proposals`, {
    method: 'POST',
    headers: headers(sessionToken, true),
    body: JSON.stringify({ message }),
    signal,
  })
  await rejectFailedResponse(response)

  return recruitmentProposalDtoSchema.parse(await response.json())
}

export async function listReceivedProposalsApi(
  sessionToken: string,
  postId: number,
  signal?: AbortSignal,
): Promise<RecruitmentProposalDto[]> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_POSTS_PATH}/${postId}/proposals`, {
    headers: headers(sessionToken),
    signal,
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return recruitmentProposalListDtoSchema.parse(await response.json()).items
}

export async function listSentProposalsApi(
  sessionToken: string,
  signal?: AbortSignal,
): Promise<RecruitmentProposalDto[]> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_PROPOSALS_PATH}/sent`, {
    headers: headers(sessionToken),
    signal,
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return recruitmentProposalListDtoSchema.parse(await response.json()).items
}

export type ProposalAction = 'accept' | 'decline' | 'withdraw'

/** 수락·거절·철회는 같은 형태의 POST이며 결과로 바뀐 제안을 돌려받습니다. */
export async function decideProposalApi(
  sessionToken: string,
  proposalId: number,
  action: ProposalAction,
  signal?: AbortSignal,
): Promise<RecruitmentProposalDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${RECRUITMENT_PROPOSALS_PATH}/${proposalId}/${action}`, {
    method: 'POST',
    headers: headers(sessionToken),
    signal,
  })
  await rejectFailedResponse(response)

  return recruitmentProposalDtoSchema.parse(await response.json())
}

async function rejectFailedResponse(response: Response): Promise<void> {
  if (response.ok) return

  let code: string | null = null
  try {
    const payload: unknown = await response.json()
    if (typeof payload === 'object' && payload !== null && 'code' in payload) {
      const value = (payload as { code: unknown }).code
      code = typeof value === 'string' ? value : null
    }
  } catch {
    code = null
  }
  throw new AccountApiError(response.status, code)
}
