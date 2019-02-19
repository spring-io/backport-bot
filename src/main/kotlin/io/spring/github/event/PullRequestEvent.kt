/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.github.event

import com.fasterxml.jackson.annotation.JsonProperty
import io.spring.github.api.IssueRef
import io.spring.github.api.PullRequest
import io.spring.github.api.RepositoryRef

/**
 * @author Rob Winch
 */
data class PullRequestEvent(val action : String, val repository : Repository, val label : Label?, @JsonProperty("pull_request") val pullRequest: PullRequest) {
    fun getIssueRef() : IssueRef {
        return IssueRef(getRepositoryRef(), this.pullRequest.number)
    }

    fun getRepositoryRef() : RepositoryRef {
        return RepositoryRef(this.repository.fullName)
    }
    data class Label(val name : String)
    data class Repository(@JsonProperty("full_name") val fullName : String)
}

