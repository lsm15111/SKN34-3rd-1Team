/** 비로그인 검색 횟수를 브라우저에 보관하는 경계입니다. 서버 측 제한이 아니므로 안내 목적으로만 씁니다. */
export type AnonymousUsageStorage = {
  read(): number
  write(count: number): void
}

export const anonymousUsageStorageKey = 'govbiz.anonymousSearchCount'

/** localStorage 기반 저장소입니다. 값이 없거나 손상됐으면 0으로 시작하고, 접근 예외는 모두 삼킵니다. */
export function createLocalAnonymousUsageStorage(): AnonymousUsageStorage {
  return {
    read() {
      try {
        return normalizeCount(window.localStorage.getItem(anonymousUsageStorageKey))
      } catch {
        return 0
      }
    },
    write(count) {
      try {
        window.localStorage.setItem(anonymousUsageStorageKey, String(normalizeCount(String(count))))
      } catch {
        // 저장에 실패하면 이번 세션 동안만 횟수가 유지됩니다.
      }
    },
  }
}

export function createMemoryAnonymousUsageStorage(initialCount = 0): AnonymousUsageStorage {
  let count = normalizeCount(String(initialCount))
  return {
    read: () => count,
    write(value) {
      count = normalizeCount(String(value))
    },
  }
}

function normalizeCount(value: string | null): number {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : 0
}
