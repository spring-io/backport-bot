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

package io.spring.github.api

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.InputStream

/**
 * @author Rob Winch
 */
interface GitHubApi {
    fun isMemberOfTeam(username: String, teamId: Int, accessToken: String): Mono<Boolean>

    fun getPermissionForDefaultLogin(repositoryRef: RepositoryRef, accessToken: String): Mono<Permission>

    /**
     * Get a milestone number by its title
     */
    fun findMilestoneNumberByTitle(repositoryRef: RepositoryRef, title : String) : Mono<Int>

    /**
     * Create a GitHub issue
     * @return the issue number
     */
    fun createIssue(issue: CreateIssue): Mono<IssueRef>

    /**
     * Closes an issue
     */
    fun closeIssue(issueRef: IssueRef) : Mono<Void>

    /**
     * Adds a comment to the issue
     */
    fun comment(issueRef: IssueRef, comment : String) : Mono<Void>

    /**
     * Find an issue. If it is not found an error is returned
     */
    fun findIssue(issueRef : IssueRef) : Mono<Issue>

    /**
     * Finds a file on a specific branch
     */
    fun findFile(branchRef: BranchRef, file : String): Mono<InputStream>

    /**
     * Get's the TimelineEvent's for an issue
     */
    fun findIssueTimeline(issueRef : IssueRef) : Flux<TimelineEvent>

    /**
     * Updates the labels on an issue
     */
    fun updateLabels(issueRef : IssueRef, labels: List<String>): Mono<Void>

    fun saveHook(saveHook: SaveHook): Mono<Void>

    /**
     * Finds all the labels for a Repository
     */
    fun findLabels(repositoryRef: RepositoryRef): Flux<Label>
}