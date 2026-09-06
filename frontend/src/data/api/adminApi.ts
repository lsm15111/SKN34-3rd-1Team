import type { AdminAccountQuery } from '../../domain/repositories/AdminRepository'
import { AccountApiError } from './accountApi'
import { getCoreApiBaseUrl } from './coreApiConfig'
import { adminAccountPageDtoSchema, type AdminAccountPageDto } from '../models/AdminDto'

const ADMIN_ACCOUNTS_PATH = '/api/v1/admin/accounts'

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
