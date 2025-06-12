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

import org.mockito.kotlin.*
import io.spring.github.api.*
import io.spring.github.event.DefaultBackportService
import io.spring.github.event.IssueEvent
import io.spring.github.event.PushEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.charset.Charset

/**
 * @author Rob Winch
 * @author Artem Bilan
 */
class DefaultBackportServiceTests {
    val github : GitHubApi = mock()
    val backport = DefaultBackportService(github)
    val milestoneNumber = 10
    val repository = IssueEvent.Repository("rwinch/test")
    val sender = IssueEvent.Sender("rwinch")
    val backportLabel = IssueEvent.Label("for: backport-to-1.0.x")
    val issue = Issue(1, "Title", Issue.Milestone(milestoneNumber), listOf(Issue.Label("Label")))
    val event = IssueEvent("labeled", repository, sender, backportLabel, issue)
    val pushEvent = PushEvent("refs/head/5.1.x", PushEvent.Repository(repository.fullName), PushEvent.Pusher("rwinch"), listOf(PushEvent.Commit("sha", "Fixes: gh-1")))
    val repositoryRef = RepositoryRef(repository.fullName)
    val branchRef = BranchRef(repositoryRef, "1.0.x")
    val issueRef = IssueRef(repositoryRef, issue.number)

    val timelineIssue = TimelineEvent.Issue(1, "Backport of gh-${issue.number}", TimelineEvent.Milestone(milestoneNumber, "title"))

    val timelineSource = TimelineEvent.Source("issue", timelineIssue)

    val backportTimelineEvent = TimelineEvent("cross-referenced", timelineSource)

    @Test
    fun findBranchNameByLabelNameWhenNotMatchThenNull() {
        assertThat(this.backport.findBranchNameByLabelName("label").block()).isNull()
    }

    @Test
    fun findBranchNameByLabelNameWhenMatchThenFound() {
        assertThat(this.backport.findBranchNameByLabelName(backportLabel.name).block()).isEqualTo("1.0.x")
    }

    @Test
    fun removeLabelWhenNoIssueThenOk() {
        whenever(this.github.findIssue(any())).thenReturn(Mono.empty<Issue>())
        this.backport.removeLabel(issueRef, "remove").block()
    }

    @Test
    fun removeLabelWhenLabelNotFoundThenOk() {
        whenever(this.github.findIssue(any())).thenReturn(Mono.just(issue))
        whenever(this.github.updateLabels(any(), any())).thenReturn(Mono.empty())

        this.backport.removeLabel(issueRef, "remove").block()

        val issueArg = argumentCaptor<IssueRef>()
        val labelArg = argumentCaptor<List<String>>()
        verify(this.github).updateLabels(issueArg.capture(), labelArg.capture())
        assertThat(issueArg.firstValue).isEqualTo(issueRef)
        assertThat(labelArg.firstValue).isEqualTo(issue.labels.map { l -> l.name })

    }

    @Test
    fun removeLabelWhenLabelFoundThenOk() {
        val issue = Issue(this.issue.number, this.issue.title, this.issue.milestone, listOf(Issue.Label("Label"), Issue.Label("remove")))
        whenever(this.github.findIssue(any())).thenReturn(Mono.just(issue))
        whenever(this.github.updateLabels(any(), any())).thenReturn(Mono.empty())

        this.backport.removeLabel(issueRef, "remove").block()

        val issueArg = argumentCaptor<IssueRef>()
        val labelArg = argumentCaptor<List<String>>()
        verify(this.github).updateLabels(issueArg.capture(), labelArg.capture())
        assertThat(issueArg.firstValue).isEqualTo(issueRef)
        assertThat(labelArg.firstValue).containsOnly("Label")

    }

    @Test
    fun findMilestoneNumberWhenFindFileEmptyThenError() {
        whenever(github.findFile(branchRef, "gradle.properties")).thenReturn(Mono.empty())
        whenever(github.findFile(branchRef, "pom.xml")).thenReturn(Mono.empty())

        StepVerifier.create(backport.findMilestoneNumber(branchRef))
            .verifyErrorSatisfies {e -> assertThat(e).hasMessage("Cannot find 'gradle.properties' or 'pom.xml' for BranchRef(repository=RepositoryRef(fullName=rwinch/test), ref=1.0.x)")}
    }

    @Test
    fun findMavenMilestoneNumber() {
        whenever(github.findFile(branchRef, "gradle.properties")).thenReturn(Mono.empty())
        whenever(github.findFile(branchRef, "pom.xml"))
            .thenReturn(Mono.just("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                	<properties>
                		<revision>1.1.0-SNAPSHOT</revision>
                	</properties>
                </project>
            """.trimIndent().byteInputStream(Charset.defaultCharset())))
        whenever(github.findMilestoneNumberByTitle(repositoryRef, "1.1.0")).thenReturn(Mono.just(1))

        StepVerifier.create(backport.findMilestoneNumber(branchRef))
            .expectNext(1)
            .verifyComplete()
    }

    @Test
    fun findMilestoneNumberWhenMilestoneNumberNotFound() {
        whenever(github.findFile(branchRef, "gradle.properties")).thenReturn(Mono.just("version=1.1.0.BUILD-SNAPSHOT".byteInputStream(Charset.defaultCharset())))
        whenever(github.findMilestoneNumberByTitle(repositoryRef, "1.1.0")).thenReturn(Mono.empty())

        StepVerifier.create(backport.findMilestoneNumber(branchRef))
            .verifyErrorSatisfies {e -> assertThat(e).hasMessage("Cannot find milestone number for title 1.1.0 and RepositoryRef(fullName=rwinch/test)")}
    }

    @Test
    fun findMilestoneNumberWhenMilestoneNumberBuildSnapshotFound() {
        whenever(github.findFile(branchRef, "gradle.properties")).thenReturn(Mono.just("version=1.1.0.BUILD-SNAPSHOT".byteInputStream(Charset.defaultCharset())))
        whenever(github.findMilestoneNumberByTitle(repositoryRef, "1.1.0")).thenReturn(Mono.just(1))

        StepVerifier.create(backport.findMilestoneNumber(branchRef))
                .expectNext(1)
                .verifyComplete()
    }

    @Test
    fun findMilestoneNumberWhenMilestoneNumberSnapshotFound() {
        whenever(github.findFile(branchRef, "gradle.properties")).thenReturn(Mono.just("version=1.1.0-SNAPSHOT".byteInputStream(Charset.defaultCharset())))
        whenever(github.findMilestoneNumberByTitle(repositoryRef, "1.1.0")).thenReturn(Mono.just(1))

        StepVerifier.create(backport.findMilestoneNumber(branchRef))
                .expectNext(1)
                .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineEmptyThenFalse() {
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.empty())
        whenever(github.findIssue(any())).thenReturn(Mono.just(Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber + 1), issue.labels)))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineWrongEventThenFalse() {
        val issueNotSameMilestone = Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber + 1), issue.labels)
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.just(TimelineEvent("foo", timelineSource)))
        whenever(github.findIssue(any())).thenReturn(Mono.just(issueNotSameMilestone))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineNullSourceThenFalse() {
        val issueNotSameMilestone = Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber + 1), issue.labels)
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.just(TimelineEvent(backportTimelineEvent.event, null)))
        whenever(github.findIssue(any())).thenReturn(Mono.just(issueNotSameMilestone))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineNullIssueThenFalse() {
        val issueNotSameMilestone = Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber + 1), issue.labels)
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.just(TimelineEvent(backportTimelineEvent.event, TimelineEvent.Source(timelineSource.type, null))))
        whenever(github.findIssue(any())).thenReturn(Mono.just(issueNotSameMilestone))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineNullMilestoneThenFalse() {
        val issueNotSameMilestone = Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber + 1), issue.labels)
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.just(TimelineEvent(backportTimelineEvent.event, TimelineEvent.Source(timelineSource.type, TimelineEvent.Issue(timelineIssue.number, timelineIssue.body, null)))))
        whenever(github.findIssue(any())).thenReturn(Mono.just(issueNotSameMilestone))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
                .expectNext(false)
                .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineMilestoneDifferentThenFalse() {
        val issueNotSameMilestone = Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber + 1), issue.labels)
        val timelineIssue = TimelineEvent.Issue(1, "No Match", TimelineEvent.Milestone(milestoneNumber, "title"))
        val timelineSource = TimelineEvent.Source(timelineSource.type, timelineIssue)
        val backportTimelineEvent = TimelineEvent("cross-referenced", timelineSource)
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.just(backportTimelineEvent))
        whenever(github.findIssue(any())).thenReturn(Mono.just(issueNotSameMilestone))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineBodyNotMatchThenFalse() {
        val issueNotSameMilestone = Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber + 1), issue.labels)
        val timelineIssue = TimelineEvent.Issue(1, this.timelineIssue.body, TimelineEvent.Milestone(milestoneNumber + 1, "title"))
        val timelineSource = TimelineEvent.Source(timelineSource.type, timelineIssue)
        val backportTimelineEvent = TimelineEvent("cross-referenced", timelineSource)
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.just(backportTimelineEvent))
        whenever(github.findIssue(any())).thenReturn(Mono.just(issueNotSameMilestone))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
                .expectNext(false)
                .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenTimelineMatchThenTrue() {
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.just(backportTimelineEvent))
        whenever(github.findIssue(any())).thenReturn(Mono.just(Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber+1), issue.labels)))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
                .expectNext(true)
                .verifyComplete()
    }

    @Test
    fun isIssueForMilestoneWhenIssueMatchThenTrue() {
        whenever(github.findIssueTimeline(issueRef)).thenReturn(Flux.empty())
        whenever(github.findIssue(any())).thenReturn(Mono.just(issue))

        StepVerifier.create(backport.isIssueForMilestone(issueRef, milestoneNumber))
                .expectNext(true)
                .verifyComplete()
    }

    @Test
    fun findBackportedIssueForMilestoneNumberWhenSameMilestoneThenFound() {
        whenever(this.github.findIssue(any())).thenReturn(Mono.just(Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber - 1), issue.labels)))
        whenever(this.github.findIssueTimeline(any())).thenReturn(Flux.just(backportTimelineEvent))

        assertThat(this.backport.findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber).block()).isEqualTo(IssueRef(repositoryRef, backportTimelineEvent.source?.issue?.number!!))
    }

    @Test
    fun findBackportedIssueForMilestoneNumberWhenIssueSameMilestoneThenFound() {
        whenever(this.github.findIssue(any())).thenReturn(Mono.just(Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber - 1), issue.labels)))
        whenever(this.github.findIssueTimeline(any())).thenReturn(Flux.just(backportTimelineEvent))

        assertThat(this.backport.findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber - 1).block()).isEqualTo(issueRef)
    }

    @Test
    fun findBackportedIssueForMilestoneNumberWhenDiffMilestoneThenNull() {
        whenever(this.github.findIssue(any())).thenReturn(Mono.just(Issue(issue.number, issue.title, Issue.Milestone(milestoneNumber - 1), issue.labels)))
        whenever(this.github.findIssueTimeline(any())).thenReturn(Flux.just(backportTimelineEvent))

        assertThat(this.backport.findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber + 1).block()).isNull()
    }

    @Test
    fun createBackportWhenFindIssueEmptyThenError() {
        whenever(github.findIssue(any())).thenReturn(Mono.empty())

        StepVerifier.create(backport.createBackport(issueRef, milestoneNumber, listOf(pushEvent.pusher.name)))
            .verifyErrorSatisfies {e -> assertThat(e).hasMessage("Cannot find issue IssueRef(repository=RepositoryRef(fullName=rwinch/test), number=1)")}
    }

    @Test
    fun createBackportWhenFindIssueThenSuccess() {
        whenever(github.findIssue(any())).thenReturn(Mono.just(issue))
        whenever(github.updateLabels(any(), any())).thenReturn(Mono.empty())
        whenever(github.createIssue(any())).thenReturn(Mono.just(IssueRef(repositoryRef, issue.number + 1)))
        whenever(github.comment(any(), any())).thenReturn(Mono.empty())
        whenever(github.closeIssue(any())).thenReturn(Mono.empty())

        StepVerifier.create(backport.createBackport(issueRef, milestoneNumber, listOf(pushEvent.pusher.name)))
                .expectNext(IssueRef(repositoryRef,2))
                .verifyComplete()

        val labelArgs = argumentCaptor<List<String>>()
        val createIssueArg = argumentCaptor<CreateIssue>()

        verify(github).updateLabels(eq(issueRef), labelArgs.capture())
        verify(github).createIssue(createIssueArg.capture())

        assertThat(labelArgs.firstValue).containsAll(issue.labels.map { n -> n.name } + "status: backported")
        createIssueArg.firstValue.apply {
            assertThat(ref).isEqualTo(issueRef.repository)
            assertThat(title).isEqualTo(issue.title)
            assertThat(body).isEqualTo("Backport of gh-${issue.number}")
            assertThat(milestone).isEqualTo(milestoneNumber)
            assertThat(labels).containsAll(issue.labels.map { n -> n.name } + "type: backport")
            assertThat(assignees).containsOnly(pushEvent.pusher.name)
        }
    }

    @Test
    fun createBackportWhenNoAssigneeThenSuccess() {
        whenever(github.findIssue(any())).thenReturn(Mono.just(issue))
        whenever(github.updateLabels(any(), any())).thenReturn(Mono.empty())
        whenever(github.createIssue(any())).thenReturn(Mono.just(IssueRef(repositoryRef,issue.number + 1)))
        whenever(github.comment(any(), any())).thenReturn(Mono.empty())
        whenever(github.closeIssue(any())).thenReturn(Mono.empty())

        StepVerifier.create(backport.createBackport(issueRef, milestoneNumber, listOf()))
                .expectNext(IssueRef(repositoryRef,2))
                .verifyComplete()

        val labelArgs = argumentCaptor<List<String>>()
        val createIssueArg = argumentCaptor<CreateIssue>()

        verify(github).updateLabels(eq(issueRef), labelArgs.capture())
        verify(github).createIssue(createIssueArg.capture())

        assertThat(labelArgs.firstValue).containsAll(issue.labels.map { n -> n.name } + "status: backported")
        createIssueArg.firstValue.apply {
            assertThat(ref).isEqualTo(issueRef.repository)
            assertThat(title).isEqualTo(issue.title)
            assertThat(body).isEqualTo("Backport of gh-${issue.number}")
            assertThat(milestone).isEqualTo(milestoneNumber)
            assertThat(labels).containsAll(issue.labels.map { n -> n.name } + "type: backport")
            assertThat(assignees).isEmpty()
        }
    }

    // gh-9
    @Test
    fun createBackportWhenHasIsBackportedThenBackportNotIsBackported() {
        val issue = Issue(issue.number, issue.title, issue.milestone, issue.labels + Issue.Label("status: backported"))
        whenever(github.findIssue(any())).thenReturn(Mono.just(issue))
        whenever(github.updateLabels(any(), any())).thenReturn(Mono.empty())
        whenever(github.createIssue(any())).thenReturn(Mono.just(IssueRef(repositoryRef,issue.number + 1)))
        whenever(github.comment(any(), any())).thenReturn(Mono.empty())
        whenever(github.closeIssue(any())).thenReturn(Mono.empty())

        StepVerifier.create(backport.createBackport(issueRef, milestoneNumber, listOf(pushEvent.pusher.name)))
                .expectNext(IssueRef(repositoryRef,2))
                .verifyComplete()

        val labelArgs = argumentCaptor<List<String>>()
        val createIssueArg = argumentCaptor<CreateIssue>()

        verify(github).updateLabels(eq(issueRef), labelArgs.capture())
        verify(github).createIssue(createIssueArg.capture())

        assertThat(labelArgs.firstValue).containsAll(issue.labels.map { n -> n.name } + "status: backported")
        createIssueArg.firstValue.apply {
            assertThat(ref).isEqualTo(issueRef.repository)
            assertThat(title).isEqualTo(issue.title)
            assertThat(body).isEqualTo("Backport of gh-${issue.number}")
            assertThat(milestone).isEqualTo(milestoneNumber)
            assertThat(labels).containsAll(issue.labels.map { n -> n.name } - "status: backported" + "type: backport")
            assertThat(assignees).containsOnly(pushEvent.pusher.name)
        }
    }

    @Test
    fun closeBackport() {
        whenever(github.comment(any(), any())).thenReturn(Mono.empty())
        whenever(github.closeIssue(any())).thenReturn(Mono.empty())

        StepVerifier.create(backport.closeBackport(issueRef, "123"))
            .verifyComplete()

        verify(github).comment(issueRef, "Fixed via 123")
        verify(github).closeIssue(issueRef)
    }

    @Test
    fun findBackportBranches() {
        val backportLabelName = backportLabel.name
        whenever(github.findLabels(any())).thenReturn(Flux.just(Label("not"), Label(backportLabelName)))
        val labels = backport.findBackportBranches(repositoryRef)
                .map { b -> b.ref }
                .collectList()
                .block()

        assertThat(labels).containsOnly("refs/heads/1.0.x")
    }
}