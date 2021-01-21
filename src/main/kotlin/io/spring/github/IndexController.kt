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

import io.spring.github.api.SaveHook
import io.spring.github.api.GitHubApi
import io.spring.github.api.RepositoryRef
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.util.UriComponentsBuilder
import java.lang.IllegalStateException
import java.security.Principal
import javax.servlet.http.HttpServletRequest

/**
 * @author Rob Winch
 */
@Controller
class IndexController(val gitHubApi: GitHubApi, val webHookSecret: GitHubWebHookSecret) {
    @GetMapping("/")
    fun index(p : Principal) : String {
        return "index"
    }

    @PostMapping("/")
    fun admin(@RequestParam fullName: String, request: HttpServletRequest, @RegisteredOAuth2AuthorizedClient("github") authorizedClient: OAuth2AuthorizedClient): String {
        val hooksUrl = UriComponentsBuilder.fromHttpRequest(ServletServerHttpRequest(request))
                .replacePath("/events/")
                .build()
                .toUriString()
        val config = SaveHook.Config(hooksUrl, webHookSecret.webhookSecret)
        val repository = RepositoryRef(fullName)
        val permission = gitHubApi.getPermissionForDefaultLogin(repository, authorizedClient.accessToken.tokenValue).block()!!
        val p = permission.permission
        if (p != "admin" && p != "write") {
            throw IllegalStateException("The configured user to manage issues, ${permission.login}, requires admin or write permission to the $fullName repository but has $p")
        }
        gitHubApi.saveHook(SaveHook(repository, config, listOf("issues", "pull_request", "push")))
                .block()

        return "redirect:/?success"
    }
}