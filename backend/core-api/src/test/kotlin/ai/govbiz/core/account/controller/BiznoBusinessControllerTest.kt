package ai.govbiz.core.account.controller

import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.account.client.bizno.BiznoClient
import ai.govbiz.core.account.client.bizno.dto.BiznoBusiness
import ai.govbiz.core.account.client.bizno.exception.BiznoClientException
import ai.govbiz.core.account.service.BiznoBusinessService
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.stream.Stream
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class BiznoBusinessControllerTest {

    @Mock
    private lateinit var biznoClient: BiznoClient

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(BiznoBusinessController(BiznoBusinessService(biznoClient)))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun normalizesHyphenatedInputAndReturnsThePublicContract() {
        Mockito.doReturn(listOf(BiznoBusiness("1248100998", "삼성전자(주)", "계속사업자")))
            .`when`(biznoClient)
            .findByBusinessNumber("1248100998")

        mockMvc.perform(get(PATH).queryParam("businessNumber", "124-81-00998"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.businesses.length()").value(1))
            .andExpect(jsonPath("$.businesses[0].businessNumber").value("1248100998"))
            .andExpect(jsonPath("$.businesses[0].companyName").value("삼성전자(주)"))
            .andExpect(jsonPath("$.businesses[0].businessStatus").value("계속사업자"))
    }

    @Test
    fun returnsAnEmptyListWhenNoRegisteredBusinessMatches() {
        Mockito.doReturn(emptyList<BiznoBusiness>()).`when`(biznoClient).findByBusinessNumber("1234567890")

        mockMvc.perform(get(PATH).queryParam("businessNumber", "1234567890"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businesses").isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["123456789", "12345678901", "124-81-0099a", "124-81-00998-", " 1248100998"])
    fun rejectsBusinessNumbersThatAreNotTenDigits(businessNumber: String) {
        mockMvc.perform(get(PATH).queryParam("businessNumber", businessNumber))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("businessNumber"))

        Mockito.verifyNoInteractions(biznoClient)
    }

    @Test
    fun requiresTheBusinessNumberParameter() {
        mockMvc.perform(get(PATH))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("businessNumber"))
    }

    @ParameterizedTest
    @MethodSource("problemCases")
    fun mapsEveryBiznoFailureToAStableProblem(
        clientException: BiznoClientException,
        expectedStatus: Int,
        expectedCode: String,
        expectedType: String,
    ) {
        Mockito.doThrow(clientException).`when`(biznoClient).findByBusinessNumber("1248100998")

        mockMvc.perform(get(PATH).queryParam("businessNumber", "1248100998"))
            .andExpect(status().`is`(expectedStatus))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(expectedStatus))
            .andExpect(jsonPath("$.instance").value(PATH))
            .andExpect(jsonPath("$.code").value(expectedCode))
            .andExpect(jsonPath("$.type").value(expectedType))
            .andExpect(content().string(not(containsString("do not expose this"))))
            .andExpect(content().string(not(containsString("bizno.net"))))
            .andExpect(content().string(not(containsString("result code"))))
    }

    private companion object {
        const val PATH = "/api/v1/auth/businesses/lookup"

        @JvmStatic
        fun problemCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    BiznoClientException.notConfigured(),
                    503,
                    "BIZNO_NOT_CONFIGURED",
                    "urn:govbiz:problem:bizno-not-configured",
                ),
                Arguments.of(
                    BiznoClientException.unavailable(ConnectException("bizno.net:443")),
                    503,
                    "BIZNO_UNAVAILABLE",
                    "urn:govbiz:problem:bizno-unavailable",
                ),
                Arguments.of(
                    BiznoClientException.timeout(SocketTimeoutException("do not expose this")),
                    504,
                    "BIZNO_TIMEOUT",
                    "urn:govbiz:problem:bizno-timeout",
                ),
                Arguments.of(
                    BiznoClientException.upstreamError("Bizno API returned result code -1", null),
                    502,
                    "BIZNO_UPSTREAM_ERROR",
                    "urn:govbiz:problem:bizno-upstream-error",
                ),
                Arguments.of(
                    BiznoClientException.invalidResponse("do not expose this", IllegalStateException("do not expose this")),
                    502,
                    "BIZNO_INVALID_RESPONSE",
                    "urn:govbiz:problem:bizno-invalid-response",
                ),
            )
    }
}
