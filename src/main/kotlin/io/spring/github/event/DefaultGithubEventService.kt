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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * @author Rob Winch
 */
@Component
class DefaultGithubEventService(val backport : BackportService) : GithubEventService {
    override fun backport(issueEvent: IssueEvent): Mono<Boolean> {
        return findBranchFromLabeledIssueEvent(issueEvent)
            .flatMap { branch ->
                backport.removeLabel(issueEvent.getIssueRef(), issueEvent.label?.name!!)
                        .then(backport(branch, issueEvent.issue.number))
            }
            .defaultIfEmpty(false)
    }

    /**
     * Get the branch from the label of a labeled IssueEvent
     */
    private fun findBranchFromLabeledIssueEvent(issueEvent: IssueEvent): Mono<BranchRef> {
        if (issueEvent.action != "labeled") {
            return Mono.empty()
        }
        val labelName = issueEvent.label!!.name
        return backport.findBranchNameByLabelName(labelName)
                .map { branchName -> BranchRef(issueEvent.getRepositoryRef(), branchName) }
    }

    private fun backport(branch: BranchRef, issueNumber : Int) : Mono<Boolean> {
        val issue = IssueRef(branch.repository, issueNumber)
        return backport.findMilestoneNumber(branch)
            .filterWhen { milestoneNumber -> backport.isIssueForMilestone(issue, milestoneNumber).map { isIssue -> !isIssue } }
            .flatMap { milestoneNumber -> backport.createBackport(issue, milestoneNumber, listOf()).then(Mono.just(true)) }
            .defaultIfEmpty(false)
    }

    override fun backport(pullRequestEvent: PullRequestEvent): Mono<Boolean> {
        return findBranchFromLabeledPullRequestEvent(pullRequestEvent)
                .flatMap { branch ->
                    backport.removeLabel(pullRequestEvent.getIssueRef(), pullRequestEvent.label?.name!!)
                            .then(backport(branch, pullRequestEvent.pullRequest.number))
                }
                .defaultIfEmpty(false)
    }

    /**
     * Get the branch from the label of a labeled IssueEvent
     */
    private fun findBranchFromLabeledPullRequestEvent(pullRequestEvent: PullRequestEvent): Mono<BranchRef> {
        if (pullRequestEvent.action != "labeled") {
            return Mono.empty()
        }
        val labelName = pullRequestEvent.label!!.name
        return backport.findBranchNameByLabelName(labelName)
                .map { branchName -> BranchRef(pullRequestEvent.getRepositoryRef(), branchName) }
    }

    override fun backport(pushEvent : PushEvent) : Mono<Boolean> {
        val branchRef = pushEvent.getBranchRef()
        return isBackport(pushEvent)
            .filter { shouldBackport -> shouldBackport }
            .flatMap { _ -> backport.findMilestoneNumber(branchRef) }
            .flatMap { milestoneNumber ->
                backport(pushEvent, milestoneNumber)
            }
            .defaultIfEmpty(false)
    }

    private fun backport(pushEvent: PushEvent, milestoneNumber: Int): Mono<Boolean> {
        return Flux.fromIterable(pushEvent.getFixCommits())
                .flatMap { fixedCommit ->
                    backport(pushEvent, fixedCommit, milestoneNumber)
                }
                .then(Mono.just(true))
    }

    private fun backport(pushEvent: PushEvent, fixedCommit: PushEvent.Commit, milestoneNumber: Int): Mono<Boolean> {
        val branchRef = pushEvent.getBranchRef()
        val fixedIssueRef = IssueRef(branchRef.repository, fixedCommit.getFixIssueId()!!)
        return backport.findBackportedIssueForMilestoneNumber(fixedIssueRef, milestoneNumber)
                .switchIfEmpty(backport.createBackport(fixedIssueRef, milestoneNumber, listOf(pushEvent.pusher.name)))
                .flatMap { issueRef ->
                    backport.closeBackport(issueRef, fixedCommit.id).then(Mono.just(true))
                }
    }

    /**
     * Determines if this a PushEvent for a backport. Should be a branch that matches a
     * branch from [BackportService.findBackportBranches] and have
     * a commit that has Fixes: gh-<number> in it
     */
    private fun isBackport(pushEvent: PushEvent): Mono<Boolean> {
        val fixedCommits = pushEvent.getFixCommits()
        if (fixedCommits.isEmpty()) {
            return Mono.just(false)
        }
        val branchRef = pushEvent.getBranchRef()
        return this.backport.findBackportBranches(branchRef.repository)
            .any { branch -> branch == branchRef }
    }
}