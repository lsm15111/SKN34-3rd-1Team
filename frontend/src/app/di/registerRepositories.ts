import { asClass } from 'awilix/browser'

import { AccountRepositoryImpl } from '../../data/repositories/AccountRepositoryImpl'
import { SampleItemRepositoryImpl } from '../../data/repositories/SampleItemRepositoryImpl'
import { SupportProgramRepositoryImpl } from '../../data/repositories/SupportProgramRepositoryImpl'
import type { AppContainer } from './types'

/** Data Layer의 Repository 구현체와 앱 수명주기를 등록합니다. */
export function registerRepositories(container: AppContainer) {
  container.register({
    accountRepository: asClass(AccountRepositoryImpl).singleton(),
    sampleItemRepository: asClass(SampleItemRepositoryImpl).singleton(),
    supportProgramRepository: asClass(SupportProgramRepositoryImpl).singleton(),
  })
}
