package io.spring.github.api

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Rob Winch
 */
data class TimelineEvent(val event : String, val source : Source?) {

    data class Source(val type : String, val issue : Issue?)

    data class Issue(val number : Int, val body : String?, val milestone : Milestone?)

    data class Milestone(val number : Int, val title : String)
}