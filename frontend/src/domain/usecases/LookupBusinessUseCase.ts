import type {
  AccountRepository,
  BusinessLookupResult,
} from '../repositories/AccountRepository'

type BusinessLookupRepository = Pick<AccountRepository, 'lookupBusiness'>

/** 하이픈 등 구분 문자를 제거한 사업자등록번호로 국세청 등록 기업을 확인합니다. */
export class LookupBusinessUseCase {
  private readonly repository: BusinessLookupRepository

  constructor(repository: BusinessLookupRepository) {
    this.repository = repository
  }

  execute(businessNumber: string, signal?: AbortSignal): Promise<BusinessLookupResult> {
    return this.repository.lookupBusiness(normalizeBusinessNumber(businessNumber), signal)
  }
}

/** 화면 입력·API 요청·저장 값이 같은 표기를 쓰도록 숫자만 남깁니다. */
export function normalizeBusinessNumber(businessNumber: string): string {
  return businessNumber.replace(/\D/g, '')
}
