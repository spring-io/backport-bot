package io.spring.github.event

import io.spring.github.api.IssueEvent
import io.spring.github.api.PushEvent
import reactor.core.publisher.Mono

/**
 * @author Rob Winch
 */
interface GithubEventService {
    // FIXME: Return a status with description of why not backported and return that in the response body of controller
    /**
     * Handle a PushEvent. Closes a backported issue when Fixes: gh-<original-ticket-id>
     * was pushed to the corresponding branch.
     */
    fun backport(pushEvent : PushEvent) : Mono<Boolean>

    /**
     * Looks for adding a label of "backport: 1.2.x" and then creates a Backport issue
     * if not already created. It then removes the label.
     */
    fun backport(issueEvent: IssueEvent) : Mono<Boolean>
}