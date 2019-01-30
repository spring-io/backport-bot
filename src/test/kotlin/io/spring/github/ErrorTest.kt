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
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import javax.servlet.RequestDispatcher

/**
 * @author Rob Winch
 */
@SpringBootTest
@AutoConfigureMockMvc
@RunWith(SpringRunner::class)
class ErrorTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun errorWhenForbiddenThenCustomPageDisplayed() {

        val request = MockMvcRequestBuilders.get("/error")
                .accept(MediaType.TEXT_HTML)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/")
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, "Forbidden")

        val response = mockMvc.perform(request)
                .andReturn().response

        assertThat(response.contentAsString).contains("<title>BackportBot - Access Denied")
        assertThat(response.contentAsString).contains("To be authorized you must be in the <a href=\"https://github.com/spring-io\">spring-io</a> Spring team.")
    }
}