package io.spring.github.api

import com.fasterxml.jackson.annotation.JsonProperty

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
        val r = """.*?Fixes:?\s+(gh\-|#)(?<id>\d+)(\r?\n)*""".toRegex(RegexOption.DOT_MATCHES_ALL)

        fun getFixIssueId() : Int? {
            return r.matchEntire(message)?.groups?.get("id")?.value?.toInt()
        }
    }

    data class Repository(@JsonProperty("full_name") val fullName : String)

    data class Pusher(val name : String)
}

