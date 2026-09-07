import type { AdminAccountQuery, AdminRecruitmentPostQuery } from '../../domain/repositories/AdminRepository'
import { AccountApiError } from './accountApi'
import { getCoreApiBaseUrl } from './coreApiConfig'
import {
  adminAccountPageDtoSchema,
  adminRecruitmentPostDtoSchema,
  adminRecruitmentPostPageDtoSchema,
  type AdminAccountPageDto,
  type AdminRecruitmentPostDto,
  type AdminRecruitmentPostPageDto,
} from '../models/AdminDto'

const ADMIN_ACCOUNTS_PATH = '/api/v1/admin/accounts'
const ADMIN_RECRUITMENT_POSTS_PATH = '/api/v1/admin/recruitment-posts'

/** 관리자 세션 토큰으로 회원 목록 한 페이지를 조회합니다. */
export async function listAdminAccountsApi(
  sessionToken: string,
  query: AdminAccountQuery,
  signal?: AbortSignal,
): Promise<AdminAccountPageDto> {
  const searchParams = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.email) searchParams.set('email', query.email)
  const response = await fetch(`${getCoreApiBaseUrl()}${ADMIN_ACCOUNTS_PATH}?${searchParams.toString()}`, {
    headers: { Accept: 'application/json', Authorization: `Bearer ${sessionToken}` },
    signal,
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return adminAccountPageDtoSchema.parse(await response.json())
}

export async function revokeAccountSessionsApi(
  sessionToken: string,
  accountId: number,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${getCoreApiBaseUrl()}${ADMIN_ACCOUNTS_PATH}/${accountId}/sessions/revoke`, {
    method: 'POST',
    headers: { Accept: 'application/json', Authorization: `Bearer ${sessionToken}` },
    signal,
  })
  await rejectFailedResponse(response)
}

export async function listAdminRecruitmentPostsApi(
  sessionToken: string,
  query: AdminRecruitmentPostQuery,
  signal?: AbortSignal,
): Promise<AdminRecruitmentPostPageDto> {
  const searchParams = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.status) searchParams.set('status', query.status)
  const response = await fetch(`${getCoreApiBaseUrl()}${ADMIN_RECRUITMENT_POSTS_PATH}?${searchParams.toString()}`, {
    headers: { Accept: 'application/json', Authorization: `Bearer ${sessionToken}` },
    signal,
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return adminRecruitmentPostPageDtoSchema.parse(await response.json())
}

export type AdminPostModeration = 'hide' | 'unhide' | 'close'

/** 숨김·마감은 사유 본문을, 해제는 본문 없이 보냅니다. 결과로 바뀐 모집글을 돌려받습니다. */
export async function moderateRecruitmentPostApi(
  sessionToken: string,
  postId: number,
  action: AdminPostModeration,
  reason: string | null,
  signal?: AbortSignal,
): Promise<AdminRecruitmentPostDto> {
  const headers: Record<string, string> = { Accept: 'application/json', Authorization: `Bearer ${sessionToken}` }
  if (reason !== null) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${getCoreApiBaseUrl()}${ADMIN_RECRUITMENT_POSTS_PATH}/${postId}/${action}`, {
    method: 'POST',
    headers,
    body: reason !== null ? JSON.stringify({ reason }) : undefined,
    signal,
  })
  await rejectFailedResponse(response)

  return adminRecruitmentPostDtoSchema.parse(await response.json())
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
