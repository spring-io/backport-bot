/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.github

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import io.spring.github.api.GitHubApi
import io.spring.github.api.Permission
import io.spring.github.webdriver.pages.IndexPage
import org.junit.Test
import org.junit.runner.RunWith
import org.openqa.selenium.WebDriver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponseWrapper

/**
 * @author Rob Winch
 */
@SpringBootTest
@AutoConfigureMockMvc
@RunWith(SpringRunner::class)
class IndexControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var webDriver: WebDriver

    @MockBean
    lateinit var gitHubApi: GitHubApi

    @MockBean
    lateinit var authzClient: OAuth2AuthorizedClientRepository

    @Test
    fun indexWhenNotAuthenticatedThenRequestsOAuth() {
        mockMvc.perform(get("/"))
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/github"))
    }

    @Test
    @WithMockUser
    fun indexWhenNotAuthorizedThenForbidden() {
        mockMvc.perform(get("/")).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = arrayOf("SPRING"))
    fun indexWhenAdminThenSuccess() {
        val token = OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-1", Instant.now(), Instant.now().plus(Duration.ofMinutes(5)))
        val registration = ClientRegistration.withRegistrationId("id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("client-id")
                .redirectUriTemplate("/login/oauth2/code/github")
                .authorizationUri("https://example.com/oauth2/token")
                .tokenUri("https://example.com/oauth2/token")
                .build()
        val result = OAuth2AuthorizedClient(registration, "rwinch", token)
        whenever(gitHubApi.saveHook(any())).thenReturn(Mono.empty())
        whenever(authzClient.loadAuthorizedClient<OAuth2AuthorizedClient>(any(), any(), any())).thenReturn(result)
        whenever(gitHubApi.getPermissionForDefaultLogin(any(), any())).thenReturn(Mono.just(Permission("spring-issuemaster", "admin")))
        val page = IndexPage.go(webDriver)
                .assertAt();

        page.addForm()
                .repositoryName("spring-projects/spring-security")
                .submit()
                .assertSuccess()
    }

    @Test
    @WithMockUser(roles = arrayOf("SPRING"))
    fun indexWhenWriteThenSuccess() {
        val token = OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-1", Instant.now(), Instant.now().plus(Duration.ofMinutes(5)))
        val registration = ClientRegistration.withRegistrationId("id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("client-id")
                .redirectUriTemplate("/login/oauth2/code/github")
                .authorizationUri("https://example.com/oauth2/token")
                .tokenUri("https://example.com/oauth2/token")
                .build()
        val result = OAuth2AuthorizedClient(registration, "rwinch", token)
        whenever(gitHubApi.saveHook(any())).thenReturn(Mono.empty())
        whenever(authzClient.loadAuthorizedClient<OAuth2AuthorizedClient>(any(), any(), any())).thenReturn(result)
        whenever(gitHubApi.getPermissionForDefaultLogin(any(), any())).thenReturn(Mono.just(Permission("spring-issuemaster", "write")))
        val page = IndexPage.go(webDriver)
                .assertAt();

        page.addForm()
                .repositoryName("spring-projects/spring-security")
                .submit()
                .assertSuccess()
    }

    @TestConfiguration
    class Go {
        @Bean
        fun go(): FilterRegistrationBean<Error> {
            val result = FilterRegistrationBean<Error>(Error())
            result.order = Ordered.HIGHEST_PRECEDENCE
            return result
        }

        class Error: Filter {
            override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
                chain.doFilter(request, ErrorWrapper(response as HttpServletResponse, request as HttpServletRequest))
            }
        }

        class ErrorWrapper(response: HttpServletResponse, val request: HttpServletRequest) : HttpServletResponseWrapper(response) {
            override fun sendError(sc: Int) {
//                super.sendError(sc)
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, sc)
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.requestURI)
                request.getRequestDispatcher("/error").forward(request, response)
            }

            override fun sendError(sc: Int, msg: String?) {
//                super.sendError(sc, msg)
                status = sc
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, sc)
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.requestURI)
                request.setAttribute(RequestDispatcher.ERROR_MESSAGE, msg)
                request.getRequestDispatcher("/error").forward(request, response)
            }
        }
    }
}