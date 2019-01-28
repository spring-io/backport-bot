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