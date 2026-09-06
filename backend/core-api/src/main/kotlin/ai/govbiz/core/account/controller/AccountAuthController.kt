package ai.govbiz.core.account.controller

import ai.govbiz.core.account.controller.dto.AuthSessionResponse
import ai.govbiz.core.account.controller.dto.CurrentAccountResponse
import ai.govbiz.core.account.controller.dto.LoginRequest
import ai.govbiz.core.account.controller.dto.SignupRequest
import ai.govbiz.core.account.service.AccountLoginService
import ai.govbiz.core.account.service.AccountSessionService
import ai.govbiz.core.account.service.AccountSignupService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AccountAuthController(
    private val signupService: AccountSignupService,
    private val loginService: AccountLoginService,
    private val sessionService: AccountSessionService,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@RequestBody @Valid request: SignupRequest): AuthSessionResponse =
        AuthSessionResponse.from(
            signupService.signUp(
                email = request.email,
                password = request.password,
                businessNumber = request.businessNumber,
            ),
        )

    @PostMapping("/login")
    fun logIn(@RequestBody @Valid request: LoginRequest): AuthSessionResponse =
        AuthSessionResponse.from(loginService.logIn(request.email, request.password))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logOut(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ) {
        sessionService.logOut(authorization)
    }

    @GetMapping("/me")
    fun me(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): CurrentAccountResponse =
        CurrentAccountResponse.from(sessionService.requireAccount(authorization))
}
