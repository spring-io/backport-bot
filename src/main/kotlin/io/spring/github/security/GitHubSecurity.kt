package io.spring.github.security

import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

/**
 * @author Rob Winch
 */
@Component
class GitHubSecurity(val gitHubSignature: GitHubSignature) {
    fun check(request : HttpServletRequest) : Boolean {
        val body = request.inputStream.bufferedReader().readText()
        val signature = request.getHeader("X-Hub-Signature")

        return gitHubSignature.check(signature, body)
    }
}