import type {
  AccountRepository,
  AccountSignUp,
  SignUpResult,
} from '../repositories/AccountRepository'
import { normalizeBusinessNumber } from './LookupBusinessUseCase'

type SignUpRepository = Pick<AccountRepository, 'signUp'>

/** 이메일은 소문자, 사업자등록번호는 숫자만으로 정규화해 가입을 요청합니다. */
export class SignUpUseCase {
  private readonly repository: SignUpRepository

  constructor(repository: SignUpRepository) {
    this.repository = repository
  }

  execute(command: AccountSignUp, signal?: AbortSignal): Promise<SignUpResult> {
    return this.repository.signUp({
      email: normalizeEmail(command.email),
      password: command.password,
      businessNumber: normalizeBusinessNumber(command.businessNumber),
    }, signal)
  }
}

/** Core API와 같은 규칙(앞뒤 공백 제거·소문자)으로 이메일을 맞춥니다. */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}
