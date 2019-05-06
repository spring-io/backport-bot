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

package io.spring.github.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.net.URI
import java.util.*

/**
 * @author Rob Winch
 */
class WebClientGitHubApi(val webClient: WebClient = WebClient.create(), val baseGitHubUrl: String = "https://api.github.com") : GitHubApi {
    override fun getPermissionForDefaultLogin(repositoryRef: RepositoryRef, accessToken: String): Mono<Permission> {
        return defaultGitHubLogin()
                .flatMap { login -> getPermissionForLogin(repositoryRef, login, accessToken) }
    }

    private fun getPermissionForLogin(repositoryRef: RepositoryRef, login: String, accessToken: String): Mono<Permission> {
        return webClient.get()
                .uri("$baseGitHubUrl/repos/${repositoryRef.fullName}/collaborators/$login/permission")
                .headers { h -> h.setBearerAuth(accessToken)}
                .retrieve()
                .bodyToMono<Map<String,Any>>()
                .map { body -> body.get("permission")?.toString()!! }
                .map { p -> Permission(login, p) }
    }

    private fun defaultGitHubLogin(): Mono<String> {
        return webClient.get()
                .uri("$baseGitHubUrl/user")
                .retrieve()
                .bodyToMono<Map<String,Any>>()
                .map { body -> body.get("login")?.toString() }
    }

    override fun isMemberOfTeam(username: String, teamId: Int, accessToken: String): Mono<Boolean> {
        return webClient.get()
                .uri("$baseGitHubUrl/teams/$teamId/memberships/$username")
                .headers { h -> h.setBearerAuth(accessToken) }
                .exchange()
                .flatMap { response ->
                    val status = response.statusCode()
                    if (status == HttpStatus.OK) {
                        Mono.just(true)
                    } else if (status == HttpStatus.NOT_FOUND) {
                        Mono.just(false)
                    } else {
                        error(response, "Failed to determine if $username is a part of $teamId")
                    }
                }
    }

    override fun saveHook(saveHook : SaveHook) : Mono<Void> {
        return getHooks(saveHook.repository)
            .filter { h -> h.config.url == saveHook.config.url }
            .next()
            .flatMap { h -> editHook(h.id, saveHook) }
            .switchIfEmpty(createNewHook(saveHook))
            .then()
    }

    // FIXME: If there are paged hooks this doesn't work
    private fun getHooks(repository: RepositoryRef) : Flux<Hook> {
        return webClient
            .get()
            .uri("$baseGitHubUrl/repos/${repository.fullName}/hooks")
            .attributes(clientRegistrationId("github"))
            .retrieve()
            .bodyToFlux<Hook>()
    }

    private fun editHook(id: Int, saveHook: SaveHook): Mono<String> {
        return webClient
                .patch()
                .uri("$baseGitHubUrl/repos/${saveHook.repository.fullName}/hooks/$id")
                .attributes(clientRegistrationId("github"))
                .syncBody(saveHook)
                .retrieve()
                .bodyToMono<String>()
    }

    private fun createNewHook(saveHook: SaveHook): Mono<String> {
        return webClient
                .post()
                .uri("$baseGitHubUrl/repos/${saveHook.repository.fullName}/hooks")
                .attributes(clientRegistrationId("github"))
                .syncBody(saveHook)
                .retrieve()
                .bodyToMono<String>()
    }

    data class Hook(val id: Int, val config: Config) {
        data class Config(val url : String?)
    }

    override fun closeIssue(issueRef: IssueRef): Mono<Void> {
        return updateIssue(issueRef, mapOf(Pair("state", "closed")))
    }

    override fun updateLabels(issueRef: IssueRef, labels: List<String>): Mono<Void> {
        return updateIssue(issueRef, mapOf(Pair("labels", labels)))
    }

    private fun updateIssue(issueRef: IssueRef, body: Any) : Mono<Void> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${issueRef.repository.fullName}/issues/{number}")
                .buildAndExpand(issueRef.number)
                .toUri()
        return webClient.patch()
                .uri(uri)
                .syncBody(body)
                .exchange()
                .flatMap { clientResponse ->
                    val status = clientResponse.statusCode()
                    if (status.is2xxSuccessful)
                        clientResponse.bodyToMono<String>()
                    else
                        error(clientResponse, "Failed to update issue $issueRef")
                }
                .then()
    }

    private fun <T> error(r : ClientResponse, message : String) : Mono<T> {
        return r.bodyToMono<String>().defaultIfEmpty("<empty body>").flatMap { b -> Mono.error<T>(RuntimeException("$message Got status ${r.statusCode()} and body $b")) }
    }

    override fun comment(issueRef: IssueRef, comment: String): Mono<Void> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${issueRef.repository.fullName}/issues/{number}/comments")
                .buildAndExpand(issueRef.number)
                .toUri()
        return webClient.post()
                .uri(uri)
                .syncBody(mapOf(Pair("body", comment)))
                .exchange()
                .flatMap { clientResponse ->
                    val status = clientResponse.statusCode()
                    if (status.is2xxSuccessful)
                        clientResponse.bodyToMono<String>()
                    else
                        error(clientResponse, "Failed to create comment for $issueRef")
                }
                .then()
    }

    override fun findMilestoneNumberByTitle(repositoryRef: RepositoryRef, title: String): Mono<Int> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${repositoryRef.fullName}/milestones")
                .build()
                .toUri()
        return exchange(uri)
                .flatMapMany(milestoneNumber())
                .filter { r -> r.title.equals(title) }
                .next()
                .map { r -> r.number }
    }

    private fun exchange(uri: URI) : Mono<ClientResponse> {
        return webClient.get()
                .uri(uri)
                .exchange()
    }

    private fun milestoneNumber(): (ClientResponse) -> Flux<GitHubMilestone> {
        return { clientResponse ->
            val status = clientResponse.statusCode()
            if (status.is2xxSuccessful) {
                val body = clientResponse.bodyToFlux<GitHubMilestone>()
                val link = next(clientResponse.headers().asHttpHeaders())
                if (link == null) {
                    body
                } else {
                    body.concatWith(exchange(URI.create(link)).flatMapMany(milestoneNumber()))
                }
            } else {
                Flux.concat(error(clientResponse, "Cannot get milestone"))
            }
        }
    }

    override fun findFile(branchRef: BranchRef, file : String) : Mono<InputStream> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${branchRef.repository.fullName}/contents/" + file)
                .queryParam("ref", branchRef.ref)
                .build()
                .toUri()
        return webClient.get()
                .uri(uri)
                .exchange()
                .flatMap { clientResponse ->
                    val status = clientResponse.statusCode()
                    if (status.is2xxSuccessful)
                        clientResponse.bodyToMono<GithubContents>()
                    else
                        error(clientResponse, "Could not get file $file for $branchRef")
                }
                .map { c -> c.content.replace("\n", "") }
                .map { c -> Base64.getDecoder().decode(c)!! }
                .map { f -> ByteArrayInputStream(f) }
    }

    override fun createIssue(issue: CreateIssue): Mono<IssueRef> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${issue.ref.fullName}/issues")
                .build()
                .toUri()
        return webClient.post()
                .uri(uri)
                .syncBody(issue)
                .exchange()
                .flatMap { clientResponse ->
                    val status = clientResponse.statusCode()
                    if (status.is2xxSuccessful)
                        clientResponse.bodyToMono<CreateIssueResponse>().map {  r -> r.number }
                    else
                        error(clientResponse, "Cannot not create issue for $issue Got status $status")
                }
                .map { issueNumber -> IssueRef(issue.ref, issueNumber) }
    }

    override fun findIssue(issueRef : IssueRef) : Mono<Issue> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${issueRef.repository.fullName}/issues/{number}")
                .buildAndExpand(issueRef.number)
                .toUri()
        return webClient.get()
                .uri(uri)
                .exchange()
                .flatMap { clientResponse ->
                    val status = clientResponse.statusCode()
                    if (status.is2xxSuccessful)
                        clientResponse.bodyToMono<Issue>()
                    else
                        error(clientResponse, "Could not find issue $issueRef")
                }
    }

    /**
     * https://developer.github.com/v3/issues/timeline/
     */
    override fun findIssueTimeline(issueRef : IssueRef) : Flux<TimelineEvent> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${issueRef.repository.fullName}/issues/{number}/timeline")
                .buildAndExpand(issueRef.number)
                .toUri()
        return findIssueTimeline(uri)
                .flatMapMany(timelineEvent(issueRef.number))
    }

    private fun findIssueTimeline(uri : URI): Mono<ClientResponse> {
        return webClient.get()
                .uri(uri)
                .header("Accept", "application/vnd.github.mockingbird-preview")
                .exchange()
    }

    private fun timelineEvent(issueNumber: Int): (ClientResponse) -> Flux<TimelineEvent> {
        return { e ->
            val status = e.statusCode()
            if (status.is2xxSuccessful) {
                val body = e.bodyToFlux<TimelineEvent>()
                val link = next(e.headers().asHttpHeaders())
                if (link == null) {
                    body
                } else {
                    body.concatWith(findIssueTimeline(URI.create(link)).flatMapMany (timelineEvent(issueNumber)))
                }
            } else {
                Flux.concat(error(e, "Cannot not create issue for $issueNumber"))
            }
        }
    }

    override fun findLabels(repositoryRef: RepositoryRef): Flux<Label> {
        val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
                .path("/repos/${repositoryRef.fullName}/labels")
                .build()
                .toUri()
        return findLabels(uri)
                .flatMapMany(label())
    }

    private fun findLabels(uri: URI): Mono<ClientResponse> {
        return webClient.get()
                .uri(uri)
                .exchange()
    }

    private fun label(): (ClientResponse) -> Flux<Label> {
        return { e ->
            val status = e.statusCode()
            if (status.is2xxSuccessful) {
                val body = e.bodyToFlux<Label>()
                val link = next(e.headers().asHttpHeaders())
                if (link == null) {
                    body
                } else {
                    body.concatWith(findIssueTimeline(URI.create(link)).flatMapMany (label()))
                }
            } else {
                Flux.concat(error(e, "Cannot not get the labels"))
            }
        }
    }

    private fun next(httpHeaders : HttpHeaders) : String? {
        val linkHeaderValue = httpHeaders.getFirst("Link")
        if (linkHeaderValue == null) {
            return null
        }
        val nextRegex = """.*?<(.*?)>; rel="next".*""".toRegex()
        if (!linkHeaderValue.matches(nextRegex)) {
            return null
        }
        return linkHeaderValue.replace(nextRegex, "$1")
    }

    data class GitHubMilestone(val number : Int, val title : String)

    data class CreateIssueResponse(val number : Int)

    data class GithubContents(val content : String)
}
