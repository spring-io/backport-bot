package io.spring.github.api

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * @author Rob Winch
 */
data class CreateIssue(@JsonIgnore val ref: RepositoryRef,
                       val title: String,
                       val body: String = "",
                       val milestone: Int? = null,
                       val labels: List<String> = emptyList(),
                       val assignees: List<String> = emptyList())