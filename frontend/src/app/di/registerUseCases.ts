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
import {
  CloseRecruitmentPostUseCase,
  CreateRecruitmentPostUseCase,
  GetRecruitmentPostUseCase,
  ListMyRecruitmentPostsUseCase,
  ListRecruitmentPostsUseCase,
  UpdateRecruitmentPostUseCase,
} from '../../domain/usecases/RecruitmentPostUseCases'
import {
  DecideProposalUseCase,
  ListReceivedProposalsUseCase,
  ListSentProposalsUseCase,
  SendProposalUseCase,
  WithdrawProposalUseCase,
} from '../../domain/usecases/RecruitmentProposalUseCases'
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
    closeRecruitmentPostUseCase: asFunction(createCloseRecruitmentPostUseCase).singleton(),
    createRecruitmentPostUseCase: asFunction(createCreateRecruitmentPostUseCase).singleton(),
    decideProposalUseCase: asFunction(createDecideProposalUseCase).singleton(),
    getCurrentAccountUseCase: asFunction(createGetCurrentAccountUseCase).singleton(),
    getRecruitmentPostUseCase: asFunction(createGetRecruitmentPostUseCase).singleton(),
    getSupportProgramDetailUseCase: asFunction(
      createGetSupportProgramDetailUseCase,
    ).singleton(),
    getSupportProgramSearchReadinessUseCase: asFunction(
      createGetSupportProgramSearchReadinessUseCase,
    ).singleton(),
    listAdminAccountsUseCase: asFunction(createListAdminAccountsUseCase).singleton(),
    listMyRecruitmentPostsUseCase: asFunction(createListMyRecruitmentPostsUseCase).singleton(),
    listReceivedProposalsUseCase: asFunction(createListReceivedProposalsUseCase).singleton(),
    listRecruitmentPostsUseCase: asFunction(createListRecruitmentPostsUseCase).singleton(),
    listSentProposalsUseCase: asFunction(createListSentProposalsUseCase).singleton(),
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
    sendProposalUseCase: asFunction(createSendProposalUseCase).singleton(),
    updateRecruitmentPostUseCase: asFunction(createUpdateRecruitmentPostUseCase).singleton(),
    withdrawProposalUseCase: asFunction(createWithdrawProposalUseCase).singleton(),
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

function createListRecruitmentPostsUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): ListRecruitmentPostsUseCase {
  return new ListRecruitmentPostsUseCase(recruitmentRepository)
}

function createGetRecruitmentPostUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): GetRecruitmentPostUseCase {
  return new GetRecruitmentPostUseCase(recruitmentRepository)
}

function createCreateRecruitmentPostUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): CreateRecruitmentPostUseCase {
  return new CreateRecruitmentPostUseCase(recruitmentRepository)
}

function createUpdateRecruitmentPostUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): UpdateRecruitmentPostUseCase {
  return new UpdateRecruitmentPostUseCase(recruitmentRepository)
}

function createCloseRecruitmentPostUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): CloseRecruitmentPostUseCase {
  return new CloseRecruitmentPostUseCase(recruitmentRepository)
}

function createSendProposalUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): SendProposalUseCase {
  return new SendProposalUseCase(recruitmentRepository)
}

function createListReceivedProposalsUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): ListReceivedProposalsUseCase {
  return new ListReceivedProposalsUseCase(recruitmentRepository)
}

function createListSentProposalsUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): ListSentProposalsUseCase {
  return new ListSentProposalsUseCase(recruitmentRepository)
}

function createDecideProposalUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): DecideProposalUseCase {
  return new DecideProposalUseCase(recruitmentRepository)
}

function createWithdrawProposalUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): WithdrawProposalUseCase {
  return new WithdrawProposalUseCase(recruitmentRepository)
}

function createListMyRecruitmentPostsUseCase({
  recruitmentRepository,
}: Pick<AppCradle, 'recruitmentRepository'>): ListMyRecruitmentPostsUseCase {
  return new ListMyRecruitmentPostsUseCase(recruitmentRepository)
}
