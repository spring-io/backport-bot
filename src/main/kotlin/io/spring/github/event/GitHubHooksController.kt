package io.spring.github.event

import io.spring.github.api.IssueEvent
import io.spring.github.api.PushEvent
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
}