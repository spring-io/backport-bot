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
                        .then(backport(branch, issueEvent.issue.number, issueEvent.sender.login))
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

    private fun backport(branch: BranchRef, issueNumber : Int, login : String) : Mono<Boolean> {
        val issue = IssueRef(branch.repository, issueNumber)
        return backport.findMilestoneNumber(branch)
            .filterWhen { milestoneNumber -> backport.isIssueForMilestone(issue, milestoneNumber).map { isIssue -> !isIssue } }
            .flatMap { milestoneNumber -> backport.createBackport(issue, milestoneNumber, login).then(Mono.just(true)) }
            .defaultIfEmpty(false)
    }

    override fun backport(pushEvent : PushEvent) : Mono<Boolean> {
        val githubRef = pushEvent.getBranchRef()
        val fixedCommits = pushEvent.getFixCommits()
        return isBackport(pushEvent)
            .filter { shouldBackport -> shouldBackport }
            .flatMap { _ -> backport.findMilestoneNumber(githubRef) }
            .flatMap { milestoneNumber ->
                Flux.fromIterable(fixedCommits)
                    .flatMap { fixedCommit ->
                        val issueRef = IssueRef(githubRef.repository, fixedCommit.getFixIssueId()!!)
                backport.findBackportedIssueForMilestoneNumber(issueRef, milestoneNumber)
                            .switchIfEmpty(backport.createBackport(issueRef, milestoneNumber, pushEvent.pusher.name))
                            .flatMap { issueNumber ->
                                backport.closeBackport(IssueRef(githubRef.repository, issueNumber), fixedCommit.id).then(Mono.just(true))
                            }
                    }
                    .then(Mono.just(true))
            }
            .defaultIfEmpty(false)
    }

    /**
     * Determines if this a PushEvent for a backport. Should be a branch ending in .x, have
     * a commit that has Fixes: gh-<number> in it
     */
    private fun isBackport(pushEvent: PushEvent): Mono<Boolean> {
        val githubRef = pushEvent.getBranchRef()
        if (!githubRef.ref.endsWith(".x")) {
            return Mono.just(false)
        }
        val fixedCommits = pushEvent.getFixCommits()
        if (fixedCommits.isEmpty()) {
            return Mono.just(false)
        }
        return Mono.just(true)
    }
}