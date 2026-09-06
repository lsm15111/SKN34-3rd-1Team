import type {
  AccountLogIn,
  AccountRepository,
  LogInResult,
} from '../repositories/AccountRepository'
import { normalizeEmail } from './SignUpUseCase'

type LogInRepository = Pick<AccountRepository, 'logIn'>

/** 정규화한 이메일과 입력한 비밀번호 그대로 로그인을 요청합니다. */
export class LogInUseCase {
  private readonly repository: LogInRepository

  constructor(repository: LogInRepository) {
    this.repository = repository
  }

  execute(command: AccountLogIn, signal?: AbortSignal): Promise<LogInResult> {
    return this.repository.logIn({
      email: normalizeEmail(command.email),
      password: command.password,
    }, signal)
  }
}
