import { z } from 'zod'

/** Core API의 SignupRequest 검증과 같은 규칙입니다. 서버는 여기서 통과한 값을 다시 검증합니다. */
export const passwordPattern = /^(?=.*[A-Za-z])(?=.*[0-9])[^\p{C}]{8,72}$/u
export const businessNumberPattern = /^[0-9]{3}-?[0-9]{2}-?[0-9]{5}$/

export const signupFormSchema = z.object({
  businessNumber: z.string().trim().regex(businessNumberPattern, '사업자등록번호 10자리를 입력해 주세요.'),
  email: z.string().trim().min(1, '이메일을 입력해 주세요.').max(320, '이메일은 320자 이하여야 합니다.')
    .pipe(z.email('이메일 형식이 올바르지 않습니다.')),
  password: z.string().regex(passwordPattern, '비밀번호는 8~72자이며 영문과 숫자를 모두 포함해야 합니다.'),
  passwordConfirm: z.string(),
  termsAgreed: z.literal(true, '서비스 이용약관과 개인정보 처리방침에 동의해 주세요.'),
}).refine((values) => values.password === values.passwordConfirm, {
  path: ['passwordConfirm'],
  message: '비밀번호가 일치하지 않습니다.',
})

export type SignupFormValues = z.infer<typeof signupFormSchema>
