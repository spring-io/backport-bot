/*
 * Copyright 2002-present the original author or authors.
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

package io.spring.github.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.util.*

/**
 * @author Rob Winch
 * @author Artem Bilan
 */
class WebClientGitHubApi(
	private val webClient: WebClient = WebClient.create(),
	val baseGitHubUrl: String = "https://api.github.com"
) : GitHubApi {

	/**
	 * @see https://docs.github.com/en/rest/users/users#get-the-authenticated-user
	 * @see https://docs.github.com/en/rest/collaborators/collaborators#get-repository-permissions-for-a-user
	 */
	override fun getPermissionForDefaultLogin(repositoryRef: RepositoryRef, accessToken: String): Mono<Permission> {
		return defaultGitHubLogin()
			.flatMap { login -> getPermissionForLogin(repositoryRef, login, accessToken) }
	}

	private fun getPermissionForLogin(
		repositoryRef: RepositoryRef,
		login: String,
		accessToken: String
	): Mono<Permission> {
		return webClient.get()
			.uri("$baseGitHubUrl/repos/${repositoryRef.fullName}/collaborators/$login/permission")
			.headers { h -> h.setBearerAuth(accessToken) }
			.retrieve()
			.bodyToMono<Map<String, Any>>()
			.map { body -> body["permission"]?.toString()!! }
			.map { p -> Permission(login, p) }
	}

	private fun defaultGitHubLogin(): Mono<String> {
		return webClient.get()
			.uri("$baseGitHubUrl/user")
			.retrieve()
			.bodyToMono<Map<String, Any>>()
			.mapNotNull { body -> body["login"]?.toString() }
	}

	/**
	 * @see https://docs.github.com/en/rest/teams/members/#get-team-membership-for-a-user
	 */
	override fun isMemberOfTeam(username: String, teamId: Int, accessToken: String): Mono<Boolean> {
		return webClient.get()
			.uri("$baseGitHubUrl/teams/$teamId/memberships/$username")
			.headers { h -> h.setBearerAuth(accessToken) }
			.exchangeToMono { response ->
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

	/**
	 * @see https://docs.github.com/en/rest/issues/issues#update-an-issue
	 */
	override fun closeIssue(issueRef: IssueRef): Mono<Void> {
		return updateIssue(issueRef, mapOf(Pair("state", "closed")))
	}

	/**
	 * @see https://docs.github.com/en/rest/issues/issues#update-an-issue
	 */
	override fun updateLabels(issueRef: IssueRef, labels: List<String>): Mono<Void> {
		return updateIssue(issueRef, mapOf(Pair("labels", labels)))
	}

	private fun updateIssue(issueRef: IssueRef, body: Any): Mono<Void> {
		val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
			.path("/repos/${issueRef.repository.fullName}/issues/{number}")
			.buildAndExpand(issueRef.number)
			.toUri()
		return webClient.patch()
			.uri(uri)
			.bodyValue(body)
			.exchangeToMono { clientResponse ->
				val status = clientResponse.statusCode()
				if (status.is2xxSuccessful)
					clientResponse.bodyToMono<String>()
				else
					error(clientResponse, "Failed to update issue $issueRef")
			}
			.then()
	}

	private fun <T> error(r: ClientResponse, message: String): Mono<T> {
		return r.bodyToMono<String>().defaultIfEmpty("<empty body>")
			.flatMap { b -> Mono.error<T>(RuntimeException("$message Got status ${r.statusCode()} and body $b")) }
	}

	/**
	 * @see https://docs.github.com/en/rest/issues/comments#create-an-issue-comment
	 */
	override fun comment(issueRef: IssueRef, comment: String): Mono<Void> {
		val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
			.path("/repos/${issueRef.repository.fullName}/issues/{number}/comments")
			.buildAndExpand(issueRef.number)
			.toUri()
		return webClient.post()
			.uri(uri)
			.bodyValue(mapOf(Pair("body", comment)))
			.exchangeToMono { clientResponse ->
				val status = clientResponse.statusCode()
				if (status.is2xxSuccessful)
					clientResponse.bodyToMono<String>()
				else
					error(clientResponse, "Failed to create comment for $issueRef")
			}
			.then()
	}

	/**
	 * @see https://docs.github.com/en/rest/issues/milestones#list-milestones-for-a-repository
	 */
	override fun findMilestoneNumberByTitle(repositoryRef: RepositoryRef, title: String): Mono<Int> {
		val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
			.path("/repos/${repositoryRef.fullName}/milestones")
			.build()
			.toUri()
		return exchange(uri)
			.flatMapMany(milestoneNumber())
			.filter { r -> r.title == title }
			.next()
			.map { r -> r.number }
	}

	private fun exchange(uri: URI): Mono<ClientResponse> {
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

	/**
	 * @see https://docs.github.com/en/rest/repos/contents#get-repository-content
	 */
	override fun findFile(branchRef: BranchRef, file: String): Mono<InputStream> {
		val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
			.path("/repos/${branchRef.repository.fullName}/contents/" + file)
			.queryParam("ref", branchRef.ref)
			.build()
			.toUri()
		return webClient.get()
			.uri(uri)
			.exchangeToMono { clientResponse ->
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

	/**
	 * @see https://docs.github.com/en/rest/issues/issues#create-an-issue
	 */
	override fun createIssue(issue: CreateIssue): Mono<IssueRef> {
		val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
			.path("/repos/${issue.ref.fullName}/issues")
			.build()
			.toUri()
		return webClient.post()
			.uri(uri)
			.bodyValue(issue)
			.exchangeToMono { clientResponse ->
				val status = clientResponse.statusCode()
				if (status.is2xxSuccessful)
					clientResponse.bodyToMono<CreateIssueResponse>().map { r -> r.number }
				else
					error(clientResponse, "Cannot create issue for $issue Got status $status")
			}
			.map { issueNumber -> IssueRef(issue.ref, issueNumber) }
	}

	/**
	 * @see https://docs.github.com/en/rest/issues/issues#get-an-issue
	 */
	override fun findIssue(issueRef: IssueRef): Mono<Issue> {
		val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
			.path("/repos/${issueRef.repository.fullName}/issues/{number}")
			.buildAndExpand(issueRef.number)
			.toUri()
		return webClient.get()
			.uri(uri)
			.exchangeToMono { clientResponse ->
				val status = clientResponse.statusCode()
				if (status.is2xxSuccessful)
					clientResponse.bodyToMono<Issue>()
				else
					error(clientResponse, "Could not find issue $issueRef")
			}
	}

	/**
	 * @see https://docs.github.com/en/rest/issues/timeline#list-timeline-events-for-an-issue
	 */
	override fun findIssueTimeline(issueRef: IssueRef): Flux<TimelineEvent> {
		val uri = UriComponentsBuilder.fromUriString(baseGitHubUrl)
			.path("/repos/${issueRef.repository.fullName}/issues/{number}/timeline")
			.buildAndExpand(issueRef.number)
			.toUri()
		return findIssueTimeline(uri)
			.flatMapMany(timelineEvent(issueRef.number))
	}

	private fun findIssueTimeline(uri: URI): Mono<ClientResponse> {
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
					body.concatWith(findIssueTimeline(URI.create(link)).flatMapMany(timelineEvent(issueNumber)))
				}
			} else {
				Flux.concat(error(e, "Cannot not create issue for $issueNumber"))
			}
		}
	}

	/**
	 * @see https://docs.github.com/en/rest/issues/labels#list-labels-for-a-repository
	 */
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
					body.concatWith(findIssueTimeline(URI.create(link)).flatMapMany(label()))
				}
			} else {
				Flux.concat(error(e, "Cannot not get the labels"))
			}
		}
	}

	private fun next(httpHeaders: HttpHeaders): String? {
		var linkHeaderValue = httpHeaders.getFirst("Link") ?: return null

		val relPrevToken = """rel="prev", """
		val index = linkHeaderValue.indexOf(relPrevToken)
		if (index != -1) {
			linkHeaderValue = linkHeaderValue.substring(index + relPrevToken.length)
		}

		val nextRegex = """.*?<(.*?)>; rel="next".*""".toRegex()
		if (!linkHeaderValue.matches(nextRegex)) {
			return null
		}
		return linkHeaderValue.replace(nextRegex, "$1")
	}

	data class GitHubMilestone(val number: Int, val title: String)

	data class CreateIssueResponse(val number: Int)

	data class GithubContents(val content: String)
}
