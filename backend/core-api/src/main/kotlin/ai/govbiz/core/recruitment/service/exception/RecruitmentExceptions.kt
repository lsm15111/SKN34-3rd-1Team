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

/** 제안이 없거나 조회자와 무관한 제안일 때 발생합니다. */
class ProposalNotFoundException : RuntimeException()

/** 자기 기업이 쓴 모집글에 제안하려 할 때 발생합니다. */
class OwnPostProposalException : RuntimeException()

/** 제안 기업이 아닌 계정이 철회하려 할 때 발생합니다. */
class NotProposalOwnerException : RuntimeException()

/** 같은 기업이 같은 모집글에 이미 제안했을 때 발생합니다. 철회·거절 뒤에도 다시 제안할 수 없습니다. */
class ProposalAlreadyExistsException : RuntimeException()

/** 이미 결정됐거나 만료·마감으로 더 이상 PENDING이 아닌 제안을 수락·거절·철회하려 할 때 발생합니다. */
class ProposalNotPendingException : RuntimeException()
