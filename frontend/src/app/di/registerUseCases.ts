import { asFunction } from 'awilix/browser'
import { BrowseSupportProgramsUseCase } from '../../domain/usecases/BrowseSupportProgramsUseCase'

import { AskSupportProgramEvidenceQuestionUseCase } from '../../domain/usecases/AskSupportProgramEvidenceQuestionUseCase'
import {
  GetMyCompanyUseCase,
  LookupBusinessUseCase,
  RegisterCompanyUseCase,
  UpdateCompanyUseCase,
} from '../../domain/usecases/CompanyUseCases'
import {
  BrowsePartnerRecruitmentsUseCase,
  CreatePartnerRecruitmentUseCase,
  GetPartnerRecruitmentDetailUseCase,
} from '../../domain/usecases/PartnerRecruitmentUseCases'
import {
  BrowsePartnerProposalsUseCase,
  RespondPartnerProposalUseCase,
  SendPartnerProposalUseCase,
} from '../../domain/usecases/PartnerProposalUseCases'
import { DevLogInUseCase } from '../../domain/usecases/DevLogInUseCase'
import { GetCurrentAccountUseCase } from '../../domain/usecases/GetCurrentAccountUseCase'
import { GetSupportProgramDetailUseCase } from '../../domain/usecases/GetSupportProgramDetailUseCase'
import { GetSupportProgramSearchReadinessUseCase } from '../../domain/usecases/GetSupportProgramSearchReadinessUseCase'
import { LogInUseCase } from '../../domain/usecases/LogInUseCase'
import {
  CompleteOAuthLogInUseCase,
  GetOAuthProvidersUseCase,
  StartOAuthLogInUseCase,
} from '../../domain/usecases/OAuthLogInUseCases'
import { LogOutUseCase } from '../../domain/usecases/LogOutUseCase'
import { PrepareSampleItemUseCase } from '../../domain/usecases/PrepareSampleItemUseCase'
import { SearchSupportProgramsUseCase } from '../../domain/usecases/SearchSupportProgramsUseCase'
import { SignUpUseCase } from '../../domain/usecases/SignUpUseCase'
import { InterpretSupportProgramConversationUseCase } from '../../domain/usecases/InterpretSupportProgramConversationUseCase'
import type { AppContainer, AppCradle } from './types'

/** Domain UseCase와 UseCase가 필요로 하는 Repository 연결을 등록합니다. */
export function registerUseCases(container: AppContainer) {
  container.register({
    browseSupportProgramsUseCase: asFunction(
      ({ supportProgramRepository }: Pick<AppCradle, 'supportProgramRepository'>) => new BrowseSupportProgramsUseCase(supportProgramRepository),
    ).singleton(),
    interpretSupportProgramConversationUseCase: asFunction(
      ({ supportProgramRepository }: Pick<AppCradle, 'supportProgramRepository'>) => new InterpretSupportProgramConversationUseCase(supportProgramRepository),
    ).singleton(),
    askSupportProgramEvidenceQuestionUseCase: asFunction(
      createAskSupportProgramEvidenceQuestionUseCase,
    ).singleton(),
    devLogInUseCase: asFunction(createDevLogInUseCase).singleton(),
    browsePartnerProposalsUseCase: asFunction(
      ({ partnerProposalRepository }: Pick<AppCradle, 'partnerProposalRepository'>) => new BrowsePartnerProposalsUseCase(partnerProposalRepository),
    ).singleton(),
    respondPartnerProposalUseCase: asFunction(
      ({ partnerProposalRepository }: Pick<AppCradle, 'partnerProposalRepository'>) => new RespondPartnerProposalUseCase(partnerProposalRepository),
    ).singleton(),
    sendPartnerProposalUseCase: asFunction(
      ({ partnerProposalRepository }: Pick<AppCradle, 'partnerProposalRepository'>) => new SendPartnerProposalUseCase(partnerProposalRepository),
    ).singleton(),
    browsePartnerRecruitmentsUseCase: asFunction(
      ({ partnerRecruitmentRepository }: Pick<AppCradle, 'partnerRecruitmentRepository'>) => new BrowsePartnerRecruitmentsUseCase(partnerRecruitmentRepository),
    ).singleton(),
    createPartnerRecruitmentUseCase: asFunction(
      ({ partnerRecruitmentRepository }: Pick<AppCradle, 'partnerRecruitmentRepository'>) => new CreatePartnerRecruitmentUseCase(partnerRecruitmentRepository),
    ).singleton(),
    getPartnerRecruitmentDetailUseCase: asFunction(
      ({ partnerRecruitmentRepository }: Pick<AppCradle, 'partnerRecruitmentRepository'>) => new GetPartnerRecruitmentDetailUseCase(partnerRecruitmentRepository),
    ).singleton(),
    getMyCompanyUseCase: asFunction(
      ({ companyRepository }: Pick<AppCradle, 'companyRepository'>) => new GetMyCompanyUseCase(companyRepository),
    ).singleton(),
    lookupBusinessUseCase: asFunction(
      ({ companyRepository }: Pick<AppCradle, 'companyRepository'>) => new LookupBusinessUseCase(companyRepository),
    ).singleton(),
    registerCompanyUseCase: asFunction(
      ({ companyRepository }: Pick<AppCradle, 'companyRepository'>) => new RegisterCompanyUseCase(companyRepository),
    ).singleton(),
    updateCompanyUseCase: asFunction(
      ({ companyRepository }: Pick<AppCradle, 'companyRepository'>) => new UpdateCompanyUseCase(companyRepository),
    ).singleton(),
    getCurrentAccountUseCase: asFunction(createGetCurrentAccountUseCase).singleton(),
    getSupportProgramDetailUseCase: asFunction(
      createGetSupportProgramDetailUseCase,
    ).singleton(),
    getSupportProgramSearchReadinessUseCase: asFunction(
      createGetSupportProgramSearchReadinessUseCase,
    ).singleton(),
    logInUseCase: asFunction(createLogInUseCase).singleton(),
    logOutUseCase: asFunction(createLogOutUseCase).singleton(),
    getOAuthProvidersUseCase: asFunction(
      ({ accountRepository }: Pick<AppCradle, 'accountRepository'>) => new GetOAuthProvidersUseCase(accountRepository),
    ).singleton(),
    startOAuthLogInUseCase: asFunction(
      ({ accountRepository }: Pick<AppCradle, 'accountRepository'>) => new StartOAuthLogInUseCase(accountRepository),
    ).singleton(),
    completeOAuthLogInUseCase: asFunction(
      ({ accountRepository }: Pick<AppCradle, 'accountRepository'>) => new CompleteOAuthLogInUseCase(accountRepository),
    ).singleton(),
    prepareSampleItemUseCase: asFunction(
      createPrepareSampleItemUseCase,
    ).singleton(),
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

function createDevLogInUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): DevLogInUseCase {
  return new DevLogInUseCase(accountRepository)
}

function createGetCurrentAccountUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): GetCurrentAccountUseCase {
  return new GetCurrentAccountUseCase(accountRepository)
}

function createLogInUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): LogInUseCase {
  return new LogInUseCase(accountRepository)
}

function createSignUpUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): SignUpUseCase {
  return new SignUpUseCase(accountRepository)
}

function createLogOutUseCase({
  accountRepository,
}: Pick<AppCradle, 'accountRepository'>): LogOutUseCase {
  return new LogOutUseCase(accountRepository)
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
