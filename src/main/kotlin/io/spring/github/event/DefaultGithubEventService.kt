/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
            .filterWhen { milestoneNumber -> isNotIssueForMilestone(issue, milestoneNumber) }
            .flatMap { milestoneNumber -> backport.createBackport(issue, milestoneNumber, listOf()).then(Mono.just(true)) }
            .defaultIfEmpty(false)
    }

    private fun isNotIssueForMilestone(issue: IssueRef, milestoneNumber: Int) =
            backport.isIssueForMilestone(issue, milestoneNumber).map { isIssue -> !isIssue }

    override fun backport(pushEvent : PushEvent) : Mono<Boolean> {
        val githubRef = pushEvent.getBranchRef()
        val fixedCommits = pushEvent.getFixCommits()
        return isBackport(pushEvent)
            .filter { shouldBackport -> shouldBackport }
            .flatMap { _ -> backport.findMilestoneNumber(githubRef) }
            .flatMap { milestoneNumber ->
                backport(fixedCommits, githubRef, milestoneNumber, pushEvent)
            }
            .defaultIfEmpty(false)
    }

    private fun backport(fixedCommits: List<PushEvent.Commit>, githubRef: BranchRef, milestoneNumber: Int, pushEvent: PushEvent): Mono<Boolean> {
        return Flux.fromIterable(fixedCommits)
                .filterWhen { fixedCommit ->
                    val issueRef = IssueRef(githubRef.repository, fixedCommit.getFixIssueId()!!)
                    // FIXME: We should refine this
                    //  We are looking up the backportedIssueForMilestone twice perhaps
                    //  need to move combined logic into private method here and only have
                    //  way to determine this specific issue (not any linked issues) have
                    //  this milestone
                    isNotIssueForMilestone(issueRef, milestoneNumber)
                }
                .flatMap { fixedCommit ->
                    backport(githubRef, fixedCommit, milestoneNumber, pushEvent)
                }
                .any { r -> r }
    }

    private fun backport(githubRef: BranchRef, fixedCommit: PushEvent.Commit, milestoneNumber: Int, pushEvent: PushEvent): Mono<Boolean> {
        val issueRef = IssueRef(githubRef.repository, fixedCommit.getFixIssueId()!!)
        return backport.findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber)
                .switchIfEmpty(backport.createBackport(issueRef, milestoneNumber, listOf(pushEvent.pusher.name)))
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
            .filter { branch -> branch == branchRef }
            .next()
            .map { branchRef -> true }
            .defaultIfEmpty(false)
    }
}