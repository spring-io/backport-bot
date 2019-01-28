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

import io.spring.github.api.WebClientGitHubApi
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.filter.ForwardedHeaderFilter
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
class BackportBotApplication(val personalAccessToken: GitHubPersonalAccessToken) {

    @Bean
    fun authorizedClientRepository() : OAuth2AuthorizedClientRepository {
        return HttpSessionOAuth2AuthorizedClientRepository()
    }

    @Bean
    fun githubApi(clientRegistrationRepository: ClientRegistrationRepository,
                  authorizedClientRepository: OAuth2AuthorizedClientRepository) : WebClientGitHubApi {
        val webClient = WebClient.builder()
            .defaultHeaders { h -> h.setBearerAuth(personalAccessToken.personalAccessToken) }
            .apply(ServletOAuth2AuthorizedClientExchangeFilterFunction(clientRegistrationRepository, authorizedClientRepository).oauth2Configuration())
            .build()

        return WebClientGitHubApi(webClient)
    }

    @Bean
    fun forwardedHeaderFilter() : FilterRegistrationBean<ForwardedHeaderFilter> {
        val result = FilterRegistrationBean(ForwardedHeaderFilter())
        result.order = Ordered.HIGHEST_PRECEDENCE
        return result
    }
}

fun main(args: Array<String>) {
    runApplication<BackportBotApplication>(*args)
}
