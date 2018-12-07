package io.spring.github

import io.spring.github.api.*
import org.assertj.core.api.Assertions.*
import org.junit.Test

/**
 * @author Rob Winch
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

    fun pushEvent(commitMessage : String) : PushEvent {
        val commits = listOf(PushEvent.Commit("sha", commitMessage))
        return PushEvent("master", PushEvent.Repository("spring-projects/spring-security"), PushEvent.Pusher("rwinch"), commits)
    }
}