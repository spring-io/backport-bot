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

import io.spring.github.api.BranchRef
import io.spring.github.api.IssueEvent
import io.spring.github.api.IssueRef
import io.spring.github.api.PushEvent
import reactor.core.publisher.Mono
import java.util.function.Predicate

/**
 * @author Rob Winch
 */
interface BackportService {
    /**
     * Gets the milestone number. Typically the title is found from gradle.properites and
     * then that is used to find a milestone number by its title.
     */
    fun findMilestoneNumber(branchRef: BranchRef) : Mono<Int>

    /**
     * Does an issue already exist for the given milestone?
     */
    fun isIssueForMilestone(issueRef: IssueRef, milestoneNumber : Int) : Mono<Boolean>

    /**
     * Creates a backport issue
     */
    fun createBackport(fixedIssue: IssueRef, milestone: Int, assignee: String): Mono<Int>

    /**
     If found, removes a label from an issue
     */
    fun removeLabel(issueRef: IssueRef, labelNameToRemove: String): Mono<Void>

    /**
     * Closes a backport
     */
    fun closeBackport(issueRef: IssueRef, fixedCommitId: String) : Mono<Void>

    /**
     * Finds a backported issue number for a specific milestone number. The issue is the
     * original issue.
     */
    fun findBackportedIssueForMilestoneNumber(issueRef: IssueRef, milestoneNumber: Int): Mono<Int>

    /**
     * Given a labelName will extract out a branch name or an empty Mono if no branch name
     * can be extracted.
     */
    fun findBranchNameByLabelName(labelName: String) : Mono<String>
}