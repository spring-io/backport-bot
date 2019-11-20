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

import com.fasterxml.jackson.annotation.JsonProperty
import io.spring.github.api.BranchRef
import io.spring.github.api.RepositoryRef

/**
 * @author Rob Winch
 */
class PushEvent(val ref : String, val repository : Repository, val pusher : Pusher, val commits : List<Commit> = listOf()) {
    fun getFixCommits() : List<Commit> {
        return commits
                .filter { c -> c.getFixIssueId() != null }
    }

    fun getBranchRef() : BranchRef {
        return BranchRef(RepositoryRef(repository.fullName), ref)
    }

    data class Commit(val id : String, val message : String) {
        val r = """.*?(Fixes|Closes):?\s+(gh\-|#)(?<id>\d+)(\r?\n)*""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        fun getFixIssueId() : Int? {
            return r.find(message)?.groups?.get("id")?.value?.toInt()
        }
    }

    data class Repository(@JsonProperty("full_name") val fullName : String)

    data class Pusher(val name : String)
}

