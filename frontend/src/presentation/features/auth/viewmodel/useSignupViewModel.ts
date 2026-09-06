import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router'

import { appContainer } from '../../../../app/appContainer'
import { useAppDispatch } from '../../../../app/hooks'
import type { Company } from '../../../../domain/entities/Account'
import type { LookupBusinessUseCase } from '../../../../domain/usecases/LookupBusinessUseCase'
import type { SignUpUseCase } from '../../../../domain/usecases/SignUpUseCase'
import { signedIn } from '../state/authSlice'
import { readReturnPath } from './returnPath'
import {
  businessNumberPattern,
  signupFormSchema,
  type SignupFormValues,
} from '../validation/signupFormSchema'

type BusinessLookupUseCase = Pick<LookupBusinessUseCase, 'execute'>
type AccountSignUpUseCase = Pick<SignUpUseCase, 'execute'>

export type BusinessLookupState =
  | { status: 'idle' }
  | { status: 'looking-up' }
  | { status: 'found'; companies: Company[] }
  | { status: 'not-found' }
  | { status: 'unavailable' }
  | { status: 'invalid-number' }

export const signupMessages = {
  emailTaken: '이미 가입된 이메일입니다. 로그인해 주세요.',
  businessNotFound: '사업자등록번호로 기업을 찾지 못했습니다. 번호를 다시 확인해 주세요.',
  lookupUnavailable: '기업 정보를 지금 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  lookupRequired: '가입 전에 사업자등록번호로 기업 정보를 확인해 주세요.',
  requestFailed: '회원가입 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

/** 사업자등록번호 확인 → 계정 정보 입력 → 가입 요청까지의 화면 상태를 관리합니다. */
export function useSignupViewModel(
  lookupBusinessUseCase: BusinessLookupUseCase = appContainer.resolve('lookupBusinessUseCase'),
  signUpUseCase: AccountSignUpUseCase = appContainer.resolve('signUpUseCase'),
) {
  const dispatchToStore = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const form = useForm<SignupFormValues>({
    resolver: zodResolver(signupFormSchema),
    mode: 'onBlur',
    defaultValues: {
      businessNumber: '',
      email: '',
      password: '',
      passwordConfirm: '',
      termsAgreed: false as unknown as true,
    },
  })
  const lookupController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)
  const [lookup, setLookup] = useState<BusinessLookupState>({ status: 'idle' })
  const [selectedCompany, setSelectedCompany] = useState<Company | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    isMounted.current = true
    return () => {
      isMounted.current = false
      lookupController.current?.abort()
    }
  }, [])

  /** 번호가 바뀌면 이전 확인 결과는 더 이상 유효하지 않습니다. */
  function registerBusinessNumber() {
    return form.register('businessNumber', {
      onChange: () => {
        lookupController.current?.abort()
        lookupController.current = null
        setLookup({ status: 'idle' })
        setSelectedCompany(null)
        setSubmitError(null)
      },
    })
  }

  async function lookupBusiness() {
    const businessNumber = form.getValues('businessNumber').trim()
    if (!businessNumberPattern.test(businessNumber)) {
      setLookup({ status: 'invalid-number' })
      return
    }

    lookupController.current?.abort()
    const controller = new AbortController()
    lookupController.current = controller
    setLookup({ status: 'looking-up' })
    setSelectedCompany(null)
    setSubmitError(null)

    try {
      const result = await lookupBusinessUseCase.execute(businessNumber, controller.signal)
      if (!isMounted.current || controller.signal.aborted) return
      if (result.outcome === 'lookup-unavailable') {
        setLookup({ status: 'unavailable' })
        return
      }
      if (result.companies.length === 0) {
        setLookup({ status: 'not-found' })
        return
      }
      setLookup({ status: 'found', companies: result.companies })
      setSelectedCompany(result.companies[0] ?? null)
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setLookup({ status: 'unavailable' })
    } finally {
      if (lookupController.current === controller) lookupController.current = null
    }
  }

  const submit = form.handleSubmit(async (values) => {
    if (isSubmitting) return
    if (lookup.status !== 'found' || !selectedCompany) {
      setSubmitError(signupMessages.lookupRequired)
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    try {
      const result = await signUpUseCase.execute({
        email: values.email,
        password: values.password,
        businessNumber: selectedCompany.businessNumber,
      })
      if (!isMounted.current) return
      switch (result.outcome) {
        case 'session':
          dispatchToStore(signedIn(result.session.account))
          navigate(readReturnPath(location.state), { replace: true })
          return
        case 'email-taken':
          setSubmitError(signupMessages.emailTaken)
          return
        case 'business-not-found':
          setSubmitError(signupMessages.businessNotFound)
          return
        case 'business-lookup-unavailable':
          setSubmitError(signupMessages.lookupUnavailable)
          return
      }
    } catch {
      if (!isMounted.current) return
      setSubmitError(signupMessages.requestFailed)
    } finally {
      if (isMounted.current) setIsSubmitting(false)
    }
  })

  return {
    errors: form.formState.errors,
    isLookingUp: lookup.status === 'looking-up',
    isSubmitting,
    lookup,
    lookupBusiness,
    registerBusinessNumber,
    registerField: form.register,
    selectCompany: setSelectedCompany,
    selectedCompany,
    submit,
    submitError,
  }
}
