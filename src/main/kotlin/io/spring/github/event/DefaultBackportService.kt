/*
 * Copyright 2002-2019 the original author or authors.
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

package io.spring.github.event

import io.spring.github.api.*
import org.springframework.stereotype.Component
import org.w3c.dom.Document
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * @author Rob Winch
 */
@Component
class DefaultBackportService(val github: GitHubApi) : BackportService {

    val backportLabelMatcher = "for: backport\\-to\\-(?<branch>.*?)".toRegex()

    val LABEL_STATUS_BACKPORTED = "status: backported"

    val LABEL_TYPE_BACKPORT = "type: backport"

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
                .map { file ->
                    val p = Properties()
                    p.load(file)
                    p.getProperty("version")
                }
            	.switchIfEmpty(Mono.defer {
                    this.github.findFile(ref, "pom.xml")
                    .map { file ->
                        val builderFactory = DocumentBuilderFactory.newInstance()
                        val builder = builderFactory.newDocumentBuilder()
                        val xmlDocument: Document = builder.parse(file)

                        val xPath: XPath = XPathFactory.newInstance().newXPath()
                        var version = xPath.compile("/project/properties/revision").evaluate(xmlDocument)
                        if (version == null) {
                            version = xPath.compile("/project/version").evaluate(xmlDocument)
                        }
                        version
                    }
                })
                .map { it.replace(".BUILD-SNAPSHOT", "").replace("-SNAPSHOT", "") }
                .switchIfEmpty(Mono.error { IllegalStateException("Cannot find 'gradle.properties' or 'pom.xml' for $ref") })
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
    override fun findBackportedIssueForMilestoneNumber(issueRef: IssueRef, milestoneNumber: Int): Mono<IssueRef> {
        return github.findIssue(issueRef)
                .filter { issue -> issue.milestone?.number == milestoneNumber }
                .map { _ -> issueRef }
                .switchIfEmpty(findBackportedIssueForMilestoneNumberFromTimeline(issueRef, milestoneNumber))
    }

    private fun findBackportedIssueForMilestoneNumberFromTimeline(issueRef: IssueRef, milestoneNumber: Int): Mono<IssueRef> {
        return github.findIssueTimeline(issueRef)
                .filter { e -> e.event == "cross-referenced" }
                .filter { e -> e.source?.issue?.milestone?.number == milestoneNumber }
                .filter { e -> e.source?.issue?.body == "Backport of gh-${issueRef.number}" }
                .map { e -> e.source?.issue?.number!! }
                .next()
                .map { issueNumber -> IssueRef(issueRef.repository, issueNumber) }
    }

    @Override
    override fun closeBackport(issueRef: IssueRef, fixedCommitId: String): Mono<Void> {
        return github.comment(issueRef, "Fixed via ${fixedCommitId}")
                .then(this.github.closeIssue(issueRef))
    }

    @Override
    override fun createBackport(fixedIssue: IssueRef, milestone: Int, assignees: List<String>): Mono<IssueRef> {
        return github.findIssue(fixedIssue)
                .switchIfEmpty(Mono.error { IllegalStateException("Cannot find issue $fixedIssue") })
                .flatMap { issue -> github.updateLabels(fixedIssue, issue.labels.map {l -> l.name } + arrayListOf(LABEL_STATUS_BACKPORTED)).thenReturn(issue) }
                .map { issue -> CreateIssue(fixedIssue.repository, issue.title, "Backport of gh-${fixedIssue.number}", milestone, issue.labels.map { l -> l.name }.filter { n -> n != LABEL_STATUS_BACKPORTED}.filter { l -> !backportLabelMatcher.matches(l) } + arrayListOf(LABEL_TYPE_BACKPORT), assignees) }
                .flatMap { createIssue -> github.createIssue(createIssue) }
    }

    override fun findBackportBranches(repositoryRef: RepositoryRef): Flux<BranchRef> {
        return this.github.findLabels(repositoryRef)
                .map { label -> label.name }
                .concatMap { labelName -> findBranchNameByLabelName(labelName) }
                .map { branchName -> BranchRef(repositoryRef, "refs/heads/$branchName") }
    }
}