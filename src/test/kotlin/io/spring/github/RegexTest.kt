package io.spring.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author Rob Winch
 */
class RegexTest {
    @Test
    fun regex() {
        val r = """Fixes: gh\-\d+""".toRegex()
        assertThat(r.containsMatchIn("Hi\n\nHello\r\n\r\nFixes: gh-123")).isTrue()
    }
}