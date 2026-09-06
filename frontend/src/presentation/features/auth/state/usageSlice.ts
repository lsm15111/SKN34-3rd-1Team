import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

import type { RootState } from '../../../../app/store'
import { sessionRestored, signedIn } from './authSlice'

/** 로그인 없이 쓸 수 있는 검색 횟수입니다. 브라우저 저장 기준이며 서버는 제한하지 않습니다. */
export const anonymousSearchLimit = 3

type UsageState = {
  anonymousSearchCount: number
}

const initialState: UsageState = {
  anonymousSearchCount: 0,
}

const usageSlice = createSlice({
  name: 'usage',
  initialState,
  reducers: {
    anonymousSearchCompleted(state) {
      state.anonymousSearchCount += 1
    },
    usageRestored(state, action: PayloadAction<number>) {
      state.anonymousSearchCount = Math.max(0, action.payload)
    },
    usageReset(state) {
      state.anonymousSearchCount = 0
    },
  },
  extraReducers: (builder) => {
    // 로그인·가입하거나 저장된 세션이 복원되면 비로그인 횟수는 더 이상 의미가 없습니다.
    builder
      .addCase(signedIn, (state) => {
        state.anonymousSearchCount = 0
      })
      .addCase(sessionRestored, (state, action) => {
        if (action.payload) state.anonymousSearchCount = 0
      })
  },
})

export const { anonymousSearchCompleted, usageRestored, usageReset } = usageSlice.actions

export const selectAnonymousSearchCount = (state: RootState) => state.usage.anonymousSearchCount
export const selectRemainingAnonymousSearches = (state: RootState) =>
  Math.max(0, anonymousSearchLimit - state.usage.anonymousSearchCount)
export const selectIsAnonymousSearchLimitReached = (state: RootState) =>
  state.usage.anonymousSearchCount >= anonymousSearchLimit

export default usageSlice.reducer
