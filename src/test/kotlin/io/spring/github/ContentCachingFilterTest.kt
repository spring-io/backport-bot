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