package io.spring.github.event

import io.spring.github.api.*
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * @author Rob Winch
 */
@Component
class DefaultBackportService(val github: GitHubApi) : BackportService {

    val backportLabelMatcher = "backport: (?<branch>.*?)".toRegex()

    override fun findBranchNameByLabelName(labelName: String): Mono<String> {
        return Mono.justOrEmpty(backportLabelMatcher.matchEntire(labelName)?.groups?.get("branch")?.value)
    }

    @Override
    override fun removeLabel(issueRef: IssueRef, labelNameToRemove: String): Mono<Void> {
        return github.findIssue(issueRef)
                .map { issue -> issue.labels.map { i -> i.name }.filter { labelName -> labelName != labelNameToRemove } }
                .flatMap { filteredLabels -> github.updateLabels(issueRef, filteredLabels) }
                .then()

    }

    @Override
    override fun findMilestoneNumber(branchRef: BranchRef): Mono<Int> {
        return findMilestoneTitle(branchRef)
                .flatMap { title -> findMilestoneNumberByTitle(branchRef.repository, title) }
                .switchIfEmpty(Mono.error { IllegalStateException("Cannot find a milestone number for $branchRef") })
    }

    private fun findMilestoneTitle(ref: BranchRef): Mono<String> {
        return this.github.findFile(ref, "gradle.properties")
                .map { input ->
                    val p = Properties()
                    p.load(input)
                    val version = p.getProperty("version")
                    version.replace(".BUILD-SNAPSHOT", "")
                }
                .switchIfEmpty(Mono.error { IllegalStateException("Cannot find file gradle.properties for $ref") })
    }

    private fun findMilestoneNumberByTitle(repositoryRef: RepositoryRef, title: String): Mono<Int> {
        return github.findMilestoneNumberByTitle(repositoryRef, title)
                .switchIfEmpty(Mono.error { IllegalStateException("Cannot find milestone number for title $title and $repositoryRef") })
    }

    @Override
    override fun isIssueForMilestone(issueRef: IssueRef, milestoneNumber: Int): Mono<Boolean> {
        // does the issue itself refer to the provided milestone (i.e. it might not be a backport)
        val isIssueForMilestone = github.findIssue(issueRef)
                .map { issue -> issue.milestone?.number == milestoneNumber }
                .defaultIfEmpty(false)
        // is there a backport for the milestone
        val isBackportForMilestone = findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber)
                .map { _ -> true }
                .defaultIfEmpty(false)

        return Flux.concat(isIssueForMilestone, isBackportForMilestone)
                .any { i -> i }
    }

    @Override
    override fun findBackportedIssueForMilestoneNumber(issueRef: IssueRef, milestoneNumber: Int): Mono<Int> {
        return github.findIssueTimeline(issueRef)
                .filter { e -> e.event == "cross-referenced" }
                .filter { e -> e.source?.issue?.milestone?.number == milestoneNumber }
                .filter { e -> e.source?.issue?.body == "Backport of gh-${issueRef.number}" }
                .map { e -> e.source?.issue?.number!! }
                .next()
    }

    @Override
    override fun closeBackport(issueRef: IssueRef, fixedCommitId: String): Mono<Void> {
        return github.comment(issueRef, "Fixed via ${fixedCommitId}")
                .then(this.github.closeIssue(issueRef))
    }

    @Override
    override fun createBackport(fixedIssue: IssueRef, milestone: Int, assignee: String): Mono<Int> {
        return github.findIssue(fixedIssue)
                .switchIfEmpty(Mono.error { IllegalStateException("Cannot find issue $fixedIssue") })
                .flatMap { issue -> github.updateLabels(fixedIssue, issue.labels.map {l -> l.name } + arrayListOf("is: backported")).thenReturn(issue) }
                .map { issue -> CreateIssue(fixedIssue.repository, issue.title, "Backport of gh-${fixedIssue.number}", milestone, issue.labels.map { l -> l.name }.filter { l -> !backportLabelMatcher.matches(l) } + arrayListOf("is: backport"), arrayListOf(assignee)) }
                .flatMap { createIssue -> github.createIssue(createIssue) }
    }
}