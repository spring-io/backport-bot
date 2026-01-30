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

import io.spring.github.event.PushEvent
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * @author Rob Winch
 * @author Artem Bilan
 */
class PushEventTest {

	@Test
	fun getFixedIssueIdsWhenOnlyFixesThenFindValue() {
		val e = pushEvent("Fixes: gh-123")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndFixesThenFindValue() {
		val e = pushEvent("Subject\n\nFixes: gh-123")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndHashNumberThenFindValues() {
		val e = pushEvent("Subject\n\nFixes: #123")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndMissingColonThenFindValues() {
		val e = pushEvent("Subject\n\nFixes gh-123")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndMutliSpaceThenFindValues() {
		val e = pushEvent("Subject\n\nFixes:  gh-123")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndEndsInNewLineThenFindValues() {
		val e = pushEvent("Subject\n\nFixes:  gh-123\n")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndEndsInNewLinesThenFindValues() {
		val e = pushEvent("Subject\n\nFixes:  gh-123\n\n")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndEndsInWindowsNewLineThenFindValues() {
		val e = pushEvent("Subject\n\nFixes:  gh-123\r\n")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndUsesClosesThenFindValues() {
		val e = pushEvent("Subject\n\nCloses: gh-123\r\n")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndUsesClosesAndNoColonThenFindValues() {
		val e = pushEvent("Subject\n\nCloses gh-123\r\n")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenSubjectAndUsesLowercaseClosesThenFindValues() {
		val e = pushEvent("Subject\n\ncloses gh-123\r\n")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenExtraSpacesThenFindValues() {
		val e = pushEvent("Subject\n\n  Closes gh-123  \r\n")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdsWhenNotLastLineThenFindValues() {
		val e = pushEvent(
			"Fix Javadoc typos\n" +
					"\n" +
					"Closes gh-22261\n" +
					"\n" +
					"(cherry picked from commit 9837ec5)"
		)
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(22261)
	}

	@Test
	fun getFixedIssueIdWhenSubjectAndIssueLinkThenFindValues() {
		val e = pushEvent("Subject\n\nFixes: https://github.com/some-org/some-project/issues/123")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun getFixedIssueIdWhenSubjectAndPullRequestLinkThenFindValues() {
		val e = pushEvent("Subject\n\nFixes: https://github.com/some-org/some-project/pull/123")
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}

	@Test
	fun regexPerLine() {
		val message = """
			Subject
			
        	Some text
		
        	Fixes: #123
		
        	More text
    		"""
		val e = pushEvent(message)
		assertThat(e.getFixCommits().map { c -> c.getFixIssueId() }).containsOnly(123)
	}


	fun pushEvent(commitMessage: String): PushEvent {
		val commits = listOf(PushEvent.Commit("sha", commitMessage))
		return PushEvent(
			"main",
			PushEvent.Repository("spring-projects/spring-security"),
			PushEvent.Pusher("rwinch"),
			commits
		)
	}
}
