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
    fun admin(@RequestParam fullName: String, request: HttpServletRequest, @RegisteredOAuth2AuthorizedClient authorizedClient: OAuth2AuthorizedClient): String {
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
        gitHubApi.saveHook(SaveHook(repository, config, listOf("issues", "push")))
                .block()

        return "redirect:/?success"
    }
}