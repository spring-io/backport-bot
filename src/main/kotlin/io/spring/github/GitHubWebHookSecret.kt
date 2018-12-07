package io.spring.github

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * @author Rob Winch
 */
@Configuration
@ConfigurationProperties(prefix = "github")
class GitHubWebHookSecret {
    lateinit var webhookSecret: String
}