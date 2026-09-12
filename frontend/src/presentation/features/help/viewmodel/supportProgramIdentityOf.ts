import type { SupportProgramIdentity } from '../../../../domain/repositories/SupportProgramRepository'

/** 지금 보고 있는 화면이 특정 공고라면 그 공고를 패널에서 이어서 물을 수 있게 합니다. */
export function supportProgramIdentityOf(search: string): SupportProgramIdentity | null {
  const parameters = new URLSearchParams(search)
  const sourceCode = parameters.get('sourceCode')
  const sourceProgramId = parameters.get('sourceProgramId')
  return sourceCode && sourceProgramId ? { sourceCode, sourceProgramId } : null
}
