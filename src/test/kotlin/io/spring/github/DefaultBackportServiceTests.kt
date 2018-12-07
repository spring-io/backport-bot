package io.spring.github

import com.nhaarman.mockitokotlin2.*
import io.spring.github.api.*
import io.spring.github.event.DefaultBackportService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.charset.Charset

/**
 * @author Rob Winch
 */
class DefaultBackportServiceTests {
    val github : GitHubApi = mock()
    val backport = DefaultBackportService(github)
    val milestoneNumber = 10
    val repository = IssueEvent.Repository("rwinch/test")
    val sender = IssueEvent.Sender("rwinch")
    val backportLabel = IssueEvent.Label("backport: 1.0.x")
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
        assertThat(this.backport.findBranchNameByLabelName("backport: 1.0.x").block()).isEqualTo("1.0.x")
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

        StepVerifier.create(backport.findMilestoneNumber(branchRef))
            .verifyErrorSatisfies {e -> assertThat(e).hasMessage("Cannot find file gradle.properties for BranchRef(repository=RepositoryRef(fullName=rwinch/test), ref=1.0.x)")}
    }

    @Test
    fun findMilestoneNumberWhenMilestoneNumberNotFound() {
        whenever(github.findFile(branchRef, "gradle.properties")).thenReturn(Mono.just("version=1.1.0.BUILD-SNAPSHOT".byteInputStream(Charset.defaultCharset())))
        whenever(github.findMilestoneNumberByTitle(repositoryRef, "1.1.0")).thenReturn(Mono.empty())

        StepVerifier.create(backport.findMilestoneNumber(branchRef))
            .verifyErrorSatisfies {e -> assertThat(e).hasMessage("Cannot find milestone number for title 1.1.0 and RepositoryRef(fullName=rwinch/test)")}
    }

    @Test
    fun findMilestoneNumberWhenMilestoneNumberFound() {
        whenever(github.findFile(branchRef, "gradle.properties")).thenReturn(Mono.just("version=1.1.0.BUILD-SNAPSHOT".byteInputStream(Charset.defaultCharset())))
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
        whenever(this.github.findIssueTimeline(any())).thenReturn(Flux.just(backportTimelineEvent))

        assertThat(this.backport.findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber).block()).isEqualTo(backportTimelineEvent.source?.issue?.number)
    }

    @Test
    fun findBackportedIssueForMilestoneNumberWhenDiffMilestoneThenNull() {
        whenever(this.github.findIssueTimeline(any())).thenReturn(Flux.just(backportTimelineEvent))

        assertThat(this.backport.findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber + 1).block()).isNull()
    }

    @Test
    fun createBackportWhenFindIssueEmptyThenError() {
        whenever(github.findIssue(any())).thenReturn(Mono.empty())

        StepVerifier.create(backport.createBackport(issueRef, milestoneNumber, pushEvent.pusher.name))
            .verifyErrorSatisfies {e -> assertThat(e).hasMessage("Cannot find issue IssueRef(repository=RepositoryRef(fullName=rwinch/test), number=1)")}
    }

    @Test
    fun createBackportWhenFindIssueThenSuccess() {
        whenever(github.findIssue(any())).thenReturn(Mono.just(issue))
        whenever(github.updateLabels(any(), any())).thenReturn(Mono.empty())
        whenever(github.createIssue(any())).thenReturn(Mono.just(issue.number + 1))
        whenever(github.comment(any(), any())).thenReturn(Mono.empty())
        whenever(github.closeIssue(any())).thenReturn(Mono.empty())

        StepVerifier.create(backport.createBackport(issueRef, milestoneNumber, pushEvent.pusher.name))
            .expectNext(2)
            .verifyComplete()

        val labelArgs = argumentCaptor<List<String>>()
        val createIssueArg = argumentCaptor<CreateIssue>()

        verify(github).updateLabels(eq(issueRef), labelArgs.capture())
        verify(github).createIssue(createIssueArg.capture())

        assertThat(labelArgs.firstValue).containsOnlyElementsOf(issue.labels.map { n -> n.name } + "is: backported")
        createIssueArg.firstValue.apply {
            assertThat(ref).isEqualTo(issueRef.repository)
            assertThat(title).isEqualTo(issue.title)
            assertThat(body).isEqualTo("Backport of gh-${issue.number}")
            assertThat(milestone).isEqualTo(milestoneNumber)
            assertThat(labels).containsOnlyElementsOf(issue.labels.map { n -> n.name } + "is: backport")
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
}