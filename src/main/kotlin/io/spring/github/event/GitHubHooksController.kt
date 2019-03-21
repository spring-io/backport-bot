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

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @author Rob Winch
 */
@RestController
@RequestMapping("/events/")
class GitHubHooksController(val events : GithubEventService) {

    @RequestMapping(headers = arrayOf("X-GitHub-Event=ping"))
    fun githubEventPing(): String {
        return "SUCCESS"
    }

    /**
     * @param body
     * @param githubEvent
     * @return
     * @throws Exception
     */
    @RequestMapping(path = arrayOf("/"), headers = arrayOf("X-GitHub-Event=push"))
    fun githubEventPush(@RequestBody pushEvent: PushEvent): ResponseEntity<String> {
        return this.events.backport(pushEvent)
                .filter { backported -> backported }
                .map { ResponseEntity("Created", HttpStatus.CREATED) }
                .defaultIfEmpty(ResponseEntity.ok("OK"))
                .block()!!
    }

    /**
     * @param body
     * @param githubEvent
     * @return
     * @throws Exception
     */
    @RequestMapping(path = arrayOf("/"), headers = arrayOf("X-GitHub-Event=issues"))
    fun githubEvent(@RequestBody issueEvent : IssueEvent): ResponseEntity<String> {
        return this.events.backport(issueEvent)
                .filter { backported -> backported }
                .map { ResponseEntity("Created", HttpStatus.CREATED) }
                .defaultIfEmpty(ResponseEntity.ok("OK"))
                .block()!!
    }

    /**
     * @param body
     * @param githubEvent
     * @return
     * @throws Exception
     */
    @RequestMapping(path = arrayOf("/"), headers = arrayOf("X-GitHub-Event=pull_request"))
    fun githubPullRequestEvent(@RequestBody pullRequestEvent: PullRequestEvent): ResponseEntity<String> {
        return this.events.backport(pullRequestEvent)
                .filter { backported -> backported }
                .map { ResponseEntity("Created", HttpStatus.CREATED) }
                .defaultIfEmpty(ResponseEntity.ok("OK"))
                .block()!!
    }
}