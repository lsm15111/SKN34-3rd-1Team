import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'

import { appContainer } from '../../../../app/appContainer'
import type { SupportProgram } from '../../../../domain/entities/SupportProgram'
import type { BrowseSupportProgramsUseCase } from '../../../../domain/usecases/BrowseSupportProgramsUseCase'
import { validatePartnerRecruitmentInput, type CreatePartnerRecruitmentUseCase } from '../../../../domain/usecases/PartnerRecruitmentUseCases'
import { useAuthSession } from '../../../shared/auth/hooks/useAuthSession'
import { useQueryCache } from '../../../shared/data/queryCacheContext'
import { companyInitial } from '../../../shared/partner-recruitment/partnerRecruitmentLabels'
import { partnerRecruitmentListCacheNamespace } from '../../../shared/partner-recruitment/usePartnerRecruitmentBrowse'
import { appPaths } from '../../../shared/routes/appPaths'
import {
  recruitmentFieldMessage,
  recruitmentFormMessages,
  todayInSeoul,
  useRecruitmentFormFields,
} from './useRecruitmentFormFields'

const DAY_MS = 86_400_000
const PROGRAM_SEARCH_PAGE_SIZE = 8

export const recruitmentCreateMessages = {
  ...recruitmentFormMessages,
  companyRequired: '프로필에서 기업을 등록한 뒤 모집글을 쓸 수 있습니다.',
  programNotFound: '고른 공고를 더 이상 찾을 수 없습니다. 공고를 다시 검색해 주세요.',
  programClosed: '접수가 끝난 공고에는 모집글을 쓸 수 없습니다. 다른 공고를 골라 주세요.',
  alreadyExists: '이 공고에는 이미 내 모집글이 있습니다. 공고당 모집글은 하나입니다.',
  programClosingToday: '오늘 접수가 끝나는 공고에는 모집글을 쓸 수 없습니다. 다른 공고를 골라 주세요.',
  failed: '모집글을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  searchFailed: '공고를 검색하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

type ProgramSearchState =
  | { status: 'idle' }
  | { status: 'searching' }
  | { status: 'found'; programs: SupportProgram[] }
  | { status: 'failed' }

type ViewModelUseCases = {
  browsePrograms: Pick<BrowseSupportProgramsUseCase, 'execute'>
  createRecruitment: Pick<CreatePartnerRecruitmentUseCase, 'execute'>
}

/** 날짜 입력에는 시간이 없으므로 접수 마감 전날이 고를 수 있는 마지막 모집 마감일입니다. 접수 마감일이 없으면 제한하지 않습니다. */
export function latestRecruitmentDeadlineFor(program: Pick<SupportProgram, 'applicationEndDate'> | null): string | null {
  if (program?.applicationEndDate == null) return null
  return new Date(Date.parse(`${program.applicationEndDate}T00:00:00Z`) - DAY_MS).toISOString().slice(0, 10)
}

/** 접수 마감 전날이 오늘보다 앞서면(오늘 마감) 모집 마감일을 고를 수 없으므로 묶을 수 없습니다. */
export function canAttachRecruitment(program: Pick<SupportProgram, 'applicationEndDate'>, today: string = todayInSeoul()): boolean {
  const latest = latestRecruitmentDeadlineFor(program)
  return latest === null || latest >= today
}

/**
 * 모집글 작성의 대표 ViewModel입니다. 공고 검색·선택을 소유하고 역할·조건·역량·본문은 수정 화면과 같은 폼 훅을 쓰며,
 * 등록 UseCase를 호출해 성공하면 새 모집글 상세로 이동합니다. 작성은 프로필에서 기업을 등록한 회원만 할 수 있고,
 * 제안 조건(이메일 인증)은 서비스 정책이라 작성자가 고르지 않습니다.
 */
export function usePartnerRecruitmentCreateViewModel(useCases?: Partial<ViewModelUseCases>) {
  const resolved: ViewModelUseCases = {
    browsePrograms: useCases?.browsePrograms ?? appContainer.resolve('browseSupportProgramsUseCase'),
    createRecruitment: useCases?.createRecruitment ?? appContainer.resolve('createPartnerRecruitmentUseCase'),
  }
  const navigate = useNavigate()
  const queryCache = useQueryCache()
  const { account, hasCompany } = useAuthSession()
  const [programKeyword, setProgramKeyword] = useState('')
  const [programSearch, setProgramSearch] = useState<ProgramSearchState>({ status: 'idle' })
  const [selectedProgram, setSelectedProgram] = useState<SupportProgram | null>(null)
  const form = useRecruitmentFormFields()
  const [isSubmitting, setIsSubmitting] = useState(false)

  const trimmedKeyword = programKeyword.trim()
  useEffect(() => {
    if (trimmedKeyword.length < 2) {
      setProgramSearch({ status: 'idle' })
      return
    }
    const controller = new AbortController()
    let current = true
    setProgramSearch({ status: 'searching' })
    const timer = setTimeout(() => {
      void Promise.resolve()
        .then(() => resolved.browsePrograms.execute(
          {
            keyword: trimmedKeyword, region: '', category: '', sourceCode: '', startupStage: '', applicantType: '', founderAge: '',
            status: 'OPEN', sort: 'DEADLINE', page: 1, pageSize: PROGRAM_SEARCH_PAGE_SIZE,
          },
          controller.signal,
        ))
        .then((catalog) => { if (current && !controller.signal.aborted) setProgramSearch({ status: 'found', programs: catalog.programs }) })
        .catch(() => { if (current && !controller.signal.aborted) setProgramSearch({ status: 'failed' }) })
    }, 300)
    return () => { current = false; clearTimeout(timer); controller.abort() }
    // 검색어가 바뀔 때만 다시 조회합니다. UseCase는 앱 수명 동안 같습니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trimmedKeyword])

  const maximumRecruitmentDeadline = latestRecruitmentDeadlineFor(selectedProgram)

  function selectProgram(program: SupportProgram) {
    if (!canAttachRecruitment(program)) {
      form.setError({ field: 'program', message: recruitmentCreateMessages.programClosingToday })
      return
    }
    setSelectedProgram(program)
    form.setError(null)
    const latest = latestRecruitmentDeadlineFor(program)
    if (latest !== null && form.recruitmentDeadline > latest) form.updateRecruitmentDeadline('')
  }

  function clearProgram() {
    setSelectedProgram(null)
    form.setError(null)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isSubmitting) return
    if (selectedProgram === null) {
      form.setError({ field: 'program', message: recruitmentCreateMessages.program })
      return
    }
    const deadlineProblem = form.deadlineProblem(maximumRecruitmentDeadline)
    if (deadlineProblem !== null) {
      form.setError(deadlineProblem)
      return
    }
    const input = {
      sourceCode: selectedProgram.sourceCode,
      sourceProgramId: selectedProgram.id,
      ...form.content(),
    }
    const problem = validatePartnerRecruitmentInput({ ...input, title: input.title.trim(), body: input.body.trim() })
    if (problem !== null) {
      form.setError(recruitmentFieldMessage(problem, maximumRecruitmentDeadline))
      return
    }

    setIsSubmitting(true)
    form.setError(null)
    try {
      const result = await resolved.createRecruitment.execute(input)
      switch (result.outcome) {
        case 'created':
          queryCache.invalidate(partnerRecruitmentListCacheNamespace)
          navigate(`${appPaths.partnerDetail}?${new URLSearchParams({ recruitmentId: String(result.recruitment.id) })}`)
          return
        case 'company-required':
          form.setError({ field: null, message: recruitmentCreateMessages.companyRequired })
          return
        case 'program-not-found':
          setSelectedProgram(null)
          form.setError({ field: 'program', message: recruitmentCreateMessages.programNotFound })
          return
        case 'program-closed':
          setSelectedProgram(null)
          form.setError({ field: 'program', message: recruitmentCreateMessages.programClosed })
          return
        case 'deadline-not-allowed':
          form.setError({
            field: 'recruitmentDeadline',
            message: result.latestAllowedDeadline === null
              ? recruitmentCreateMessages.recruitmentDeadline
              : recruitmentCreateMessages.deadlineBefore(result.latestAllowedDeadline),
          })
          return
        case 'already-exists':
          form.setError({ field: 'program', message: recruitmentCreateMessages.alreadyExists })
          return
      }
    } catch {
      form.setError({ field: null, message: recruitmentCreateMessages.failed })
    } finally {
      setIsSubmitting(false)
    }
  }

  return {
    form,
    error: form.error,
    maximumRecruitmentDeadline,
    isSubmitting,
    submit,
    /** 기업 등록 전에는 폼 대신 등록 안내를 보여 줍니다. */
    canCreate: hasCompany,
    profilePath: appPaths.profile,
    /** 모집글에 표시되는 우리 기업입니다. 세션의 등록 기업 요약을 쓰고 상세 값은 프로필 API가 맡습니다. */
    ownCompany: account?.company
      ? { initial: companyInitial(account.company.companyName), name: account.company.companyName, isEmailVerified: account.emailVerified }
      : null,
    programKeyword,
    updateProgramKeyword: setProgramKeyword,
    programSearch,
    canAttachRecruitment,
    selectedProgram,
    selectProgram,
    clearProgram,
  }
}
