/** 도움말 답변을 받지 못한 이유입니다. 요청량 한도와 그 밖의 실패를 화면이 다르게 안내합니다. */
export class HelpAnswerError extends Error {
  readonly reason: 'rate-limited' | 'unavailable'

  constructor(reason: 'rate-limited' | 'unavailable') {
    super(`help answer failed: ${reason}`)
    this.name = 'HelpAnswerError'
    this.reason = reason
  }
}
