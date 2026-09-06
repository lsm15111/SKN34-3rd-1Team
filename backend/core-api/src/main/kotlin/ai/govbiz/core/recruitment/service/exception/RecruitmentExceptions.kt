package ai.govbiz.core.recruitment.service.exception

/** 모집글이 없거나, 숨김·종료된 글을 작성 기업이 아닌 사용자가 조회할 때 발생합니다. */
class RecruitmentPostNotFoundException : RuntimeException()

/** 작성 기업이 아닌 계정이 수정·마감·제안 관리를 시도할 때 발생합니다. */
class NotPostOwnerException : RuntimeException()

/** 모집 중이 아닌 글을 수정·마감하거나 제안하려 할 때 발생합니다. */
class RecruitmentPostNotOpenException : RuntimeException()

/** 연결하려는 공고가 현재 공개되지 않았거나 접수가 끝났을 때 발생합니다. */
class SupportProgramNotOpenException : RuntimeException()

/** 모집 마감일이 오늘 이전이거나 공고 접수 마감일보다 늦을 때 발생합니다. */
class RecruitmentClosesOnInvalidException : RuntimeException()

/** 본문에 이메일·전화번호 같은 연락처가 들어 있을 때 발생합니다. */
class ContactInTextException : RuntimeException()
