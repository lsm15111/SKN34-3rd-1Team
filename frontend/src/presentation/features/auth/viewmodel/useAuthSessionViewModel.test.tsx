// @vitest-environment jsdom

import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { Provider } from 'react-redux'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { createAppStore } from '../../../../app/store'
import { selectAuthStatus, sessionRestored } from '../state/authSlice'
import { useAuthSessionViewModel, useRestoreAuthSession } from './useAuthSessionViewModel'

afterEach(cleanup)

const account = {
  email: 'manager@company.co.kr',
  role: 'USER' as const,
  company: { businessNumber: '1248100998', companyName: '삼성전자(주)', businessStatus: '계속사업자' },
}

describe('useRestoreAuthSession', () => {
  it('restores the stored session once and marks the app authenticated', async () => {
    const store = createAppStore()
    const execute = vi.fn().mockResolvedValue(account)

    const { rerender } = renderHook(() => useRestoreAuthSession({ execute }), { wrapper: createWrapper(store) })

    await waitFor(() => expect(selectAuthStatus(store.getState())).toBe('authenticated'))
    rerender()
    expect(execute).toHaveBeenCalledOnce()
    expect(execute).toHaveBeenCalledWith(expect.any(AbortSignal))
  })

  it('falls back to anonymous when the restore request fails', async () => {
    const store = createAppStore()
    const execute = vi.fn().mockRejectedValue(new Error('network'))

    renderHook(() => useRestoreAuthSession({ execute }), { wrapper: createWrapper(store) })

    await waitFor(() => expect(selectAuthStatus(store.getState())).toBe('anonymous'))
  })

  it('does not call the use case again when the status is already known', () => {
    const store = createAppStore()
    store.dispatch(sessionRestored(null))
    const execute = vi.fn()

    renderHook(() => useRestoreAuthSession({ execute }), { wrapper: createWrapper(store) })

    expect(execute).not.toHaveBeenCalled()
  })
})

describe('useAuthSessionViewModel', () => {
  it('exposes the account and signs out even if the server logout fails', async () => {
    const store = createAppStore()
    store.dispatch(sessionRestored(account))
    const execute = vi.fn().mockRejectedValue(new Error('server down'))

    const { result } = renderHook(() => useAuthSessionViewModel({ execute }), { wrapper: createWrapper(store) })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.account).toEqual(account)

    await act(async () => {
      await result.current.logOut()
    })

    expect(execute).toHaveBeenCalledOnce()
    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.account).toBeNull()
  })
})

function createWrapper(store: ReturnType<typeof createAppStore>) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <Provider store={store}>{children}</Provider>
  }
}
