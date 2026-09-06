// @vitest-environment jsdom

import type { ReactNode } from 'react'
import { Provider } from 'react-redux'
import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { createAppStore } from '../../../../app/store'
import { supportPrograms } from '../../../../data/fixtures/supportPrograms'
import { sessionRestored } from '../../auth/state/authSlice'
import { anonymousSearchLimit, usageRestored } from '../../auth/state/usageSlice'
import { draftChanged } from '../state/chatSlice'
import { useSupportProgramChatViewModel } from './useSupportProgramChatViewModel'

afterEach(cleanup)

const account = {
  email: 'manager@company.co.kr',
  role: 'USER' as const,
  company: { businessNumber: '1248100998', companyName: '삼성전자(주)', businessStatus: '계속사업자' },
}

describe('anonymous search limit in the chat view model', () => {
  it('opens the auth gate instead of searching once an anonymous user reaches the limit', async () => {
    const execute = vi.fn().mockResolvedValue({ query: '수출', programs: [supportPrograms[3]] })
    const store = createAppStore()
    store.dispatch(sessionRestored(null))
    const { result } = renderHook(() => useSupportProgramChatViewModel({ execute }), {
      wrapper: createWrapper(store),
    })

    for (let index = 0; index < anonymousSearchLimit; index += 1) {
      act(() => store.dispatch(draftChanged(`검색 ${index}`)))
      await act(async () => result.current.submitMessage())
    }

    expect(execute).toHaveBeenCalledTimes(anonymousSearchLimit)
    expect(result.current.remainingAnonymousSearches).toBe(0)
    expect(result.current.isAuthGateOpen).toBe(false)

    act(() => store.dispatch(draftChanged('네 번째 검색')))
    await act(async () => result.current.submitMessage())

    expect(execute).toHaveBeenCalledTimes(anonymousSearchLimit)
    expect(result.current.isAuthGateOpen).toBe(true)
    expect(store.getState().chat.draft).toBe('네 번째 검색')

    act(() => result.current.closeAuthGate())
    expect(result.current.isAuthGateOpen).toBe(false)
  })

  it('does not count or limit searches for an authenticated user', async () => {
    const execute = vi.fn().mockResolvedValue({ query: '수출', programs: [] })
    const store = createAppStore()
    store.dispatch(usageRestored(anonymousSearchLimit))
    store.dispatch(sessionRestored(account))
    const { result } = renderHook(() => useSupportProgramChatViewModel({ execute }), {
      wrapper: createWrapper(store),
    })

    act(() => store.dispatch(draftChanged('로그인 사용자 검색')))
    await act(async () => result.current.submitMessage())

    expect(execute).toHaveBeenCalledOnce()
    expect(result.current.isAuthGateOpen).toBe(false)
    expect(result.current.remainingAnonymousSearches).toBeNull()
    expect(store.getState().usage.anonymousSearchCount).toBe(0)
  })

  it('hides the remaining count until the session status is known', () => {
    const store = createAppStore()
    const { result } = renderHook(() => useSupportProgramChatViewModel({ execute: vi.fn() }), {
      wrapper: createWrapper(store),
    })

    expect(result.current.remainingAnonymousSearches).toBeNull()

    act(() => store.dispatch(sessionRestored(null)))
    expect(result.current.remainingAnonymousSearches).toBe(anonymousSearchLimit)
  })
})

function createWrapper(store: ReturnType<typeof createAppStore>) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <Provider store={store}>{children}</Provider>
  }
}
