/*
 * Copyright 2002-2025 the original author or authors.
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

package io.spring.github

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * @author Rob Winch
 */
class RegexTest {

	@Test
	fun regex() {
		val r = """Fixes: gh-\d+""".toRegex()
		assertThat(r.containsMatchIn("Hi\n\nHello\r\n\r\nFixes: gh-123")).isTrue()
	}

	@Test
	fun nextLinkReplace() {
		var linkHeaderValue =
			"""<https://api.github.com/repositories/2090979/issues/10083/timeline?page=1>; rel="prev", <https://api.github.com/repositories/2090979/issues/10083/timeline?page=3>; rel="next", <https://api.github.com/repositories/2090979/issues/10083/timeline?page=3>; rel="last", <https://api.github.com/repositories/2090979/issues/10083/timeline?page=1>; rel="first""""

		val relPrevToken = """rel="prev", """

		val index = linkHeaderValue.indexOf(relPrevToken)
		if (index != -1) {
			linkHeaderValue = linkHeaderValue.substring(index + relPrevToken.length)
		}

		val nextRegex = """.*?<(.*?)>; rel="next".*"""
		assertThat(linkHeaderValue).matches(nextRegex)

		assertThatNoException().isThrownBy {
			URI.create(linkHeaderValue.replace(nextRegex.toRegex(), "$1"))
		}
	}

}