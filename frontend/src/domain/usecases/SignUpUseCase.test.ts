import { describe, expect, it, vi } from 'vitest'

import { LogInUseCase } from './LogInUseCase'
import { LookupBusinessUseCase } from './LookupBusinessUseCase'
import { SignUpUseCase } from './SignUpUseCase'

describe('account use cases', () => {
  it('normalizes the email and business number before signing up', async () => {
    const signUp = vi.fn().mockResolvedValue({ outcome: 'email-taken' })
    const controller = new AbortController()

    const result = await new SignUpUseCase({ signUp }).execute({
      email: ' Manager@Company.co.kr ',
      password: 'password1',
      businessNumber: '124-81-00998',
    }, controller.signal)

    expect(signUp).toHaveBeenCalledWith({
      email: 'manager@company.co.kr',
      password: 'password1',
      businessNumber: '1248100998',
    }, controller.signal)
    expect(result).toEqual({ outcome: 'email-taken' })
  })

  it('normalizes only the email for login and keeps the password as typed', async () => {
    const logIn = vi.fn().mockResolvedValue({ outcome: 'invalid-credentials' })

    await new LogInUseCase({ logIn }).execute({ email: 'MANAGER@company.co.kr ', password: ' Pass word1 ' })

    expect(logIn).toHaveBeenCalledWith(
      { email: 'manager@company.co.kr', password: ' Pass word1 ' },
      undefined,
    )
  })

  it('strips separators from the business number before the lookup', async () => {
    const lookupBusiness = vi.fn().mockResolvedValue({ outcome: 'found', companies: [] })

    await new LookupBusinessUseCase({ lookupBusiness }).execute(' 124-81-00998 ')

    expect(lookupBusiness).toHaveBeenCalledWith('1248100998', undefined)
  })
})
