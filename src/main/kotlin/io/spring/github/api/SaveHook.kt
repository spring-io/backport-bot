package io.spring.github.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Rob Winch
 */
data class SaveHook(@JsonIgnore val repository: RepositoryRef, val config : Config, val events: List<String>, val name: String = "web") {

    data class Config(val url : String, val secret: String, @JsonProperty("content_type") val contentType: String = "json")
}