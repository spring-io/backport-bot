package io.spring.github.api

/**
 * @author Rob Winch
 */
data class BranchRef(val repository : RepositoryRef, val ref : String)