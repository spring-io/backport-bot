/*
 * Copyright 2002-present the original author or authors.
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

import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * @author Rob Winch
 */
@Component
class GitHubHooksController(val events : GithubEventService) {

    /**
     * @param body
     * @param githubEvent
     * @return
     * @throws Exception
     */
    fun githubEventPush(@RequestBody pushEvent: PushEvent): Result {
        return this.events.backport(pushEvent)
                .filter { backported -> backported }
                .map { Result.CREATED }
                .defaultIfEmpty(Result.OK)
                .block()!!
    }

    /**
     * @param body
     * @param githubEvent
     * @return
     * @throws Exception
     */
    @RequestMapping(path = arrayOf("/"), headers = arrayOf("X-GitHub-Event=issues"))
    fun githubEvent(@RequestBody issueEvent : IssueEvent): Result {
        return this.events.backport(issueEvent)
                .filter { backported -> backported }
                .map { Result.CREATED }
                .defaultIfEmpty(Result.OK)
                .block()!!
    }

    /**
     * @param body
     * @param githubEvent
     * @return
     * @throws Exception
     */
    @RequestMapping(path = arrayOf("/"), headers = arrayOf("X-GitHub-Event=pull_request"))
    fun githubPullRequestEvent(@RequestBody pullRequestEvent: PullRequestEvent): Result {
        return this.events.backport(pullRequestEvent)
                .filter { backported -> backported }
                .map { Result.CREATED }
                .defaultIfEmpty(Result.OK)
                .block()!!
    }

    enum class Result {
        OK, CREATED, UNDEFINED
    }
}