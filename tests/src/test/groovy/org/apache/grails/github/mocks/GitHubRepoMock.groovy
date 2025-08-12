/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.github.mocks

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.TagOpt
import org.eclipse.jgit.transport.URIish
import org.testcontainers.containers.Container
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class GitHubRepoMock implements Closeable {

    GenericContainer gitHost
    Network network
    Path actionRepoPath

    GitHubRepoMock(Path actionRepoPath, Network network) {
        this.actionRepoPath = actionRepoPath
        this.network = network
    }

    /**
     * Starts up the git server in a container & setups initial credentials
     */
    void init() {
        gitHost = new GenericContainer<>('gitea/gitea:1.24')
                .withNetwork(network).withNetworkAliases('gitea')
                .withExposedPorts(3000)
                .withEnv('GITEA__server__ROOT_URL', 'http://gitea:3000/')
                .withEnv('GITEA__security__INSTALL_LOCK', 'true')
                .withEnv('GITEA__service__DISABLE_REGISTRATION', 'true')
                .withEnv('GITEA__database__DB_TYPE', 'sqlite3')
                .withEnv('GITEA__repository__DEFAULT_BRANCH', 'main')
                .waitingFor(Wait.forHttp('/').forPort(3000))
        gitHost.start()
        Container.ExecResult create = gitHost.execInContainer(
                'su-exec', 'git',
                'gitea', 'admin', 'user', 'create',
                '--admin', '--username', 'ci', '--password', 'pass',
                '--email', 'ci@example.com', '--must-change-password=false'
        )
        if (create.getExitCode() != 0) {
            throw new IllegalStateException("Create user failed: ${create.getStderr()}" as String)
        }

        String base = "http://${gitHost.getHost()}:${gitHost.getMappedPort(3000)}" as String
        HttpClient http = HttpClient.newHttpClient()
        String basic = "Basic ${Base64.getEncoder().encodeToString('ci:pass'.getBytes(StandardCharsets.UTF_8))}" as String

        HttpRequest createRepo = HttpRequest.newBuilder(
                URI.create("$base/api/v1/user/repos" as String))
                .header('Authorization', basic)
                .header('Content-Type', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"widgets\",\"private\":false}"))
                .build()
        def response = http.send(createRepo, HttpResponse.BodyHandlers.ofString())
        response.statusCode() == 201
    }

    String getExternalGitHostUrl() {
        "http://ci:pass@${gitHost.getHost()}:${gitHost.getMappedPort(3000)}/ci/widgets.git"
    }

    void populateRepository(String storedProjectVersion, String tag, List<String> additionalBranchNames = ['7.0.x']) {
        Path temp = Files.createTempDirectory('ref-project-version')
        try {
            try (Repository repo = FileRepositoryBuilder.create(temp.resolve('.git').toFile())) {
                repo.create()

                try (Git git = Git.open(temp.toFile())) {
                    // Example project
                    Files.writeString(temp.resolve('README.md'), '# demo\n')
                    Files.writeString(temp.resolve('gradle.properties'), "projectVersion=${storedProjectVersion}\n")
                    assert git.add().addFilepattern('.').call()
                    assert git.commit()
                            .setAuthor('CI', 'ci@example.com')
                            .setCommitter('CI', 'ci@example.com')
                            .setSign(false)
                            .setMessage('init').call()

                    assert git.branchRename().setNewName('main').call()

                    for (String additionalBranch : additionalBranchNames) {
                        assert git.branchCreate().setName(additionalBranch).setStartPoint('main').call()
                    }

                    def tagRef = git.tag().setName(tag)
                            .setMessage('My Release Message')
                            .call()
                    assert tagRef

                    assert git.remoteAdd().setName('origin').setUri(new URIish(externalGitHostUrl)).call()

                    List<RefSpec> specs = ['main', additionalBranchNames].flatten().unique().collect { String branch ->
                        new RefSpec("refs/heads/${branch}:refs/heads/${branch}" as String)
                    }

                    PushCommand push = git.push()
                            .setRemote('origin')
                            .setRefSpecs(specs)
                            .setPushAll()
                            .setPushTags()
                    def pushResult = push.call()
                    assert pushResult

                    specs.each { RefSpec spec ->
                        assert repo.findRef(spec.destination)
                    }
                }
            }
        }
        finally {
            temp.toFile().deleteDir()
        }
    }

    void stageRepositoryForAction(String refName) {
        cloneRepo(actionRepoPath, refName) { Git git ->
            String internalGitHostUrl = 'http://ci:pass@gitea:3000/ci/widgets.git'
            assert git.remoteSetUrl().setRemoteName('origin').setRemoteUri(new URIish(internalGitHostUrl)).call()
        }
    }

    void setProjectVersion(String refName, String newProjectVersion) {
        Path temp = Files.createTempDirectory('ref-project-version')
        try {
            cloneRepo(temp, refName) { Git git ->
                temp.resolve('gradle.properties').toFile().text = "projectVersion=${newProjectVersion}\n"

                git.add().addFilepattern('gradle.properties').call()
                assert git.add().addFilepattern('.').call()
                assert git.commit()
                        .setAuthor('CI', 'ci@example.com')
                        .setCommitter('CI', 'ci@example.com')
                        .setSign(false)
                        .setMessage("set project version = $newProjectVersion").call()

                def tagRef = git.repository.findRef("refs/tags/$refName")
                if (tagRef != null) {
                    // It's a tag: delete and recreate tag at new commit
                    git.tagDelete().setTags(refName).call()
                    git.tag().setName(refName).setMessage("Updated tag $refName").call()
                    def pushResult = git.push()
                            .setRemote('origin')
                            .setRefSpecs(new RefSpec("refs/tags/$refName:refs/tags/$refName"))
                            .setPushTags()
                            .setForce(true)
                            .call()
                    assert pushResult
                } else {
                    PushCommand push = git.push()
                            .setRemote('origin')
                            .setRefSpecs(new RefSpec("refs/heads/$refName:refs/heads/$refName" as String))
                            .setPushAll()
                            .setPushTags()
                    def pushResult = push.call()
                    assert pushResult
                }
            }
        }
        finally {
            temp.toFile().deleteDir()
        }
    }

    boolean branchExists(String branchName) {
        Path temp = Files.createTempDirectory('ref-project-version')
        try {
            cloneRepo(temp, branchName)
            return true
        }
        catch(e) {
            return false
        }
        finally {
            temp.toFile().deleteDir()
        }
    }

    private void cloneRepo(Path targetDirectory, String checkoutRef, Closure c = null) {
        try(Git git = Git.cloneRepository().setURI(externalGitHostUrl).setDirectory(targetDirectory.toFile()).setCloneAllBranches(true).call()) {
            git.fetch().setTagOpt(TagOpt.FETCH_TAGS).call()

            if(checkoutRef != 'main') {
                def repo = git.getRepository()
                def ref = repo.findRef(checkoutRef)
                if (ref == null) {
                    // Try as remote branch
                    ref = repo.findRef("refs/remotes/origin/$checkoutRef")
                }
                if (ref == null) {
                    throw new IllegalArgumentException("Ref 'checkoutRef' does not exist in remote repository")
                }

                if (ref.getName().startsWith('refs/tags/')) {
                    // Checkout tag in detached HEAD
                    git.checkout().setName(checkoutRef).call()
                } else {
                    // Checkout branch, create if needed
                    git.checkout().setCreateBranch(true).setName(checkoutRef).setStartPoint("origin/${checkoutRef}").call()
                }
            }
            // else main already checked out

            if (c) {
                c.call(git)
            }
        }
    }

    String getRefProjectVersion(String refName) {
        Path temp = Files.createTempDirectory('ref-project-version')
        try {
            String matched = null
            cloneRepo(temp, refName) { Git git ->
                Path gradleProperties = temp.resolve('gradle.properties')
                if (!Files.exists(gradleProperties)) {
                    throw new IllegalStateException(
                            "Could not resolve 'gradle.properties' at $refName. Ref or file may not exist (or ref wasn’t fetched).");
                }

                String contents = gradleProperties.toFile().text
                def matcher = (contents =~ /projectVersion=(.+)/)
                if (matcher) {
                    matched = matcher[0][1] as String
                } else {
                    throw new IllegalStateException("Could not find projectVersion in 'gradle.properties' for ref $refName" as String)
                }
            }
            return matched
        }
        finally {
            temp.toFile().deleteDir()
        }
    }

    void close() {
        gitHost?.stop()
    }
}
