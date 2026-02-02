/*
 * Copyright 2002-present the original author or authors.
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

import io.spring.github.api.WebClientGitHubApi
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
class BackportBotApplication {
    @Bean
    internal fun webClientGitHubApi(gitHubAccessToken: GitHubAccessToken) : WebClientGitHubApi {
        val webClient = WebClient.builder()
                .defaultHeaders { headers -> headers.setBearerAuth(gitHubAccessToken.accessToken) }
                .build()
        return WebClientGitHubApi(webClient)
    }

}

fun main(args: Array<String>) {
    runApplication<BackportBotApplication>(*args)
}
