import type { AwilixContainer } from 'awilix/browser'
import type { BrowseSupportProgramsUseCase } from '../../domain/usecases/BrowseSupportProgramsUseCase'

import type { CoreApiHealth } from '../../data/api/coreApiHealth'
import type { SessionHintStorage } from '../../data/storage/sessionHintStorage'
import type { AccountRepository } from '../../domain/repositories/AccountRepository'
import type { CompanyRepository } from '../../domain/repositories/CompanyRepository'
import type { PartnerProposalRepository } from '../../domain/repositories/PartnerProposalRepository'
import type { PartnerRecruitmentRepository } from '../../domain/repositories/PartnerRecruitmentRepository'
import type {
  GetMyCompanyUseCase,
  LookupBusinessUseCase,
  RegisterCompanyUseCase,
  UpdateCompanyUseCase,
} from '../../domain/usecases/CompanyUseCases'
import type {
  BrowsePartnerRecruitmentsUseCase,
  CreatePartnerRecruitmentUseCase,
  GetPartnerRecruitmentDetailUseCase,
} from '../../domain/usecases/PartnerRecruitmentUseCases'
import type {
  BrowsePartnerProposalsUseCase,
  RespondPartnerProposalUseCase,
  SendPartnerProposalUseCase,
} from '../../domain/usecases/PartnerProposalUseCases'
import type { SampleItemRepository } from '../../domain/repositories/SampleItemRepository'
import type { SupportProgramRepository } from '../../domain/repositories/SupportProgramRepository'
import type { AskSupportProgramEvidenceQuestionUseCase } from '../../domain/usecases/AskSupportProgramEvidenceQuestionUseCase'
import type { DevLogInUseCase } from '../../domain/usecases/DevLogInUseCase'
import type { GetCurrentAccountUseCase } from '../../domain/usecases/GetCurrentAccountUseCase'
import type { GetSupportProgramDetailUseCase } from '../../domain/usecases/GetSupportProgramDetailUseCase'
import type { GetSupportProgramSearchReadinessUseCase } from '../../domain/usecases/GetSupportProgramSearchReadinessUseCase'
import type { LogInUseCase } from '../../domain/usecases/LogInUseCase'
import type { LogOutUseCase } from '../../domain/usecases/LogOutUseCase'
import type {
  CompleteOAuthLogInUseCase,
  GetOAuthProvidersUseCase,
  StartOAuthLogInUseCase,
} from '../../domain/usecases/OAuthLogInUseCases'
import type { SignUpUseCase } from '../../domain/usecases/SignUpUseCase'
import type { PrepareSampleItemUseCase } from '../../domain/usecases/PrepareSampleItemUseCase'
import type { SearchSupportProgramsUseCase } from '../../domain/usecases/SearchSupportProgramsUseCase'
import type { InterpretSupportProgramConversationUseCase } from '../../domain/usecases/InterpretSupportProgramConversationUseCase'

export type FetchCoreApiHealth = (signal?: AbortSignal) => Promise<CoreApiHealth>

/** Awilix가 생성·연결할 수 있는 전체 의존성 목록입니다. */
export type AppCradle = {
  browseSupportProgramsUseCase: BrowseSupportProgramsUseCase
  accountRepository: AccountRepository
  browsePartnerProposalsUseCase: BrowsePartnerProposalsUseCase
  browsePartnerRecruitmentsUseCase: BrowsePartnerRecruitmentsUseCase
  companyRepository: CompanyRepository
  createPartnerRecruitmentUseCase: CreatePartnerRecruitmentUseCase
  getPartnerRecruitmentDetailUseCase: GetPartnerRecruitmentDetailUseCase
  getMyCompanyUseCase: GetMyCompanyUseCase
  lookupBusinessUseCase: LookupBusinessUseCase
  registerCompanyUseCase: RegisterCompanyUseCase
  updateCompanyUseCase: UpdateCompanyUseCase
  interpretSupportProgramConversationUseCase: InterpretSupportProgramConversationUseCase
  askSupportProgramEvidenceQuestionUseCase: AskSupportProgramEvidenceQuestionUseCase
  devLogInUseCase: DevLogInUseCase
  fetchCoreApiHealth: FetchCoreApiHealth
  getCurrentAccountUseCase: GetCurrentAccountUseCase
  getSupportProgramDetailUseCase: GetSupportProgramDetailUseCase
  getSupportProgramSearchReadinessUseCase: GetSupportProgramSearchReadinessUseCase
  logInUseCase: LogInUseCase
  logOutUseCase: LogOutUseCase
  getOAuthProvidersUseCase: GetOAuthProvidersUseCase
  startOAuthLogInUseCase: StartOAuthLogInUseCase
  completeOAuthLogInUseCase: CompleteOAuthLogInUseCase
  partnerProposalRepository: PartnerProposalRepository
  partnerRecruitmentRepository: PartnerRecruitmentRepository
  respondPartnerProposalUseCase: RespondPartnerProposalUseCase
  sendPartnerProposalUseCase: SendPartnerProposalUseCase
  prepareSampleItemUseCase: PrepareSampleItemUseCase
  sampleItemRepository: SampleItemRepository
  searchSupportProgramsUseCase: SearchSupportProgramsUseCase
  sessionHintStorage: SessionHintStorage
  signUpUseCase: SignUpUseCase
  supportProgramRepository: SupportProgramRepository
}

export type AppContainer = AwilixContainer<AppCradle>
