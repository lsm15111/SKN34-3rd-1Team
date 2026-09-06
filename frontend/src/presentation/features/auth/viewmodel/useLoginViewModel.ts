import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'

import { appContainer } from '../../../../app/appContainer'
import { useAppDispatch } from '../../../../app/hooks'
import type { LogInUseCase } from '../../../../domain/usecases/LogInUseCase'
import { signedIn } from '../state/authSlice'
import { loginFormSchema, type LoginFormValues } from '../validation/loginFormSchema'

type AccountLogInUseCase = Pick<LogInUseCase, 'execute'>

export const loginMessages = {
  invalidCredentials: '이메일 또는 비밀번호를 확인해 주세요.',
  requestFailed: '로그인 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

/** 이메일·비밀번호 입력을 검증하고 로그인 결과를 화면 상태로 바꿉니다. */
export function useLoginViewModel(
  logInUseCase: AccountLogInUseCase = appContainer.resolve('logInUseCase'),
) {
  const dispatchToStore = useAppDispatch()
  const navigate = useNavigate()
  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginFormSchema),
    mode: 'onBlur',
    defaultValues: { email: '', password: '' },
  })
  const isMounted = useRef(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    isMounted.current = true
    return () => {
      isMounted.current = false
    }
  }, [])

  const submit = form.handleSubmit(async (values) => {
    if (isSubmitting) return

    setIsSubmitting(true)
    setSubmitError(null)
    try {
      const result = await logInUseCase.execute(values)
      if (!isMounted.current) return
      if (result.outcome === 'invalid-credentials') {
        setSubmitError(loginMessages.invalidCredentials)
        return
      }
      dispatchToStore(signedIn(result.session.account))
      navigate('/', { replace: true })
    } catch {
      if (!isMounted.current) return
      setSubmitError(loginMessages.requestFailed)
    } finally {
      if (isMounted.current) setIsSubmitting(false)
    }
  })

  return {
    errors: form.formState.errors,
    isSubmitting,
    registerField: form.register,
    submit,
    submitError,
  }
}
