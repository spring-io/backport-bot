package io.spring.github.security

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.security.Principal

/**
 * @author Rob Winch
 */
@ControllerAdvice
class SecurityControllerAdvice {
    @ModelAttribute("currentUser")
    fun currentUser(@AuthenticationPrincipal principal : GitHubOAuth2UserService.GitHubUser?) : GitHubOAuth2UserService.GitHubUser? {
        return principal;
    }

}
