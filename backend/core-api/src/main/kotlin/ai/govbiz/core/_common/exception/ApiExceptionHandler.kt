package ai.govbiz.core._common.exception

import ai.govbiz.core.account.client.bizno.exception.BiznoClientException
import ai.govbiz.core.account.service.exception.AccountNotFoundException
import ai.govbiz.core.account.service.exception.AdminRequiredException
import ai.govbiz.core.account.service.exception.AuthenticationRequiredException
import ai.govbiz.core.account.service.exception.BusinessNotFoundException
import ai.govbiz.core.account.service.exception.EmailAlreadyRegisteredException
import ai.govbiz.core.account.service.exception.InvalidCredentialsException
import ai.govbiz.core.recruitment.service.exception.ContactInTextException
import ai.govbiz.core.recruitment.service.exception.NotPostOwnerException
import ai.govbiz.core.recruitment.service.exception.NotProposalOwnerException
import ai.govbiz.core.recruitment.service.exception.OwnPostProposalException
import ai.govbiz.core.recruitment.service.exception.ProposalAlreadyExistsException
import ai.govbiz.core.recruitment.service.exception.ProposalNotFoundException
import ai.govbiz.core.recruitment.service.exception.ProposalNotPendingException
import ai.govbiz.core.recruitment.service.exception.RecruitmentClosesOnInvalidException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotFoundException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotOpenException
import ai.govbiz.core.recruitment.service.exception.SupportProgramNotOpenException
import ai.govbiz.core.supportprogram.service.detail.exception.SupportProgramNotFoundException
import ai.govbiz.core.supportprogram.service.evidence.exception.SupportProgramEvidenceNotSupportedException
import ai.govbiz.core.supportprogram.service.evidence.exception.SupportProgramEvidenceUnavailableException
import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(SupportProgramNotFoundException::class)
    fun handleSupportProgramNotFoundException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.NOT_FOUND,
                URI.create("urn:govbiz:problem:support-program-not-found"),
                "Support Program Not Found",
                "The requested support program does not exist or is no longer available.",
                "SUPPORT_PROGRAM_NOT_FOUND",
            ),
            request,
        )

    @ExceptionHandler(SupportProgramEvidenceNotSupportedException::class)
    fun handleSupportProgramEvidenceNotSupportedException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.UNPROCESSABLE_CONTENT,
                URI.create("urn:govbiz:problem:support-program-evidence-not-supported"),
                "Support Program Evidence Not Supported",
                "Evidence-based answers are not supported for this support program source.",
                "SUPPORT_PROGRAM_EVIDENCE_NOT_SUPPORTED",
            ),
            request,
        )

    @ExceptionHandler(SupportProgramEvidenceUnavailableException::class)
    fun handleSupportProgramEvidenceUnavailableException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.SERVICE_UNAVAILABLE,
                URI.create("urn:govbiz:problem:support-program-evidence-unavailable"),
                "Support Program Evidence Unavailable",
                "Evidence-based answers are temporarily unavailable for this support program.",
                "SUPPORT_PROGRAM_EVIDENCE_UNAVAILABLE",
            ),
            request,
        )

    @ExceptionHandler(AiServiceCallException::class)
    fun handleAiServiceCallException(
        exception: AiServiceCallException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(definitionFor(exception.failure), request)

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun handleEmailAlreadyRegisteredException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.CONFLICT,
                URI.create("urn:govbiz:problem:email-already-registered"),
                "Email Already Registered",
                "An account with this email already exists.",
                "EMAIL_ALREADY_REGISTERED",
            ),
            request,
        )

    @ExceptionHandler(BusinessNotFoundException::class)
    fun handleBusinessNotFoundException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.UNPROCESSABLE_CONTENT,
                URI.create("urn:govbiz:problem:business-not-found"),
                "Business Not Found",
                "The business registration number is not a registered business.",
                "BUSINESS_NOT_FOUND",
            ),
            request,
        )

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentialsException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.UNAUTHORIZED,
                URI.create("urn:govbiz:problem:invalid-credentials"),
                "Invalid Credentials",
                "The email or password is incorrect.",
                "INVALID_CREDENTIALS",
            ),
            request,
        )

    @ExceptionHandler(AuthenticationRequiredException::class)
    fun handleAuthenticationRequiredException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val response = problemResponse(
            ProblemDefinition(
                HttpStatus.UNAUTHORIZED,
                URI.create("urn:govbiz:problem:authentication-required"),
                "Authentication Required",
                "A valid session token is required.",
                "AUTHENTICATION_REQUIRED",
            ),
            request,
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(response.body)
    }

    @ExceptionHandler(AdminRequiredException::class)
    fun handleAdminRequiredException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.FORBIDDEN,
                URI.create("urn:govbiz:problem:admin-required"),
                "Admin Required",
                "This operation requires an administrator account.",
                "ADMIN_REQUIRED",
            ),
            request,
        )

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFoundException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.NOT_FOUND,
                URI.create("urn:govbiz:problem:account-not-found"),
                "Account Not Found",
                "The requested account does not exist.",
                "ACCOUNT_NOT_FOUND",
            ),
            request,
        )

    @ExceptionHandler(RecruitmentPostNotFoundException::class)
    fun handleRecruitmentPostNotFoundException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.NOT_FOUND,
                URI.create("urn:govbiz:problem:recruitment-post-not-found"),
                "Recruitment Post Not Found",
                "The requested recruitment post does not exist or is no longer visible.",
                "RECRUITMENT_POST_NOT_FOUND",
            ),
            request,
        )

    @ExceptionHandler(NotPostOwnerException::class)
    fun handleNotPostOwnerException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.FORBIDDEN,
                URI.create("urn:govbiz:problem:not-post-owner"),
                "Not Post Owner",
                "Only the company that wrote this recruitment post can manage it.",
                "NOT_POST_OWNER",
            ),
            request,
        )

    @ExceptionHandler(RecruitmentPostNotOpenException::class)
    fun handleRecruitmentPostNotOpenException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.CONFLICT,
                URI.create("urn:govbiz:problem:recruitment-post-not-open"),
                "Recruitment Post Not Open",
                "This recruitment post is no longer open.",
                "RECRUITMENT_POST_NOT_OPEN",
            ),
            request,
        )

    @ExceptionHandler(SupportProgramNotOpenException::class)
    fun handleSupportProgramNotOpenException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.UNPROCESSABLE_CONTENT,
                URI.create("urn:govbiz:problem:support-program-not-open"),
                "Support Program Not Open",
                "The linked support program is not currently available or has closed.",
                "SUPPORT_PROGRAM_NOT_OPEN",
            ),
            request,
        )

    @ExceptionHandler(RecruitmentClosesOnInvalidException::class)
    fun handleRecruitmentClosesOnInvalidException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.UNPROCESSABLE_CONTENT,
                URI.create("urn:govbiz:problem:recruitment-closes-on-invalid"),
                "Recruitment Closing Date Invalid",
                "The recruitment closing date must be today or later and no later than the program deadline.",
                "RECRUITMENT_CLOSES_ON_INVALID",
            ),
            request,
        )

    @ExceptionHandler(ContactInTextException::class)
    fun handleContactInTextException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.UNPROCESSABLE_CONTENT,
                URI.create("urn:govbiz:problem:contact-in-text"),
                "Contact Details Not Allowed",
                "Email addresses and phone numbers must not be written in the text.",
                "CONTACT_IN_TEXT",
            ),
            request,
        )

    @ExceptionHandler(ProposalNotFoundException::class)
    fun handleProposalNotFoundException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.NOT_FOUND,
                URI.create("urn:govbiz:problem:proposal-not-found"),
                "Proposal Not Found",
                "The requested proposal does not exist.",
                "PROPOSAL_NOT_FOUND",
            ),
            request,
        )

    @ExceptionHandler(OwnPostProposalException::class)
    fun handleOwnPostProposalException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.FORBIDDEN,
                URI.create("urn:govbiz:problem:own-post"),
                "Own Post",
                "A company cannot send a proposal to its own recruitment post.",
                "OWN_POST",
            ),
            request,
        )

    @ExceptionHandler(NotProposalOwnerException::class)
    fun handleNotProposalOwnerException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.FORBIDDEN,
                URI.create("urn:govbiz:problem:not-proposal-owner"),
                "Not Proposal Owner",
                "Only the company that sent this proposal can withdraw it.",
                "NOT_PROPOSAL_OWNER",
            ),
            request,
        )

    @ExceptionHandler(ProposalAlreadyExistsException::class)
    fun handleProposalAlreadyExistsException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.CONFLICT,
                URI.create("urn:govbiz:problem:proposal-already-exists"),
                "Proposal Already Exists",
                "This company has already sent a proposal to this recruitment post.",
                "PROPOSAL_ALREADY_EXISTS",
            ),
            request,
        )

    @ExceptionHandler(ProposalNotPendingException::class)
    fun handleProposalNotPendingException(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problemResponse(
            ProblemDefinition(
                HttpStatus.CONFLICT,
                URI.create("urn:govbiz:problem:proposal-not-pending"),
                "Proposal Not Pending",
                "This proposal has already been decided, expired, or its recruitment post is closed.",
                "PROPOSAL_NOT_PENDING",
            ),
            request,
        )

    @ExceptionHandler(BiznoClientException::class)
    fun handleBiznoClientException(
        exception: BiznoClientException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problemResponse(definitionFor(exception.failure), request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val errors = java.util.List.copyOf(
            exception.bindingResult.fieldErrors.map(::toValidationError).distinct(),
        )

        return validationProblem(
            HttpStatus.BAD_REQUEST,
            URI.create("urn:govbiz:problem:request-validation-failed"),
            "Request Validation Failed",
            "One or more request fields are invalid.",
            "REQUEST_VALIDATION_FAILED",
            errors,
            request,
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidationException(
        exception: HandlerMethodValidationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val errors = java.util.List.copyOf(
            exception.parameterValidationResults
                .map { result ->
                    ValidationError(
                        result.methodParameter.parameterName ?: "request",
                        "INVALID_VALUE",
                    )
                }
                .distinct(),
        )

        return validationProblem(
            HttpStatus.BAD_REQUEST,
            URI.create("urn:govbiz:problem:request-validation-failed"),
            "Request Validation Failed",
            "One or more request fields are invalid.",
            "REQUEST_VALIDATION_FAILED",
            errors,
            request,
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        exception: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        validationProblem(
            HttpStatus.BAD_REQUEST,
            URI.create("urn:govbiz:problem:request-validation-failed"),
            "Request Validation Failed",
            "One or more request fields are invalid.",
            "REQUEST_VALIDATION_FAILED",
            listOf(ValidationError(exception.parameterName, "INVALID_VALUE")),
            request,
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        validationProblem(
            HttpStatus.BAD_REQUEST,
            URI.create("urn:govbiz:problem:request-validation-failed"),
            "Request Validation Failed",
            "The request body is invalid.",
            "REQUEST_VALIDATION_FAILED",
            emptyList(),
            request,
        )

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupportedException(
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        validationProblem(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            URI.create("urn:govbiz:problem:unsupported-media-type"),
            "Unsupported Media Type",
            "This endpoint accepts application/json requests.",
            "UNSUPPORTED_MEDIA_TYPE",
            emptyList(),
            request,
        )

    private fun validationProblem(
        status: HttpStatusCode,
        type: URI,
        title: String,
        detail: String,
        code: String,
        errors: List<ValidationError>,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val definition = ProblemDefinition(status, type, title, detail, code)
        return problemResponse(definition, request, errors)
    }

    private fun problemResponse(
        definition: ProblemDefinition,
        request: HttpServletRequest,
        errors: List<ValidationError>? = null,
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(definition.status, definition.detail)
        problem.type = definition.type
        problem.title = definition.title
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", definition.code)
        if (errors != null) {
            problem.setProperty("errors", errors)
        }

        return ResponseEntity.status(definition.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }

    private fun toValidationError(fieldError: FieldError): ValidationError =
        ValidationError(fieldError.field, "INVALID_VALUE")

    private fun definitionFor(failure: AiServiceFailure): ProblemDefinition =
        when (failure) {
            AiServiceFailure.UPSTREAM_ERROR -> ProblemDefinition(
                HttpStatus.BAD_GATEWAY,
                URI.create("urn:govbiz:problem:ai-service-upstream-error"),
                "AI Service Upstream Error",
                "AI Service returned an unexpected HTTP status.",
                "AI_SERVICE_UPSTREAM_ERROR",
            )
            AiServiceFailure.INVALID_RESPONSE -> ProblemDefinition(
                HttpStatus.BAD_GATEWAY,
                URI.create("urn:govbiz:problem:ai-service-invalid-response"),
                "AI Service Invalid Response",
                "AI Service returned an invalid response.",
                "AI_SERVICE_INVALID_RESPONSE",
            )
            AiServiceFailure.UNAVAILABLE -> ProblemDefinition(
                HttpStatus.SERVICE_UNAVAILABLE,
                URI.create("urn:govbiz:problem:ai-service-unavailable"),
                "AI Service Unavailable",
                "AI Service is currently unavailable.",
                "AI_SERVICE_UNAVAILABLE",
            )
            AiServiceFailure.TIMEOUT -> ProblemDefinition(
                HttpStatus.GATEWAY_TIMEOUT,
                URI.create("urn:govbiz:problem:ai-service-timeout"),
                "AI Service Gateway Timeout",
                "AI Service did not respond within the configured timeout.",
                "AI_SERVICE_TIMEOUT",
            )
        }

    private fun definitionFor(failure: BiznoClientException.Failure): ProblemDefinition =
        when (failure) {
            BiznoClientException.Failure.NOT_CONFIGURED -> ProblemDefinition(
                HttpStatus.SERVICE_UNAVAILABLE,
                URI.create("urn:govbiz:problem:bizno-not-configured"),
                "Bizno Not Configured",
                "Business registration lookup is not configured on this server.",
                "BIZNO_NOT_CONFIGURED",
            )
            BiznoClientException.Failure.UNAVAILABLE -> ProblemDefinition(
                HttpStatus.SERVICE_UNAVAILABLE,
                URI.create("urn:govbiz:problem:bizno-unavailable"),
                "Bizno Unavailable",
                "Business registration lookup is currently unavailable.",
                "BIZNO_UNAVAILABLE",
            )
            BiznoClientException.Failure.TIMEOUT -> ProblemDefinition(
                HttpStatus.GATEWAY_TIMEOUT,
                URI.create("urn:govbiz:problem:bizno-timeout"),
                "Bizno Gateway Timeout",
                "Business registration lookup did not respond within the configured timeout.",
                "BIZNO_TIMEOUT",
            )
            BiznoClientException.Failure.UPSTREAM_ERROR -> ProblemDefinition(
                HttpStatus.BAD_GATEWAY,
                URI.create("urn:govbiz:problem:bizno-upstream-error"),
                "Bizno Upstream Error",
                "Business registration lookup returned an unexpected result.",
                "BIZNO_UPSTREAM_ERROR",
            )
            BiznoClientException.Failure.INVALID_RESPONSE -> ProblemDefinition(
                HttpStatus.BAD_GATEWAY,
                URI.create("urn:govbiz:problem:bizno-invalid-response"),
                "Bizno Invalid Response",
                "Business registration lookup returned an invalid response.",
                "BIZNO_INVALID_RESPONSE",
            )
        }

    private data class ProblemDefinition(
        val status: HttpStatusCode,
        val type: URI,
        val title: String,
        val detail: String,
        val code: String,
    )

    private data class ValidationError(
        val field: String,
        val code: String,
    )
}
