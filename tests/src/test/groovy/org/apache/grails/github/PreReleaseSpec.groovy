package org.apache.grails.github

import org.apache.grails.github.mocks.GitHubDockerAction
import org.apache.grails.github.mocks.GitHubRelease
import org.apache.grails.github.mocks.GitHubRepoMock
import org.testcontainers.containers.Network
import spock.lang.Specification

class PreReleaseSpec extends Specification {

    def 'success - tag v7.0.0-RC1 updated with version'() {
        given:
        Network net = Network.newNetwork()

        and:
        GitHubRelease release = new GitHubRelease(version: '7.0.0-RC1', tagName: 'v7.0.0-RC1', targetBranch: 'main', targetVersion: '7.0.0-SNAPSHOT')
        GitHubDockerAction action = new GitHubDockerAction('pre-release', release)

        GitHubRepoMock gitRepo = new GitHubRepoMock(action.workspacePath, net)
        gitRepo.init()
        gitRepo.populateRepository('7.0.0-SNAPSHOT', 'v7.0.0-RC1', [])
        gitRepo.stageRepositoryForAction('v7.0.0-RC1')

        and:
        def env = action.getDefaultEnvironment()

        and:
        action.createContainer(env, net)

        when:
        action.runAction()

        then:
        action.actionExitCode == 0L
        action.actionLogs

        and: 'release version'
        action.getActionGroupLogs('Setup').contains('Release Version: 7.0.0-RC1')

        and: 'next version'
        action.getActionGroupLogs('Pushing Project Changes').contains('Pushing release version and recreating v7.0.0-RC1 tag')

        and: 'target branch'
        action.getActionGroupLogs('Updating Release for Project Changes').contains('Pre Release steps complete')

        and: 'project version updated'
        action.workspacePath.resolve('gradle.properties').toFile().text.contains("projectVersion=7.0.0-RC1")

        and:
        gitRepo.getRefProjectVersion('main') == '7.0.0-SNAPSHOT'
        gitRepo.getRefProjectVersion('v7.0.0-RC1') == '7.0.0-RC1'

        cleanup:
        System.out.println("Container logs:\n${action.actionLogs}" as String)
        gitRepo?.close()
        action.close()
    }

    def 'success - tag with custom prefix updated with version'() {
        given:
        Network net = Network.newNetwork()

        and:
        GitHubRelease release = new GitHubRelease(version: '7.0.0-RC1', tagName: 'rel-7.0.0-RC1', targetBranch: 'main', targetVersion: '7.0.0-SNAPSHOT')
        GitHubDockerAction action = new GitHubDockerAction('pre-release', release)

        GitHubRepoMock gitRepo = new GitHubRepoMock(action.workspacePath, net)
        gitRepo.init()
        gitRepo.populateRepository('7.0.0-SNAPSHOT', 'rel-7.0.0-RC1', [])
        gitRepo.stageRepositoryForAction('rel-7.0.0-RC1')

        and:
        def env = action.getDefaultEnvironment()
        env['RELEASE_TAG_PREFIX'] = 'rel-'

        and:
        action.createContainer(env, net)

        when:
        action.runAction()

        then:
        action.actionExitCode == 0L
        action.actionLogs

        and: 'release version'
        action.getActionGroupLogs('Setup').contains('Release Version: 7.0.0-RC1')

        and: 'next version'
        action.getActionGroupLogs('Pushing Project Changes').contains('Pushing release version and recreating rel-7.0.0-RC1 tag')

        and: 'target branch'
        action.getActionGroupLogs('Updating Release for Project Changes').contains('Pre Release steps complete')

        and: 'project version updated'
        action.workspacePath.resolve('gradle.properties').toFile().text.contains("projectVersion=7.0.0-RC1")

        and:
        gitRepo.getRefProjectVersion('main') == '7.0.0-SNAPSHOT'
        gitRepo.getRefProjectVersion('rel-7.0.0-RC1') == '7.0.0-RC1'

        cleanup:
        System.out.println("Container logs:\n${action.actionLogs}" as String)
        gitRepo?.close()
        action.close()
    }
}
