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

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import reactor.test.StepVerifier

/**
 * @author Rob Winch
 * @author Artem Bilan
 */
class WebClientGitHubApiTest {
    private val server = MockWebServer()

    private val baseUrl = server.url("").toUrl().toExternalForm()

    private val github = WebClientGitHubApi(baseGitHubUrl = baseUrl)

    private val repository = RepositoryRef("rwinch/repository")

    private val branch = BranchRef(repository, "refs/heads/main")

    private val issue = IssueRef(branch.repository, 1347)

    val teamId = 482984

    val accessToken = "access-token-1"

    @Test
    fun isMemberOfTeamWhenUserActiveThenTrue() {
        val body = """{
            "url": "https://api.github.com/teams/1/memberships/octocat",
            "role": "member",
            "state": "active"
        }"""
        enqueue(body)

        assertThat(this.github.isMemberOfTeam("rwinch", teamId, accessToken).block()).isTrue()
    }

    @Test
    fun isMemberOfTeamWhenUserMaintainerThenTrue() {
        val body = """{
            "url": "https://api.github.com/teams/1/memberships/octocat",
            "role": "maintainer",
            "state": "active"
        }"""
        enqueue(body)

        assertThat(this.github.isMemberOfTeam("rwinch", teamId, accessToken).block()).isTrue()
    }

    @Test
    fun isMemberOfTeamWhenUserPendingThenTrue() {
        val body = """{
            "url": "https://api.github.com/teams/1/memberships/octocat",
            "role": "member",
            "state": "pending"
        }"""
        enqueue(body)

        assertThat(this.github.isMemberOfTeam("rwinch", teamId, accessToken).block()).isTrue()
    }

    @Test
    fun isMemberOfTeamWhenUserNotMemberThenFalse() {
        val response = response("").setResponseCode(HttpStatus.NOT_FOUND.value())
        server.enqueue(response)

        assertThat(this.github.isMemberOfTeam("rwinch", teamId, accessToken).block()).isFalse()
    }

    @Test
    fun isMemberOfTeamWhenErrorThenFalse() {
        val response = response("Oops").setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
        server.enqueue(response)

        assertThatCode { this.github.isMemberOfTeam("rwinch", teamId, accessToken).block() }
                .hasMessage("Failed to determine if rwinch is a part of 482984 Got status 500 INTERNAL_SERVER_ERROR and body Oops")
    }

    @Test
    fun findMilestoneNumberByTitleWhenFound() {
        enqueue("""
        [
  {
    "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
    "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
    "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
    "id": 1002604,
    "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
    "number": 1,
    "state": "open",
    "title": "v1.0",
    "description": "Tracking milestone for version 1.0",
    "creator": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "followers_url": "https://api.github.com/users/octocat/followers",
      "following_url": "https://api.github.com/users/octocat/following{/other_user}",
      "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
      "organizations_url": "https://api.github.com/users/octocat/orgs",
      "repos_url": "https://api.github.com/users/octocat/repos",
      "events_url": "https://api.github.com/users/octocat/events{/privacy}",
      "received_events_url": "https://api.github.com/users/octocat/received_events",
      "type": "User",
      "site_admin": false
    },
    "open_issues": 4,
    "closed_issues": 8,
    "created_at": "2011-04-10T20:09:31Z",
    "updated_at": "2014-03-03T18:58:10Z",
    "closed_at": "2013-02-12T13:22:01Z",
    "due_on": "2012-10-09T23:39:01Z"
  }
]
        """)

        assertThat(this.github.findMilestoneNumberByTitle(repository, "v1.0").block()).isEqualTo(1)

        val findMilestoneRequest = server.takeRequest()
        assertThat(findMilestoneRequest.method).isEqualTo(HttpMethod.GET.name());
        assertThat(findMilestoneRequest.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/milestones")
    }

    @Test
    fun findMilestoneNumberByTitleWhenFoundSecondResult() {
        enqueue("""
        [
  {
    "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
    "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
    "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
    "id": 1002604,
    "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
    "number": 1,
    "state": "open",
    "title": "v1.0",
    "description": "Tracking milestone for version 1.0",
    "creator": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "followers_url": "https://api.github.com/users/octocat/followers",
      "following_url": "https://api.github.com/users/octocat/following{/other_user}",
      "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
      "organizations_url": "https://api.github.com/users/octocat/orgs",
      "repos_url": "https://api.github.com/users/octocat/repos",
      "events_url": "https://api.github.com/users/octocat/events{/privacy}",
      "received_events_url": "https://api.github.com/users/octocat/received_events",
      "type": "User",
      "site_admin": false
    },
    "open_issues": 4,
    "closed_issues": 8,
    "created_at": "2011-04-10T20:09:31Z",
    "updated_at": "2014-03-03T18:58:10Z",
    "closed_at": "2013-02-12T13:22:01Z",
    "due_on": "2012-10-09T23:39:01Z"
  },
  {
    "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
    "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
    "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
    "id": 1002604,
    "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
    "number": 2,
    "state": "open",
    "title": "v2.0",
    "description": "Tracking milestone for version 1.0",
    "creator": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "followers_url": "https://api.github.com/users/octocat/followers",
      "following_url": "https://api.github.com/users/octocat/following{/other_user}",
      "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
      "organizations_url": "https://api.github.com/users/octocat/orgs",
      "repos_url": "https://api.github.com/users/octocat/repos",
      "events_url": "https://api.github.com/users/octocat/events{/privacy}",
      "received_events_url": "https://api.github.com/users/octocat/received_events",
      "type": "User",
      "site_admin": false
    },
    "open_issues": 4,
    "closed_issues": 8,
    "created_at": "2011-04-10T20:09:31Z",
    "updated_at": "2014-03-03T18:58:10Z",
    "closed_at": "2013-02-12T13:22:01Z",
    "due_on": "2012-10-09T23:39:01Z"
  }
]
        """)

        assertThat(this.github.findMilestoneNumberByTitle(repository, "v2.0").block()).isEqualTo(2)

        val findMilestoneRequest = server.takeRequest()
        assertThat(findMilestoneRequest.method).isEqualTo(HttpMethod.GET.name());
        assertThat(findMilestoneRequest.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/milestones")
    }

    @Test
    fun findMilestoneNumberByTitleWhenPaged() {
        val baseUrl = this.server.url("")
        val next = "${baseUrl}/resource?page=2"
        this.server.enqueue(response("""
        [
  {
    "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
    "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
    "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
    "id": 1002604,
    "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
    "number": 1,
    "state": "open",
    "title": "v1.0",
    "description": "Tracking milestone for version 1.0",
    "creator": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "followers_url": "https://api.github.com/users/octocat/followers",
      "following_url": "https://api.github.com/users/octocat/following{/other_user}",
      "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
      "organizations_url": "https://api.github.com/users/octocat/orgs",
      "repos_url": "https://api.github.com/users/octocat/repos",
      "events_url": "https://api.github.com/users/octocat/events{/privacy}",
      "received_events_url": "https://api.github.com/users/octocat/received_events",
      "type": "User",
      "site_admin": false
    },
    "open_issues": 4,
    "closed_issues": 8,
    "created_at": "2011-04-10T20:09:31Z",
    "updated_at": "2014-03-03T18:58:10Z",
    "closed_at": "2013-02-12T13:22:01Z",
    "due_on": "2012-10-09T23:39:01Z"
  }
]
        """).setHeader("Link", """<${next}>; rel="next", <${baseUrl}/resource?page=5>; rel="last""""))
        this.server.enqueue(response("""
        [
  {
    "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
    "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
    "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
    "id": 1002604,
    "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
    "number": 1,
    "state": "open",
    "title": "v1.0",
    "description": "Tracking milestone for version 1.0",
    "creator": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "followers_url": "https://api.github.com/users/octocat/followers",
      "following_url": "https://api.github.com/users/octocat/following{/other_user}",
      "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
      "organizations_url": "https://api.github.com/users/octocat/orgs",
      "repos_url": "https://api.github.com/users/octocat/repos",
      "events_url": "https://api.github.com/users/octocat/events{/privacy}",
      "received_events_url": "https://api.github.com/users/octocat/received_events",
      "type": "User",
      "site_admin": false
    },
    "open_issues": 4,
    "closed_issues": 8,
    "created_at": "2011-04-10T20:09:31Z",
    "updated_at": "2014-03-03T18:58:10Z",
    "closed_at": "2013-02-12T13:22:01Z",
    "due_on": "2012-10-09T23:39:01Z"
  }
]
        """).setHeader("Link", """<${next}>; rel="next", <${baseUrl}/resource?page=5>; rel="last""""))
        enqueue("""
        [
  {
    "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
    "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
    "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
    "id": 1002604,
    "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
    "number": 2,
    "state": "open",
    "title": "v2.0",
    "description": "Tracking milestone for version 1.0",
    "creator": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "followers_url": "https://api.github.com/users/octocat/followers",
      "following_url": "https://api.github.com/users/octocat/following{/other_user}",
      "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
      "organizations_url": "https://api.github.com/users/octocat/orgs",
      "repos_url": "https://api.github.com/users/octocat/repos",
      "events_url": "https://api.github.com/users/octocat/events{/privacy}",
      "received_events_url": "https://api.github.com/users/octocat/received_events",
      "type": "User",
      "site_admin": false
    },
    "open_issues": 4,
    "closed_issues": 8,
    "created_at": "2011-04-10T20:09:31Z",
    "updated_at": "2014-03-03T18:58:10Z",
    "closed_at": "2013-02-12T13:22:01Z",
    "due_on": "2012-10-09T23:39:01Z"
  }
]
        """)

        assertThat(this.github.findMilestoneNumberByTitle(repository, "v2.0").block()).isEqualTo(2)
    }

    @Test
    fun findMilestoneNumberByTitleWhenNotFound() {
        enqueueNotFound()

        StepVerifier.create(this.github.findMilestoneNumberByTitle(repository, "v1.0"))
                .verifyErrorSatisfies { e -> assertThat(e).hasMessageStartingWith("404 Not Found from GET") }
    }

    @Test
    fun createIssue() {
        enqueue("""
        {
          "id": 1,
          "node_id": "MDU6SXNzdWUx",
          "url": "https://api.github.com/repos/octocat/Hello-World/issues/1347",
          "repository_url": "https://api.github.com/repos/octocat/Hello-World",
          "labels_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/labels{/name}",
          "comments_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/comments",
          "events_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/events",
          "html_url": "https://github.com/octocat/Hello-World/issues/1347",
          "number": 1347,
          "state": "open",
          "title": "Found a bug",
          "body": "I'm having a problem with this.",
          "user": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "labels": [
            {
              "id": 208045946,
              "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
              "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
              "name": "bug",
              "description": "Something isn't working",
              "color": "f29513",
              "default": true
            }
          ],
          "assignee": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "assignees": [
            {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            }
          ],
          "milestone": {
            "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
            "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
            "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
            "id": 1002604,
            "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
            "number": 1,
            "state": "open",
            "title": "v1.0",
            "description": "Tracking milestone for version 1.0",
            "creator": {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            },
            "open_issues": 4,
            "closed_issues": 8,
            "created_at": "2011-04-10T20:09:31Z",
            "updated_at": "2014-03-03T18:58:10Z",
            "closed_at": "2013-02-12T13:22:01Z",
            "due_on": "2012-10-09T23:39:01Z"
          },
          "locked": true,
          "active_lock_reason": "too heated",
          "comments": 0,
          "pull_request": {
            "url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
            "html_url": "https://github.com/octocat/Hello-World/pull/1347",
            "diff_url": "https://github.com/octocat/Hello-World/pull/1347.diff",
            "patch_url": "https://github.com/octocat/Hello-World/pull/1347.patch"
          },
          "closed_at": null,
          "created_at": "2011-04-22T13:33:48Z",
          "updated_at": "2011-04-22T13:33:48Z",
          "closed_by": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          }
        }
        """)

        StepVerifier.create(this.github.createIssue(CreateIssue(repository, "Title", "Body", 1, listOf("label"), listOf("rwinch"))))
                .expectNext(IssueRef(repository, 1347))
                .verifyComplete()

        val createIssueRequest = server.takeRequest()
        assertThat(createIssueRequest.method).isEqualTo(HttpMethod.POST.name());
        assertThat(createIssueRequest.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/issues")
        JSONAssert.assertEquals("""{"title":"Title","body":"Body","milestone":1,"labels":["label"],"assignees":["rwinch"]}""", createIssueRequest.body.readUtf8(), true)
    }

    @Test
    fun createIssueWhenUnauthorized() {
        this.server.enqueue(MockResponse()
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .setResponseCode(HttpStatus.UNAUTHORIZED.value()))

        StepVerifier.create(this.github.createIssue(CreateIssue(repository, "Title", "Body", 1, listOf("label"), listOf("rwinch"))))
                .verifyErrorSatisfies { e -> assertThat(e).hasMessage("Cannot create issue for CreateIssue(ref=RepositoryRef(fullName=rwinch/repository), title=Title, body=Body, milestone=1, labels=[label], assignees=[rwinch]) Got status 401 UNAUTHORIZED Got status 401 UNAUTHORIZED and body <empty body>") }
    }

    @Test
    fun closeIssue() {
        enqueue("""
    {
          "id": 1,
          "node_id": "MDU6SXNzdWUx",
          "url": "https://api.github.com/repos/octocat/Hello-World/issues/1347",
          "repository_url": "https://api.github.com/repos/octocat/Hello-World",
          "labels_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/labels{/name}",
          "comments_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/comments",
          "events_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/events",
          "html_url": "https://github.com/octocat/Hello-World/issues/1347",
          "number": 1347,
          "state": "open",
          "title": "Found a bug",
          "body": "I'm having a problem with this.",
          "user": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "labels": [
            {
              "id": 208045946,
              "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
              "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
              "name": "bug",
              "description": "Something isn't working",
              "color": "f29513",
              "default": true
            }
          ],
          "assignee": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "assignees": [
            {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            }
          ],
          "milestone": {
            "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
            "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
            "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
            "id": 1002604,
            "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
            "number": 1,
            "state": "open",
            "title": "v1.0",
            "description": "Tracking milestone for version 1.0",
            "creator": {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            },
            "open_issues": 4,
            "closed_issues": 8,
            "created_at": "2011-04-10T20:09:31Z",
            "updated_at": "2014-03-03T18:58:10Z",
            "closed_at": "2013-02-12T13:22:01Z",
            "due_on": "2012-10-09T23:39:01Z"
          },
          "locked": true,
          "active_lock_reason": "too heated",
          "comments": 0,
          "pull_request": {
            "url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
            "html_url": "https://github.com/octocat/Hello-World/pull/1347",
            "diff_url": "https://github.com/octocat/Hello-World/pull/1347.diff",
            "patch_url": "https://github.com/octocat/Hello-World/pull/1347.patch"
          },
          "closed_at": null,
          "created_at": "2011-04-22T13:33:48Z",
          "updated_at": "2011-04-22T13:33:48Z",
          "closed_by": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          }
        }""")

        StepVerifier.create(this.github.closeIssue(issue))
            .verifyComplete()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo(HttpMethod.PATCH.name());
        assertThat(request.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/issues/1347")
        JSONAssert.assertEquals("""{"state":"closed"}""", request.body.readUtf8(), true)
    }

    @Test
    fun updateLabelsWhen200ThenRequestCorrect() {
        enqueue("""
        {
          "id": 1,
          "node_id": "MDU6SXNzdWUx",
          "url": "https://api.github.com/repos/octocat/Hello-World/issues/1347",
          "repository_url": "https://api.github.com/repos/octocat/Hello-World",
          "labels_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/labels{/name}",
          "comments_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/comments",
          "events_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/events",
          "html_url": "https://github.com/octocat/Hello-World/issues/1347",
          "number": 1347,
          "state": "open",
          "title": "Found a bug",
          "body": "I'm having a problem with this.",
          "user": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "labels": [
            {
              "id": 208045946,
              "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
              "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
              "name": "bug",
              "description": "Something isn't working",
              "color": "f29513",
              "default": true
            }
          ],
          "assignee": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "assignees": [
            {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            }
          ],
          "milestone": {
            "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
            "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
            "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
            "id": 1002604,
            "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
            "number": 1,
            "state": "open",
            "title": "v1.0",
            "description": "Tracking milestone for version 1.0",
            "creator": {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            },
            "open_issues": 4,
            "closed_issues": 8,
            "created_at": "2011-04-10T20:09:31Z",
            "updated_at": "2014-03-03T18:58:10Z",
            "closed_at": "2013-02-12T13:22:01Z",
            "due_on": "2012-10-09T23:39:01Z"
          },
          "locked": true,
          "active_lock_reason": "too heated",
          "comments": 0,
          "pull_request": {
            "url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
            "html_url": "https://github.com/octocat/Hello-World/pull/1347",
            "diff_url": "https://github.com/octocat/Hello-World/pull/1347.diff",
            "patch_url": "https://github.com/octocat/Hello-World/pull/1347.patch"
          },
          "closed_at": null,
          "created_at": "2011-04-22T13:33:48Z",
          "updated_at": "2011-04-22T13:33:48Z",
          "closed_by": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          }
        }""")

        StepVerifier.create(this.github.updateLabels(issue, listOf("bugs")))
            .verifyComplete()

        val request = server.takeRequest()
        assertThat(request.requestUrl?.toUrl()?.toExternalForm()).isEqualTo("${baseUrl}repos/${issue.repository.fullName}/issues/${issue.number}")
        assertThat(request.method).isEqualTo(HttpMethod.PATCH.name())
        JSONAssert.assertEquals("""{labels: ["bugs"]}""", request.body.readUtf8(), true)
    }

    @Test
    fun comment() {
        enqueue("""
        {
          "id": 1,
          "node_id": "MDEyOklzc3VlQ29tbWVudDE=",
          "url": "https://api.github.com/repos/octocat/Hello-World/issues/comments/1",
          "html_url": "https://github.com/octocat/Hello-World/issues/1347#issuecomment-1",
          "body": "Me too",
          "user": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "created_at": "2011-04-14T16:00:49Z",
          "updated_at": "2011-04-14T16:00:49Z"
        }""")

        StepVerifier.create(this.github.comment(issue, "Me too"))
                .verifyComplete()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo(HttpMethod.POST.name());
        assertThat(request.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/issues/1347/comments")
        JSONAssert.assertEquals("""{"body":"Me too"}""", request.body.readUtf8(), true)
    }

    @Test
    fun findIssue() {
        enqueue("""{
          "id": 1,
          "node_id": "MDU6SXNzdWUx",
          "url": "https://api.github.com/repos/octocat/Hello-World/issues/1347",
          "repository_url": "https://api.github.com/repos/octocat/Hello-World",
          "labels_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/labels{/name}",
          "comments_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/comments",
          "events_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/events",
          "html_url": "https://github.com/octocat/Hello-World/issues/1347",
          "number": 1347,
          "state": "open",
          "title": "Found a bug",
          "body": "I'm having a problem with this.",
          "user": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "labels": [
            {
              "id": 208045946,
              "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
              "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
              "name": "bug",
              "description": "Something isn't working",
              "color": "f29513",
              "default": true
            }
          ],
          "assignee": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          },
          "assignees": [
            {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            }
          ],
          "milestone": {
            "url": "https://api.github.com/repos/octocat/Hello-World/milestones/1",
            "html_url": "https://github.com/octocat/Hello-World/milestones/v1.0",
            "labels_url": "https://api.github.com/repos/octocat/Hello-World/milestones/1/labels",
            "id": 1002604,
            "node_id": "MDk6TWlsZXN0b25lMTAwMjYwNA==",
            "number": 1,
            "state": "open",
            "title": "v1.0",
            "description": "Tracking milestone for version 1.0",
            "creator": {
              "login": "octocat",
              "id": 1,
              "node_id": "MDQ6VXNlcjE=",
              "avatar_url": "https://github.com/images/error/octocat_happy.gif",
              "gravatar_id": "",
              "url": "https://api.github.com/users/octocat",
              "html_url": "https://github.com/octocat",
              "followers_url": "https://api.github.com/users/octocat/followers",
              "following_url": "https://api.github.com/users/octocat/following{/other_user}",
              "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
              "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
              "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
              "organizations_url": "https://api.github.com/users/octocat/orgs",
              "repos_url": "https://api.github.com/users/octocat/repos",
              "events_url": "https://api.github.com/users/octocat/events{/privacy}",
              "received_events_url": "https://api.github.com/users/octocat/received_events",
              "type": "User",
              "site_admin": false
            },
            "open_issues": 4,
            "closed_issues": 8,
            "created_at": "2011-04-10T20:09:31Z",
            "updated_at": "2014-03-03T18:58:10Z",
            "closed_at": "2013-02-12T13:22:01Z",
            "due_on": "2012-10-09T23:39:01Z"
          },
          "locked": true,
          "active_lock_reason": "too heated",
          "comments": 0,
          "pull_request": {
            "url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
            "html_url": "https://github.com/octocat/Hello-World/pull/1347",
            "diff_url": "https://github.com/octocat/Hello-World/pull/1347.diff",
            "patch_url": "https://github.com/octocat/Hello-World/pull/1347.patch"
          },
          "closed_at": null,
          "created_at": "2011-04-22T13:33:48Z",
          "updated_at": "2011-04-22T13:33:48Z",
          "closed_by": {
            "login": "octocat",
            "id": 1,
            "node_id": "MDQ6VXNlcjE=",
            "avatar_url": "https://github.com/images/error/octocat_happy.gif",
            "gravatar_id": "",
            "url": "https://api.github.com/users/octocat",
            "html_url": "https://github.com/octocat",
            "followers_url": "https://api.github.com/users/octocat/followers",
            "following_url": "https://api.github.com/users/octocat/following{/other_user}",
            "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
            "organizations_url": "https://api.github.com/users/octocat/orgs",
            "repos_url": "https://api.github.com/users/octocat/repos",
            "events_url": "https://api.github.com/users/octocat/events{/privacy}",
            "received_events_url": "https://api.github.com/users/octocat/received_events",
            "type": "User",
            "site_admin": false
          }
        }""")

        StepVerifier.create(this.github.findIssue(issue))
            .assertNext { i ->
                assertThat(i.number).isEqualTo(1347)
                assertThat(i.title).isEqualTo("Found a bug")
                assertThat(i.labels.map { l -> l.name }).containsOnly("bug")
            }
            .verifyComplete()

        val findIssueRequest = server.takeRequest()
        assertThat(findIssueRequest.method).isEqualTo(HttpMethod.GET.name());
        assertThat(findIssueRequest.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/issues/1347")
    }

    @Test
    fun findIssueWhenNotFound() {
        enqueueNotFound()

        StepVerifier.create(this.github.findIssue(issue))
                .verifyErrorSatisfies { e -> assertThat(e).hasMessage("Could not find issue IssueRef(repository=RepositoryRef(fullName=rwinch/repository), number=1347) Got status 404 NOT_FOUND and body <empty body>") }
    }

    @Test
    fun findFile() {
        enqueue("""{
          "type": "file",
          "encoding": "base64",
          "size": 5362,
          "name": "README.md",
          "path": "README.md",
          "content": "SGVsbG8gR2l0SHVi",
          "sha": "3d21ec53a331a6f037a91c368710b99387d012c1",
          "url": "https://api.github.com/repos/octokit/octokit.rb/contents/README.md",
          "git_url": "https://api.github.com/repos/octokit/octokit.rb/git/blobs/3d21ec53a331a6f037a91c368710b99387d012c1",
          "html_url": "https://github.com/octokit/octokit.rb/blob/main/README.md",
          "download_url": "https://raw.githubusercontent.com/octokit/octokit.rb/main/README.md",
          "_links": {
            "git": "https://api.github.com/repos/octokit/octokit.rb/git/blobs/3d21ec53a331a6f037a91c368710b99387d012c1",
            "self": "https://api.github.com/repos/octokit/octokit.rb/contents/README.md",
            "html": "https://github.com/octokit/octokit.rb/blob/main/README.md"
          }
        }""")

        StepVerifier.create(this.github.findFile(branch, "README.md"))
                .assertNext { f ->
                    assertThat(f).hasContent("Hello GitHub")
                }
                .verifyComplete()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo(HttpMethod.GET.name());
        assertThat(request.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/contents/README.md?ref=refs/heads/main")
    }

    @Test
    fun findFileWhenNotFound() {
        enqueueNotFound()

        StepVerifier.create(this.github.findFile(branch, "README.md"))
            .verifyErrorSatisfies { e -> assertThat(e).hasMessage("Could not get file README.md for BranchRef(repository=RepositoryRef(fullName=rwinch/repository), ref=refs/heads/main) Got status 404 NOT_FOUND and body <empty body>") }

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo(HttpMethod.GET.name());
        assertThat(request.requestUrl?.toUrl()?.toExternalForm()).endsWith("/repos/rwinch/repository/contents/README.md?ref=refs/heads/main")
    }

    @Test
    fun findIssueTimelineWhenCrossReferenced() {
        enqueue("""[
    {
        "actor": {
            "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
            "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
            "followers_url": "https://api.github.com/users/rwinch/followers",
            "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
            "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
            "gravatar_id": "",
            "html_url": "https://github.com/rwinch",
            "id": 362503,
            "login": "rwinch",
            "node_id": "MDQ6VXNlcjM2MjUwMw==",
            "organizations_url": "https://api.github.com/users/rwinch/orgs",
            "received_events_url": "https://api.github.com/users/rwinch/received_events",
            "repos_url": "https://api.github.com/users/rwinch/repos",
            "site_admin": false,
            "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
            "type": "User",
            "url": "https://api.github.com/users/rwinch"
        },
        "created_at": "2018-12-13T21:03:40Z",
        "event": "cross-referenced",
        "source": {
            "issue": {
                "assignee": {
                    "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
                    "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
                    "followers_url": "https://api.github.com/users/rwinch/followers",
                    "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
                    "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
                    "gravatar_id": "",
                    "html_url": "https://github.com/rwinch",
                    "id": 362503,
                    "login": "rwinch",
                    "node_id": "MDQ6VXNlcjM2MjUwMw==",
                    "organizations_url": "https://api.github.com/users/rwinch/orgs",
                    "received_events_url": "https://api.github.com/users/rwinch/received_events",
                    "repos_url": "https://api.github.com/users/rwinch/repos",
                    "site_admin": false,
                    "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
                    "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
                    "type": "User",
                    "url": "https://api.github.com/users/rwinch"
                },
                "assignees": [
                    {
                        "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
                        "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
                        "followers_url": "https://api.github.com/users/rwinch/followers",
                        "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
                        "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
                        "gravatar_id": "",
                        "html_url": "https://github.com/rwinch",
                        "id": 362503,
                        "login": "rwinch",
                        "node_id": "MDQ6VXNlcjM2MjUwMw==",
                        "organizations_url": "https://api.github.com/users/rwinch/orgs",
                        "received_events_url": "https://api.github.com/users/rwinch/received_events",
                        "repos_url": "https://api.github.com/users/rwinch/repos",
                        "site_admin": false,
                        "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
                        "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
                        "type": "User",
                        "url": "https://api.github.com/users/rwinch"
                    }
                ],
                "author_association": "OWNER",
                "body": "Backport of #30",
                "closed_at": "2018-12-13T21:03:42Z",
                "comments": 1,
                "comments_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/32/comments",
                "created_at": "2018-12-13T21:03:40Z",
                "events_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/32/events",
                "html_url": "https://github.com/rwinch/deleteme-backport-test/issues/32",
                "id": 390859059,
                "labels": [
                    {
                        "color": "fbca04",
                        "default": false,
                        "id": 1153297086,
                        "name": "Backport",
                        "node_id": "MDU6TGFiZWwxMTUzMjk3MDg2",
                        "url": "https://api.github.com/repos/rwinch/deleteme-backport-test/labels/Backport"
                    }
                ],
                "labels_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/32/labels{/name}",
                "locked": false,
                "milestone": {
                    "closed_at": null,
                    "closed_issues": 1,
                    "created_at": "2018-12-13T21:02:20Z",
                    "creator": {
                        "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
                        "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
                        "followers_url": "https://api.github.com/users/rwinch/followers",
                        "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
                        "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
                        "gravatar_id": "",
                        "html_url": "https://github.com/rwinch",
                        "id": 362503,
                        "login": "rwinch",
                        "node_id": "MDQ6VXNlcjM2MjUwMw==",
                        "organizations_url": "https://api.github.com/users/rwinch/orgs",
                        "received_events_url": "https://api.github.com/users/rwinch/received_events",
                        "repos_url": "https://api.github.com/users/rwinch/repos",
                        "site_admin": false,
                        "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
                        "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
                        "type": "User",
                        "url": "https://api.github.com/users/rwinch"
                    },
                    "description": "",
                    "due_on": null,
                    "html_url": "https://github.com/rwinch/deleteme-backport-test/milestone/3",
                    "id": 3897830,
                    "labels_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/milestones/3/labels",
                    "node_id": "MDk6TWlsZXN0b25lMzg5NzgzMA==",
                    "number": 3,
                    "open_issues": 0,
                    "state": "open",
                    "title": "1.1.0",
                    "updated_at": "2018-12-13T21:03:43Z",
                    "url": "https://api.github.com/repos/rwinch/deleteme-backport-test/milestones/3"
                },
                "node_id": "MDU6SXNzdWUzOTA4NTkwNTk=",
                "number": 32,
                "repository": {
                    "archive_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/{archive_format}{/branch}",
                    "archived": false,
                    "assignees_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/assignees{/user}",
                    "blobs_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/git/blobs{/sha}",
                    "branches_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/branches{/branch}",
                    "clone_url": "https://github.com/rwinch/deleteme-backport-test.git",
                    "collaborators_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/collaborators{/collaborator}",
                    "comments_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/comments{/number}",
                    "commits_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/commits{/sha}",
                    "compare_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/compare/{base}...{head}",
                    "contents_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/contents/{+path}",
                    "contributors_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/contributors",
                    "created_at": "2018-12-04T20:05:53Z",
                    "default_branch": "main",
                    "deployments_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/deployments",
                    "description": null,
                    "downloads_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/downloads",
                    "events_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/events",
                    "fork": false,
                    "forks": 0,
                    "forks_count": 0,
                    "forks_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/forks",
                    "full_name": "rwinch/deleteme-backport-test",
                    "git_commits_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/git/commits{/sha}",
                    "git_refs_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/git/refs{/sha}",
                    "git_tags_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/git/tags{/sha}",
                    "git_url": "git://github.com/rwinch/deleteme-backport-test.git",
                    "has_downloads": true,
                    "has_issues": true,
                    "has_pages": false,
                    "has_projects": true,
                    "has_wiki": true,
                    "homepage": null,
                    "hooks_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/hooks",
                    "html_url": "https://github.com/rwinch/deleteme-backport-test",
                    "id": 160412400,
                    "issue_comment_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/comments{/number}",
                    "issue_events_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/events{/number}",
                    "issues_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues{/number}",
                    "keys_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/keys{/key_id}",
                    "labels_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/labels{/name}",
                    "language": null,
                    "languages_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/languages",
                    "license": null,
                    "merges_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/merges",
                    "milestones_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/milestones{/number}",
                    "mirror_url": null,
                    "name": "deleteme-backport-test",
                    "node_id": "MDEwOlJlcG9zaXRvcnkxNjA0MTI0MDA=",
                    "notifications_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/notifications{?since,all,participating}",
                    "open_issues": 1,
                    "open_issues_count": 1,
                    "owner": {
                        "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
                        "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
                        "followers_url": "https://api.github.com/users/rwinch/followers",
                        "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
                        "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
                        "gravatar_id": "",
                        "html_url": "https://github.com/rwinch",
                        "id": 362503,
                        "login": "rwinch",
                        "node_id": "MDQ6VXNlcjM2MjUwMw==",
                        "organizations_url": "https://api.github.com/users/rwinch/orgs",
                        "received_events_url": "https://api.github.com/users/rwinch/received_events",
                        "repos_url": "https://api.github.com/users/rwinch/repos",
                        "site_admin": false,
                        "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
                        "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
                        "type": "User",
                        "url": "https://api.github.com/users/rwinch"
                    },
                    "private": false,
                    "pulls_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/pulls{/number}",
                    "pushed_at": "2018-12-13T21:03:38Z",
                    "releases_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/releases{/id}",
                    "size": 12,
                    "ssh_url": "git@github.com:rwinch/deleteme-backport-test.git",
                    "stargazers_count": 0,
                    "stargazers_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/stargazers",
                    "statuses_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/statuses/{sha}",
                    "subscribers_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/subscribers",
                    "subscription_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/subscription",
                    "svn_url": "https://github.com/rwinch/deleteme-backport-test",
                    "tags_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/tags",
                    "teams_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/teams",
                    "trees_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/git/trees{/sha}",
                    "updated_at": "2018-12-05T20:33:00Z",
                    "url": "https://api.github.com/repos/rwinch/deleteme-backport-test",
                    "watchers": 0,
                    "watchers_count": 0
                },
                "repository_url": "https://api.github.com/repos/rwinch/deleteme-backport-test",
                "state": "closed",
                "title": "Issue",
                "updated_at": "2018-12-13T21:03:43Z",
                "url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/32",
                "user": {
                    "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
                    "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
                    "followers_url": "https://api.github.com/users/rwinch/followers",
                    "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
                    "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
                    "gravatar_id": "",
                    "html_url": "https://github.com/rwinch",
                    "id": 362503,
                    "login": "rwinch",
                    "node_id": "MDQ6VXNlcjM2MjUwMw==",
                    "organizations_url": "https://api.github.com/users/rwinch/orgs",
                    "received_events_url": "https://api.github.com/users/rwinch/received_events",
                    "repos_url": "https://api.github.com/users/rwinch/repos",
                    "site_admin": false,
                    "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
                    "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
                    "type": "User",
                    "url": "https://api.github.com/users/rwinch"
                }
            },
            "type": "issue"
        },
        "updated_at": "2018-12-13T21:03:40Z"
    }
]

        """.trimIndent())

        val timeline = github.findIssueTimeline(issue).collectList().block()!!

        assertThat(timeline).hasSize(1)
        val e = timeline.get(0)!!
        assertThat(e.event).isEqualTo("cross-referenced")
        assertThat(e.source?.type).isEqualTo("issue")
        assertThat(e.source?.issue?.body).isEqualTo("Backport of #30")
        assertThat(e.source?.issue?.milestone?.number).isEqualTo(3)
        assertThat(e.source?.issue?.milestone?.title).isEqualTo("1.1.0")

        assertThat(server.takeRequest().headers.get("Accept")).isEqualTo("application/vnd.github.mockingbird-preview")

    }

    @Test
    fun findIssueTimelineWhenReferencedInCommit() {
        enqueue("""[
    {
        "actor": {
            "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
            "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
            "followers_url": "https://api.github.com/users/rwinch/followers",
            "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
            "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
            "gravatar_id": "",
            "html_url": "https://github.com/rwinch",
            "id": 362503,
            "login": "rwinch",
            "node_id": "MDQ6VXNlcjM2MjUwMw==",
            "organizations_url": "https://api.github.com/users/rwinch/orgs",
            "received_events_url": "https://api.github.com/users/rwinch/received_events",
            "repos_url": "https://api.github.com/users/rwinch/repos",
            "site_admin": false,
            "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
            "type": "User",
            "url": "https://api.github.com/users/rwinch"
        },
        "commit_id": "357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "commit_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/commits/357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "created_at": "2018-12-13T21:03:38Z",
        "event": "referenced",
        "id": 2025328930,
        "node_id": "MDE1OlJlZmVyZW5jZWRFdmVudDIwMjUzMjg5MzA=",
        "url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/events/2025328930"
    }
]

        """.trimIndent())

        val timeline = github.findIssueTimeline(issue).collectList().block()!!

        assertThat(timeline).hasSize(1)
        val e = timeline.get(0)!!
        assertThat(e.event).isEqualTo("referenced")
    }

    @Test
    fun findIssueTimelineWhenPagination() {
        val baseUrl = this.server.url("")
        val next = "${baseUrl}/resource?page=2"
        this.server.enqueue(response("""[
    {
        "actor": {
            "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
            "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
            "followers_url": "https://api.github.com/users/rwinch/followers",
            "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
            "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
            "gravatar_id": "",
            "html_url": "https://github.com/rwinch",
            "id": 362503,
            "login": "rwinch",
            "node_id": "MDQ6VXNlcjM2MjUwMw==",
            "organizations_url": "https://api.github.com/users/rwinch/orgs",
            "received_events_url": "https://api.github.com/users/rwinch/received_events",
            "repos_url": "https://api.github.com/users/rwinch/repos",
            "site_admin": false,
            "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
            "type": "User",
            "url": "https://api.github.com/users/rwinch"
        },
        "commit_id": "357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "commit_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/commits/357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "created_at": "2018-12-13T21:03:38Z",
        "event": "referenced",
        "id": 2025328930,
        "node_id": "MDE1OlJlZmVyZW5jZWRFdmVudDIwMjUzMjg5MzA=",
        "url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/events/2025328930"
    }
]

        """.trimIndent()).setHeader("Link", """<${next}>; rel="next", <${baseUrl}/resource?page=5>; rel="last""""))
        enqueue("""[
    {
        "actor": {
            "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
            "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
            "followers_url": "https://api.github.com/users/rwinch/followers",
            "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
            "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
            "gravatar_id": "",
            "html_url": "https://github.com/rwinch",
            "id": 362503,
            "login": "rwinch",
            "node_id": "MDQ6VXNlcjM2MjUwMw==",
            "organizations_url": "https://api.github.com/users/rwinch/orgs",
            "received_events_url": "https://api.github.com/users/rwinch/received_events",
            "repos_url": "https://api.github.com/users/rwinch/repos",
            "site_admin": false,
            "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
            "type": "User",
            "url": "https://api.github.com/users/rwinch"
        },
        "commit_id": "357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "commit_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/commits/357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "created_at": "2018-12-13T21:03:38Z",
        "event": "referenced",
        "id": 2025328930,
        "node_id": "MDE1OlJlZmVyZW5jZWRFdmVudDIwMjUzMjg5MzA=",
        "url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/events/2025328930"
    }
]""")

        val timeline = github.findIssueTimeline(issue).collectList().block()!!

        assertThat(timeline).hasSize(2)
        assertThat(this.server.takeRequest().requestUrl?.toUrl()).hasPath("/repos/rwinch/repository/issues/1347/timeline")
        assertThat(this.server.takeRequest().requestUrl?.toUrl()?.toExternalForm()).isEqualTo(next)
    }

    @Test
    fun findIssueTimelineWhenPaginationWhenNoNext() {
        val baseUrl = this.server.url("")
        val next = "${baseUrl}repositories/1148753/issues/22603/timeline?page=1"
        val nextHeader = "<${next}>; rel=\"prev\", <${baseUrl}repositories/1148753/issues/22603/timeline?page=1>; rel=\"first\"; rel=\"last\""
        this.server.enqueue(response("""[
    {
        "actor": {
            "avatar_url": "https://avatars0.githubusercontent.com/u/362503?v=4",
            "events_url": "https://api.github.com/users/rwinch/events{/privacy}",
            "followers_url": "https://api.github.com/users/rwinch/followers",
            "following_url": "https://api.github.com/users/rwinch/following{/other_user}",
            "gists_url": "https://api.github.com/users/rwinch/gists{/gist_id}",
            "gravatar_id": "",
            "html_url": "https://github.com/rwinch",
            "id": 362503,
            "login": "rwinch",
            "node_id": "MDQ6VXNlcjM2MjUwMw==",
            "organizations_url": "https://api.github.com/users/rwinch/orgs",
            "received_events_url": "https://api.github.com/users/rwinch/received_events",
            "repos_url": "https://api.github.com/users/rwinch/repos",
            "site_admin": false,
            "starred_url": "https://api.github.com/users/rwinch/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/rwinch/subscriptions",
            "type": "User",
            "url": "https://api.github.com/users/rwinch"
        },
        "commit_id": "357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "commit_url": "https://api.github.com/repos/rwinch/deleteme-backport-test/commits/357f2a77c9bcf7e0920e0107e35380f9dc50dd9e",
        "created_at": "2018-12-13T21:03:38Z",
        "event": "referenced",
        "id": 2025328930,
        "node_id": "MDE1OlJlZmVyZW5jZWRFdmVudDIwMjUzMjg5MzA=",
        "url": "https://api.github.com/repos/rwinch/deleteme-backport-test/issues/events/2025328930"
    }
]
        """.trimIndent()).setHeader("Link", nextHeader))

        val timeline = github.findIssueTimeline(issue).collectList().block()!!

        assertThat(timeline).hasSize(1)
        assertThat(this.server.takeRequest().requestUrl?.toUrl()).hasPath("/repos/rwinch/repository/issues/1347/timeline")
    }
    @Test
    fun findLabelsWhenSinglePageThenWorks() {
        enqueue("""[
          {
            "id": 208045946,
            "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
            "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
            "name": "bug",
            "description": "Something isn't working",
            "color": "f29513",
            "default": true
          },
          {
            "id": 208045947,
            "node_id": "MDU6TGFiZWwyMDgwNDU5NDc=",
            "url": "https://api.github.com/repos/octocat/Hello-World/labels/enhancement",
            "name": "enhancement",
            "description": "New feature or request",
            "color": "a2eeef",
            "default": false
          }
        ]""")

        val labels = this.github.findLabels(repository)
                .map { label -> label.name }
                .collectList()
                .block()

        assertThat(labels).containsOnly("bug", "enhancement")
    }

    @Test
    fun findLabelsWhenPagedThenWorks() {
        val baseUrl = this.server.url("")
        val next = "${baseUrl}/resource?page=2"
        this.server.enqueue(response("""[
          {
            "id": 208045946,
            "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
            "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
            "name": "bug",
            "description": "Something isn't working",
            "color": "f29513",
            "default": true
          }
        ]""").setHeader("Link", """<${next}>; rel="next", <${baseUrl}/resource?page=5>; rel="last""""))
        this.server.enqueue(response("""[
          {
            "id": 208045947,
            "node_id": "MDU6TGFiZWwyMDgwNDU5NDc=",
            "url": "https://api.github.com/repos/octocat/Hello-World/labels/enhancement",
            "name": "enhancement",
            "description": "New feature or request",
            "color": "a2eeef",
            "default": false
          }
        ]""").setHeader("Link", """<${next}>; rel="next", <${baseUrl}/resource?page=5>; rel="last""""))
        enqueue("""[
          {
            "id": 56789,
            "node_id": "SFLDJFSDLKJ=",
            "url": "https://api.github.com/repos/octocat/Hello-World/labels/another",
            "name": "another",
            "description": "Another",
            "color": "f29513",
            "default": false
          }
        ]""")

        val labels = this.github.findLabels(repository)
                .map { label -> label.name }
                .collectList()
                .block()
        assertThat(labels).containsOnly("bug", "enhancement", "another")
    }

    fun enqueueNotFound() {
        this.server.enqueue(MockResponse()
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .setResponseCode(HttpStatus.NOT_FOUND.value()))
    }

    fun enqueue(body : String) {
        this.server.enqueue(response(body))
    }

    fun response(body : String) : MockResponse {
        return MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBody(body.trimIndent())
    }
}
