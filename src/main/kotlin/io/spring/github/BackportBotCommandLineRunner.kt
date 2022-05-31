package io.spring.github

import com.fasterxml.jackson.databind.ObjectMapper
import io.spring.github.event.GitHubHooksController
import io.spring.github.event.IssueEvent
import io.spring.github.event.PullRequestEvent
import io.spring.github.event.PushEvent
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class BackportBotCommandLineRunner(val controller : GitHubHooksController, val objectMapper : ObjectMapper) : CommandLineRunner {
    var created : GitHubHooksController.Result = GitHubHooksController.Result.UNDEFINED
    override fun run(vararg args: String?) {
        if (args.size != 5) {
            usage(args);
        }
        val action = args[2]
        val event = args[4]
        when(action) {
            "pull_request" -> {
                val pullRequest = this.objectMapper.readValue(event, PullRequestEvent::class.java)
                this.created = controller.githubPullRequestEvent(pullRequest)
            }
            "issues" -> {
                val issues = this.objectMapper.readValue(event, IssueEvent::class.java)
                this.created = controller.githubEvent(issues)
            }
            "push" -> {
                val push = this.objectMapper.readValue(event, PushEvent::class.java)
                this.created = controller.githubEventPush(push)
            }
            else -> {
                usage(args)
            }
        }
    }

    private fun usage(args: Array<out String?>) {
        throw java.lang.IllegalArgumentException("Invalid usage Got ${args.size} arguments. Expected 5: java -jar jarname.jar --github.accessToken=<github.accessToken> --github.event_name <github.event_name> --github.event <github.event>")
    }
}