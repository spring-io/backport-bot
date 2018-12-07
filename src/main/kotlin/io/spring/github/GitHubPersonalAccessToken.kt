package io.spring.github

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * @author Rob Winch
 */
@Configuration
@ConfigurationProperties(prefix = "github.issuemaster")
class GitHubPersonalAccessToken {
    lateinit var personalAccessToken: String
}