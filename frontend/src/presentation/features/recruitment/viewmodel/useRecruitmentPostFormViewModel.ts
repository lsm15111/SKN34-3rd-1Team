import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'

import { appContainer } from '../../../../app/appContainer'
import type { LinkedProgram } from '../../../../domain/entities/RecruitmentPost'
import type { SupportProgram } from '../../../../domain/entities/SupportProgram'
import type { SupportProgramIdentity } from '../../../../domain/repositories/SupportProgramRepository'
import type { GetSupportProgramDetailUseCase } from '../../../../domain/usecases/GetSupportProgramDetailUseCase'
import type {
  CreateRecruitmentPostUseCase,
  GetRecruitmentPostUseCase,
  UpdateRecruitmentPostUseCase,
} from '../../../../domain/usecases/RecruitmentPostUseCases'
import type { SearchSupportProgramsUseCase } from '../../../../domain/usecases/SearchSupportProgramsUseCase'
import {
  recruitmentPostFormSchema,
  toRecruitmentPostDraft,
  toRecruitmentPostFormValues,
  type RecruitmentPostFormValues,
} from '../validation/recruitmentPostFormSchema'

/** 검색 결과(SupportProgram)와 저장된 연결 공고(LinkedProgram)를 같은 모양으로 보여 주기 위한 요약입니다. */
export type ProgramSummary = {
  sourceCode: string
  sourceProgramId: string
  title: string
  organization: string
  status: SupportProgram['status']
  applicationPeriod: string
  applicationEndDate: string | null
  targetDescription: string
}

export type RecruitmentPostFormMode =
  | { kind: 'create'; initialProgram: SupportProgramIdentity | null }
  | { kind: 'edit'; postId: number }

type ProgramSearchState =
  | { status: 'idle' }
  | { status: 'searching' }
  | { status: 'results'; programs: ProgramSummary[] }
  | { status: 'failed' }

type FormLoadState = 'loading' | 'ready' | 'not-found' | 'forbidden' | 'not-open' | 'failed'

export const recruitmentFormMessages = {
  programRequired: '연결할 공고를 먼저 선택해 주세요.',
  programNotOpen: '선택한 공고는 현재 공개되지 않았거나 접수가 끝났습니다. 다른 공고를 선택해 주세요.',
  closesOnInvalid: '모집 마감일은 오늘 이후이면서 공고 접수 마감일 이전이어야 합니다.',
  contactInText: '제목·모집 소개에 이메일이나 전화번호를 적을 수 없습니다. 담당자 연락처는 제안 수락 뒤 공개됩니다.',
  notOwner: '작성 기업만 이 모집글을 수정할 수 있습니다.',
  notOpen: '이미 종료된 모집글은 수정할 수 없습니다.',
  notFound: '모집글을 찾을 수 없습니다.',
  requestFailed: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  searchFailed: '공고를 검색하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

const emptyValues: RecruitmentPostFormValues = {
  title: '',
  body: '',
  ourRole: 'LEAD',
  wantedRole: 'PARTICIPANT',
  wantedCompanyCount: 1,
  wantedRegion: '',
  requiredCapabilitiesText: '',
  closesOn: '',
}

/**
 * 모집글 작성·수정 폼입니다. 작성 모드는 공고 검색으로 연결 공고를 고르고, 수정 모드는 저장된 글을 불러와
 * 작성 기업이 모집 중일 때만 조건을 고칩니다.
 */
export function useRecruitmentPostFormViewModel(
  mode: RecruitmentPostFormMode,
  deps: {
    createRecruitmentPostUseCase?: Pick<CreateRecruitmentPostUseCase, 'execute'>
    updateRecruitmentPostUseCase?: Pick<UpdateRecruitmentPostUseCase, 'execute'>
    getRecruitmentPostUseCase?: Pick<GetRecruitmentPostUseCase, 'execute'>
    searchSupportProgramsUseCase?: Pick<SearchSupportProgramsUseCase, 'execute'>
    getSupportProgramDetailUseCase?: Pick<GetSupportProgramDetailUseCase, 'execute'>
  } = {},
) {
  const createUseCase = deps.createRecruitmentPostUseCase ?? appContainer.resolve('createRecruitmentPostUseCase')
  const updateUseCase = deps.updateRecruitmentPostUseCase ?? appContainer.resolve('updateRecruitmentPostUseCase')
  const getPostUseCase = deps.getRecruitmentPostUseCase ?? appContainer.resolve('getRecruitmentPostUseCase')
  const searchUseCase = deps.searchSupportProgramsUseCase ?? appContainer.resolve('searchSupportProgramsUseCase')
  const detailUseCase = deps.getSupportProgramDetailUseCase ?? appContainer.resolve('getSupportProgramDetailUseCase')
  const navigate = useNavigate()
  const form = useForm<RecruitmentPostFormValues>({
    resolver: zodResolver(recruitmentPostFormSchema),
    mode: 'onBlur',
    defaultValues: emptyValues,
  })
  const [loadState, setLoadState] = useState<FormLoadState>(mode.kind === 'edit' ? 'loading' : 'ready')
  const [selectedProgram, setSelectedProgram] = useState<ProgramSummary | null>(null)
  const [programQuery, setProgramQuery] = useState('')
  const [programSearch, setProgramSearch] = useState<ProgramSearchState>({ status: 'idle' })
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const searchController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)
  const modeKey = mode.kind === 'edit'
    ? `edit:${mode.postId}`
    : `create:${mode.initialProgram?.sourceCode ?? ''}:${mode.initialProgram?.sourceProgramId ?? ''}`

  const loadInitial = useCallback(async () => {
    const controller = new AbortController()
    searchController.current?.abort()
    searchController.current = controller
    if (mode.kind === 'edit') {
      setLoadState('loading')
      try {
        const post = await getPostUseCase.execute(mode.postId, controller.signal)
        if (!isMounted.current || controller.signal.aborted) return
        if (!post) return setLoadState('not-found')
        if (!post.viewer.isOwner) return setLoadState('forbidden')
        if (post.status !== 'OPEN') return setLoadState('not-open')
        form.reset(toRecruitmentPostFormValues(post))
        setSelectedProgram(post.program ? fromLinkedProgram(post.program) : null)
        setLoadState('ready')
      } catch {
        if (!isMounted.current || controller.signal.aborted) return
        setLoadState('failed')
      }
      return
    }
    if (!mode.initialProgram) return
    try {
      const program = await detailUseCase.execute(mode.initialProgram, controller.signal)
      if (!isMounted.current || controller.signal.aborted) return
      if (program) setSelectedProgram(fromSupportProgram(program))
    } catch {
      // 미리 선택한 공고를 못 읽으면 검색으로 다시 고르면 됩니다.
    }
    // mode 객체는 매 렌더 새로 만들어지므로 문자열 키로 비교합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detailUseCase, form, getPostUseCase, modeKey])

  useEffect(() => {
    isMounted.current = true
    void loadInitial()
    return () => {
      isMounted.current = false
      searchController.current?.abort()
    }
  }, [loadInitial])

  async function searchPrograms() {
    const query = programQuery.trim()
    if (query.length === 0) return
    searchController.current?.abort()
    const controller = new AbortController()
    searchController.current = controller
    setProgramSearch({ status: 'searching' })
    try {
      const result = await searchUseCase.execute(query, controller.signal)
      if (!isMounted.current || controller.signal.aborted) return
      setProgramSearch({ status: 'results', programs: result.programs.map(fromSupportProgram) })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setProgramSearch({ status: 'failed' })
    }
  }

  function selectProgram(program: ProgramSummary) {
    setSelectedProgram(program)
    setProgramSearch({ status: 'idle' })
    setSubmitError(null)
  }

  const submit = form.handleSubmit(async (values) => {
    if (isSubmitting) return
    if (mode.kind === 'create' && !selectedProgram) {
      setSubmitError(recruitmentFormMessages.programRequired)
      return
    }
    setIsSubmitting(true)
    setSubmitError(null)
    try {
      const draft = toRecruitmentPostDraft(values)
      const result = mode.kind === 'create'
        ? await createUseCase.execute(
          { sourceCode: selectedProgram!.sourceCode, sourceProgramId: selectedProgram!.sourceProgramId },
          draft,
        )
        : await updateUseCase.execute(mode.postId, draft)
      if (!isMounted.current) return
      if (result.outcome === 'saved') {
        navigate(`/partners/${result.post.id}`, { replace: true })
        return
      }
      setSubmitError(messageFor(result.outcome))
    } catch {
      if (!isMounted.current) return
      setSubmitError(recruitmentFormMessages.requestFailed)
    } finally {
      if (isMounted.current) setIsSubmitting(false)
    }
  })

  return {
    canChangeProgram: mode.kind === 'create',
    clearProgram: () => setSelectedProgram(null),
    errors: form.formState.errors,
    isSubmitting,
    loadState,
    mode: mode.kind,
    programQuery,
    programSearch,
    registerField: form.register,
    searchPrograms,
    selectProgram,
    selectedProgram,
    setProgramQuery,
    submit,
    submitError,
  }
}

function messageFor(outcome: 'program-not-open' | 'closes-on-invalid' | 'contact-in-text' | 'not-owner' | 'not-open' | 'not-found') {
  switch (outcome) {
    case 'program-not-open': return recruitmentFormMessages.programNotOpen
    case 'closes-on-invalid': return recruitmentFormMessages.closesOnInvalid
    case 'contact-in-text': return recruitmentFormMessages.contactInText
    case 'not-owner': return recruitmentFormMessages.notOwner
    case 'not-open': return recruitmentFormMessages.notOpen
    case 'not-found': return recruitmentFormMessages.notFound
  }
}

function fromSupportProgram(program: SupportProgram): ProgramSummary {
  return {
    sourceCode: program.sourceCode,
    sourceProgramId: program.id,
    title: program.title,
    organization: program.organization,
    status: program.status,
    applicationPeriod: program.applicationPeriod,
    applicationEndDate: program.applicationEndDate,
    targetDescription: program.targetDescription,
  }
}

function fromLinkedProgram(program: LinkedProgram): ProgramSummary {
  return {
    sourceCode: program.sourceCode,
    sourceProgramId: program.sourceProgramId,
    title: program.title,
    organization: program.organization,
    status: program.status,
    applicationPeriod: program.applicationPeriod,
    applicationEndDate: program.applicationEndDate,
    targetDescription: program.targetDescription,
  }
}
