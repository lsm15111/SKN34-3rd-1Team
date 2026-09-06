import { z } from 'zod'

export const loginFormSchema = z.object({
  email: z.string().trim().min(1, '이메일을 입력해 주세요.').max(320, '이메일은 320자 이하여야 합니다.')
    .pipe(z.email('이메일 형식이 올바르지 않습니다.')),
  password: z.string().min(1, '비밀번호를 입력해 주세요.').max(72, '비밀번호는 72자 이하여야 합니다.'),
})

export type LoginFormValues = z.infer<typeof loginFormSchema>
