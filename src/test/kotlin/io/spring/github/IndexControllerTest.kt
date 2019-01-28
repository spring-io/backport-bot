/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.github

import com.gargoylesoftware.htmlunit.WebClient
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import io.spring.github.api.GitHubApi
import io.spring.github.api.Permission
import io.spring.github.webdriver.pages.IndexPage
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openqa.selenium.WebDriver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * @author Rob Winch
 */
@SpringBootTest
@AutoConfigureMockMvc
@RunWith(SpringRunner::class)
class IndexControllerTest {
    @Autowired
    lateinit var webDriver: WebDriver

    @MockBean
    lateinit var gitHubApi: GitHubApi

    @MockBean
    lateinit var authzClient: OAuth2AuthorizedClientRepository

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
}