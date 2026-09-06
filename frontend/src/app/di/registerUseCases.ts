import { asFunction } from 'awilix/browser'

import { AskSupportProgramEvidenceQuestionUseCase } from '../../domain/usecases/AskSupportProgramEvidenceQuestionUseCase'
import { GetCurrentAccountUseCase } from '../../domain/usecases/GetCurrentAccountUseCase'
import { GetSupportProgramDetailUseCase } from '../../domain/usecases/GetSupportProgramDetailUseCase'
import { GetSupportProgramSearchReadinessUseCase } from '../../domain/usecases/GetSupportProgramSearchReadinessUseCase'
import { ListAdminAccountsUseCase } from '../../domain/usecases/ListAdminAccountsUseCase'
import { LogInUseCase } from '../../domain/usecases/LogInUseCase'
import { LogOutUseCase } from '../../domain/usecases/LogOutUseCase'
import { LookupBusinessUseCase } from '../../domain/usecases/LookupBusinessUseCase'
import { PrepareSampleItemUseCase } from '../../domain/usecases/PrepareSampleItemUseCase'
import { RevokeAccountSessionsUseCase } from '../../domain/usecases/RevokeAccountSessionsUseCase'
import { SearchSupportProgramsUseCase } from '../../domain/usecases/SearchSupportProgramsUseCase'
import { SignUpUseCase } from '../../domain/usecases/SignUpUseCase'
import type { AppContainer, AppCradle } from './types'

/** Domain UseCase와 UseCase가 필요로 하는 Repository 연결을 등록합니다. */
export function registerUseCases(container: AppContainer) {
  container.register({
    askSupportProgramEvidenceQuestionUseCase: asFunction(
      createAskSupportProgramEvidenceQuestionUseCase,
    ).singleton(),
    getCurrentAccountUseCase: asFunction(createGetCurrentAccountUseCase).singleton(),
    getSupportProgramDetailUseCase: asFunction(
      createGetSupportProgramDetailUseCase,
    ).singleton(),
    getSupportProgramSearchReadinessUseCase: asFunction(
      createGetSupportProgramSearchReadinessUseCase,
    ).singleton(),
    listAdminAccountsUseCase: asFunction(createListAdminAccountsUseCase).singleton(),
    logInUseCase: asFunction(createLogInUseCase).singleton(),
    logOutUseCase: asFunction(createLogOutUseCase).singleton(),
    lookupBusinessUseCase: asFunction(createLookupBusinessUseCase).singleton(),
    prepareSampleItemUseCase: asFunction(
      createPrepareSampleItemUseCase,
    ).singleton(),
    revokeAccountSessionsUseCase: asFunction(createRevokeAccountSessionsUseCase).singleton(),
    searchSupportProgramsUseCase: asFunction(
      createSearchSupportProgramsUseCase,
    ).singleton(),
    signUpUseCase: asFunction(createSignUpUseCase).singleton(),
  })
}

function createAskSupportProgramEvidenceQuestionUseCase({
  supportProgramRepository,
}: Pick<AppCradle, 'supportProgramRepository'>): AskSupportProgramEvidenceQuestionUseCase {
  return new AskSupportProgramEvidenceQuestionUseCase(supportProgramRepository)
}

function createGetCurrentAccountUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): GetCurrentAccountUseCase {
  return new GetCurrentAccountUseCase(accountRepository)
}

function createGetSupportProgramDetailUseCase({
  supportProgramRepository,
}: Pick<AppCradle, 'supportProgramRepository'>): GetSupportProgramDetailUseCase {
  return new GetSupportProgramDetailUseCase(supportProgramRepository)
}

function createGetSupportProgramSearchReadinessUseCase({
  supportProgramRepository,
}: Pick<AppCradle, 'supportProgramRepository'>): GetSupportProgramSearchReadinessUseCase {
  return new GetSupportProgramSearchReadinessUseCase(supportProgramRepository)
}

function createListAdminAccountsUseCase({
  adminRepository,
}: Pick<AppCradle, 'adminRepository'>): ListAdminAccountsUseCase {
  return new ListAdminAccountsUseCase(adminRepository)
}

function createRevokeAccountSessionsUseCase({
  adminRepository,
}: Pick<AppCradle, 'adminRepository'>): RevokeAccountSessionsUseCase {
  return new RevokeAccountSessionsUseCase(adminRepository)
}

function createLogInUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): LogInUseCase {
  return new LogInUseCase(accountRepository)
}

function createLogOutUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): LogOutUseCase {
  return new LogOutUseCase(accountRepository)
}

function createLookupBusinessUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): LookupBusinessUseCase {
  return new LookupBusinessUseCase(accountRepository)
}

function createPrepareSampleItemUseCase({
  sampleItemRepository,
}: Pick<AppCradle, 'sampleItemRepository'>): PrepareSampleItemUseCase {
  return new PrepareSampleItemUseCase(sampleItemRepository)
}

function createSearchSupportProgramsUseCase({
  supportProgramRepository,
}: Pick<AppCradle, 'supportProgramRepository'>): SearchSupportProgramsUseCase {
  return new SearchSupportProgramsUseCase(supportProgramRepository)
}

function createSignUpUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): SignUpUseCase {
  return new SignUpUseCase(accountRepository)
}
