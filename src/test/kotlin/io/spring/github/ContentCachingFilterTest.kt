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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 * @author Rob Winch
 */
class ContentCachingFilterTest {

    @Test
    fun getInputStreamWhenTwiceThenStillWorks() {
        val filter = ContentCachingFilter()
        val controller = @RestController object {
            var request: HttpServletRequest? = null
            @PostMapping("/")
            fun index(request: HttpServletRequest) : String {
                this.request = request
                return "ok"
            }

        }
        val mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters<StandaloneMockMvcBuilder>(filter)
                .build()

        val expectedBody = "hi"
        mvc.perform(post("/").content(expectedBody))

        val body = controller.request?.inputStream?.bufferedReader()?.readText()
        assertThat(body).isEqualTo(expectedBody)
        val body2 = controller.request?.inputStream?.bufferedReader()?.readText()
        assertThat(body2).isEqualTo(expectedBody)
    }
}