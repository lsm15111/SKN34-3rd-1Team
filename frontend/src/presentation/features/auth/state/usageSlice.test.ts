import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../../app/store'
import { createMemoryAnonymousUsageStorage } from '../../../../data/storage/anonymousUsageStorage'
import { sessionRestored, signedIn } from './authSlice'
import {
  anonymousSearchCompleted,
  anonymousSearchLimit,
  selectIsAnonymousSearchLimitReached,
  selectRemainingAnonymousSearches,
  usageReset,
  usageRestored,
} from './usageSlice'

const account = {
  email: 'manager@company.co.kr',
  role: 'USER' as const,
  company: { businessNumber: '1248100998', companyName: '삼성전자(주)', businessStatus: '계속사업자' },
}

describe('usageSlice', () => {
  it('counts anonymous searches down to the limit', () => {
    const store = createAppStore()

    expect(selectRemainingAnonymousSearches(store.getState())).toBe(anonymousSearchLimit)
    for (let index = 0; index < anonymousSearchLimit; index += 1) {
      store.dispatch(anonymousSearchCompleted())
    }

    expect(selectRemainingAnonymousSearches(store.getState())).toBe(0)
    expect(selectIsAnonymousSearchLimitReached(store.getState())).toBe(true)

    store.dispatch(usageReset())
    expect(selectIsAnonymousSearchLimitReached(store.getState())).toBe(false)
  })

  it('resets the count when the user signs in or a stored session is restored', () => {
    const store = createAppStore()
    store.dispatch(usageRestored(anonymousSearchLimit))

    store.dispatch(sessionRestored(null))
    expect(selectIsAnonymousSearchLimitReached(store.getState())).toBe(true)

    store.dispatch(sessionRestored(account))
    expect(selectRemainingAnonymousSearches(store.getState())).toBe(anonymousSearchLimit)

    store.dispatch(usageRestored(2))
    store.dispatch(signedIn(account))
    expect(selectRemainingAnonymousSearches(store.getState())).toBe(anonymousSearchLimit)
  })

  it('starts from the persisted count and writes changes back to the storage', () => {
    const storage = createMemoryAnonymousUsageStorage(2)
    const store = createAppStore({ anonymousUsageStorage: storage })

    expect(selectRemainingAnonymousSearches(store.getState())).toBe(1)

    store.dispatch(anonymousSearchCompleted())
    expect(storage.read()).toBe(3)

    store.dispatch(signedIn(account))
    expect(storage.read()).toBe(0)
  })

  it('treats a corrupted persisted value as zero', () => {
    const storage = createMemoryAnonymousUsageStorage(-5)

    expect(storage.read()).toBe(0)
    storage.write(Number.NaN)
    expect(storage.read()).toBe(0)
  })
})
