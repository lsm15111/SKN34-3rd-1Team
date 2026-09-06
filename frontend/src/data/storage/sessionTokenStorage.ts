/** 브라우저에 세션 토큰을 보관하는 경계입니다. 테스트는 메모리 구현으로 대체합니다. */
export type SessionTokenStorage = {
  read(): string | null
  write(sessionToken: string): void
  clear(): void
}

export const sessionTokenStorageKey = 'govbiz.sessionToken'

/**
 * localStorage 기반 저장소입니다. 새로고침 뒤에도 로그인 상태를 복원하지만 같은 origin의
 * 스크립트가 읽을 수 있으므로, 외부 스크립트를 넣지 않는 현재 앱 범위에서만 사용합니다.
 * 비공개 창·차단 설정에서는 접근 자체가 예외를 던지므로 모든 호출을 감쌉니다.
 */
export function createLocalSessionTokenStorage(): SessionTokenStorage {
  return {
    read() {
      try {
        return window.localStorage.getItem(sessionTokenStorageKey)
      } catch {
        return null
      }
    },
    write(sessionToken) {
      try {
        window.localStorage.setItem(sessionTokenStorageKey, sessionToken)
      } catch {
        // 저장에 실패하면 이번 세션 동안만 로그인 상태가 유지됩니다.
      }
    },
    clear() {
      try {
        window.localStorage.removeItem(sessionTokenStorageKey)
      } catch {
        // 지울 수 없는 저장소는 읽기도 실패하므로 남는 토큰이 없습니다.
      }
    },
  }
}

export function createMemorySessionTokenStorage(initialToken: string | null = null): SessionTokenStorage {
  let sessionToken = initialToken
  return {
    read: () => sessionToken,
    write(value) {
      sessionToken = value
    },
    clear() {
      sessionToken = null
    },
  }
}
