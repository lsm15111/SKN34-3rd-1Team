import type { AwilixContainer } from 'awilix/browser'

import type { CoreApiHealth } from '../../data/api/coreApiHealth'
import type { SessionTokenStorage } from '../../data/storage/sessionTokenStorage'
import type { AccountRepository } from '../../domain/repositories/AccountRepository'
import type { AdminRepository } from '../../domain/repositories/AdminRepository'
import type { SampleItemRepository } from '../../domain/repositories/SampleItemRepository'
import type { SupportProgramRepository } from '../../domain/repositories/SupportProgramRepository'
import type { AskSupportProgramEvidenceQuestionUseCase } from '../../domain/usecases/AskSupportProgramEvidenceQuestionUseCase'
import type { GetCurrentAccountUseCase } from '../../domain/usecases/GetCurrentAccountUseCase'
import type { GetSupportProgramDetailUseCase } from '../../domain/usecases/GetSupportProgramDetailUseCase'
import type { GetSupportProgramSearchReadinessUseCase } from '../../domain/usecases/GetSupportProgramSearchReadinessUseCase'
import type { ListAdminAccountsUseCase } from '../../domain/usecases/ListAdminAccountsUseCase'
import type { LogInUseCase } from '../../domain/usecases/LogInUseCase'
import type { LogOutUseCase } from '../../domain/usecases/LogOutUseCase'
import type { LookupBusinessUseCase } from '../../domain/usecases/LookupBusinessUseCase'
import type { PrepareSampleItemUseCase } from '../../domain/usecases/PrepareSampleItemUseCase'
import type { RevokeAccountSessionsUseCase } from '../../domain/usecases/RevokeAccountSessionsUseCase'
import type { SearchSupportProgramsUseCase } from '../../domain/usecases/SearchSupportProgramsUseCase'
import type { SignUpUseCase } from '../../domain/usecases/SignUpUseCase'

export type FetchCoreApiHealth = (signal?: AbortSignal) => Promise<CoreApiHealth>

/** Awilix가 생성·연결할 수 있는 전체 의존성 목록입니다. */
export type AppCradle = {
  accountRepository: AccountRepository
  adminRepository: AdminRepository
  askSupportProgramEvidenceQuestionUseCase: AskSupportProgramEvidenceQuestionUseCase
  fetchCoreApiHealth: FetchCoreApiHealth
  getCurrentAccountUseCase: GetCurrentAccountUseCase
  getSupportProgramDetailUseCase: GetSupportProgramDetailUseCase
  getSupportProgramSearchReadinessUseCase: GetSupportProgramSearchReadinessUseCase
  listAdminAccountsUseCase: ListAdminAccountsUseCase
  logInUseCase: LogInUseCase
  logOutUseCase: LogOutUseCase
  lookupBusinessUseCase: LookupBusinessUseCase
  prepareSampleItemUseCase: PrepareSampleItemUseCase
  revokeAccountSessionsUseCase: RevokeAccountSessionsUseCase
  sampleItemRepository: SampleItemRepository
  searchSupportProgramsUseCase: SearchSupportProgramsUseCase
  sessionTokenStorage: SessionTokenStorage
  signUpUseCase: SignUpUseCase
  supportProgramRepository: SupportProgramRepository
}

export type AppContainer = AwilixContainer<AppCradle>
