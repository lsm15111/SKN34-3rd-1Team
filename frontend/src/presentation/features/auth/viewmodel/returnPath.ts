/** 로그인·가입 화면이 성공 뒤 돌아갈 경로입니다. 앱 안의 경로만 허용해 외부 이동을 막습니다. */
export function readReturnPath(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const from = (state as { from: unknown }).from
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) return from
  }
  return '/'
}
