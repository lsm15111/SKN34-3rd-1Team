import { configureStore } from '@reduxjs/toolkit'

import type { AnonymousUsageStorage } from '../data/storage/anonymousUsageStorage'
import authReducer from '../presentation/features/auth/state/authSlice'
import usageReducer from '../presentation/features/auth/state/usageSlice'
import chatReducer from '../presentation/features/chat/state/chatSlice'
import sampleItemReducer from '../presentation/features/sample-item/state/sampleItemSlice'

export type CreateAppStoreOptions = {
  /** 지정하면 비로그인 검색 횟수를 이 저장소에서 읽어 시작하고 바뀔 때마다 기록합니다. */
  anonymousUsageStorage?: AnonymousUsageStorage
}

export function createAppStore({ anonymousUsageStorage }: CreateAppStoreOptions = {}) {
  const store = configureStore({
    reducer: {
      chat: chatReducer,
      auth: authReducer,
      usage: usageReducer,
      sampleItem: sampleItemReducer,
    },
    preloadedState: anonymousUsageStorage
      ? { usage: { anonymousSearchCount: anonymousUsageStorage.read() } }
      : undefined,
  })

  if (anonymousUsageStorage) {
    let persistedCount = store.getState().usage.anonymousSearchCount
    store.subscribe(() => {
      const count = store.getState().usage.anonymousSearchCount
      if (count === persistedCount) return
      persistedCount = count
      anonymousUsageStorage.write(count)
    })
  }

  return store
}

export type AppStore = ReturnType<typeof createAppStore>
export type RootState = ReturnType<AppStore['getState']>
export type AppDispatch = AppStore['dispatch']
