package io.spring.github.security

import io.spring.github.GitHubWebHookSecret
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * @author Rob Winch
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(val userService : OAuth2UserService<OAuth2UserRequest,OAuth2User>) : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        val requiresSecureMatcher = RequestMatcher() {
            request -> request.getHeader("x-forwarded-port") != null
        }
        http
            .requiresChannel()
                .requestMatchers(requiresSecureMatcher).requiresSecure()
                .and()
            .csrf()
                .ignoringAntMatchers("/events/**")
                .and()
            .authorizeRequests()
                .mvcMatchers("/events/**").access("@gitHubSecurity.check(request)")
                .anyRequest().hasRole("SPRING")
                .and()
            .oauth2Login()
                .userInfoEndpoint()
                    .userService(this.userService)
    }

    @Bean
    fun githubSignature(webHookSecret: GitHubWebHookSecret) : GitHubSignature {
        return GitHubSignature(webHookSecret.webhookSecret)
    }
}