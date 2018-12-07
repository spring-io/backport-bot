package io.spring.github.api

/**
 * @author Rob Winch
 */
data class Issue(val number : Int, val title : String, val milestone : Milestone?, val labels : List<Label>) {
    data class Label(val name : String)
    data class Milestone(val number : Int)
}

