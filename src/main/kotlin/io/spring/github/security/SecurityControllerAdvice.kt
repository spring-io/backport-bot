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
