package io.spring.github.api

import com.fasterxml.jackson.annotation.JsonProperty
import reactor.core.publisher.Mono

/**
 * @author Rob Winch
 */
data class IssueEvent(val action : String, val repository : Repository, val sender : Sender, val label : Label?, val issue : Issue) {
    fun getIssueRef() : IssueRef {
        return IssueRef(getRepositoryRef(), this.issue.number)
    }

    fun getRepositoryRef() : RepositoryRef {
        return RepositoryRef(this.repository.fullName)
    }

    data class Sender(val login : String)
    data class Label(val name : String)
    data class Repository(@JsonProperty("full_name") val fullName : String)
}