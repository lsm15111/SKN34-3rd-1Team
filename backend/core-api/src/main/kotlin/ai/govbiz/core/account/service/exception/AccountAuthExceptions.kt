package ai.govbiz.core.account.service.exception

/** 같은 이메일의 계정이 이미 있을 때 발생합니다. */
class EmailAlreadyRegisteredException : RuntimeException()

/** 사업자등록번호가 국세청 등록 사업자로 확인되지 않을 때 발생합니다. */
class BusinessNotFoundException : RuntimeException()

/** 이메일 또는 비밀번호가 맞지 않을 때 발생합니다. 두 경우를 구분하지 않습니다. */
class InvalidCredentialsException : RuntimeException()

/** Bearer 세션 토큰이 없거나 만료·삭제됐을 때 발생합니다. */
class AuthenticationRequiredException : RuntimeException()

/** 로그인은 했지만 관리자 역할이 아닐 때 발생합니다. */
class AdminRequiredException : RuntimeException()

/** 운영자가 지정한 계정이 없을 때 발생합니다. */
class AccountNotFoundException : RuntimeException()
