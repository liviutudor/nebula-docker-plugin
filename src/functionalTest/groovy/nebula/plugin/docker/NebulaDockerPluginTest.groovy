/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package nebula.plugin.docker

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import nebula.test.ProjectSpec
import org.gradle.api.Task
import org.gradle.api.tasks.Sync

/**
 * Unit test for {@link NebulaDockerPlugin}
 *
 * @author ltudor
 */
class NebulaDockerPluginTest extends ProjectSpec {
    def "createTasks create 2 sets of tasks one for current version one for latest"() {
        def repoUrl = 'titan-registry.main.us-east-1.dynprod.netflix.net:7001'
        def x = new NebulaDockerPlugin()
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.tasks.create 'buildImage'
        project.nebulaDocker.dockerRepo = [test: repoUrl]

        when:
        x.createTasks project, "test"
        def tagTest = project.tasks['dockerTagImageTest']
        def pushTest = project.tasks['pushImageTest']
        def tagTestLatest = project.tasks['dockerTagImageTestLatest']
        def pushTestLatest = project.tasks['pushImageTestLatest']

        then:
        tagTest
        tagTest.dependsOn.find({ it.hasProperty('name') && it.name == 'buildImage' })
        tagTest.repository == repoUrl

        pushTest
        pushTest.dependsOn.find({ it.hasProperty('name') && it.name == 'dockerTagImageTest' })

        tagTestLatest
        tagTestLatest.dependsOn.find({ it.hasProperty('name') && it.name == 'pushImageTest' })
        tagTestLatest.repository == repoUrl

        pushTestLatest
        pushTestLatest.dependsOn.find({ it.hasProperty('name') && it.name == 'dockerTagImageTestLatest' })
    }

    def "createTasks allows for dockerRepo to be set to a closure and invokes the closure in that case"() {
        def repoUrl = 'titan-registry.main.us-east-1.dynprod.netflix.net:7001'
        def calls = 0
        def fct = {
            calls++
            repoUrl
        }
        def x = new NebulaDockerPlugin()
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.tasks.create 'buildImage'
        project.nebulaDocker.dockerRepo = [test: fct]

        when:
        x.createTasks project, "test"
        def tagTest = project.tasks['dockerTagImageTest']
        def pushTest = project.tasks['pushImageTest']
        def tagTestLatest = project.tasks['dockerTagImageTestLatest']
        def pushTestLatest = project.tasks['pushImageTestLatest']

        then:
        calls == 2 //one for current version and one for "latest"
        tagTest
        tagTest.dependsOn.find({ it.hasProperty('name') && it.name == 'buildImage' })
        tagTest.repository == repoUrl

        pushTest
        pushTest.dependsOn.find({ it.hasProperty('name') && it.name == 'dockerTagImageTest' })

        tagTestLatest
        tagTestLatest.dependsOn.find({ it.hasProperty('name') && it.name == 'pushImageTest' })
        tagTestLatest.repository == repoUrl

        pushTestLatest
        pushTestLatest.dependsOn.find({ it.hasProperty('name') && it.name == 'dockerTagImageTestLatest' })
    }

    def "createTasks uses the tagVersion closure if set"() {
        def calls = 0
        def repoUrl = 'titan-registry.main.us-east-1.dynprod.netflix.net:7001'
        def x = new NebulaDockerPlugin()
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.tasks.create 'buildImage'
        project.nebulaDocker.dockerRepo = [test: repoUrl]
        project.nebulaDocker.tagVersion = { project ->
            calls++
        }

        when:
        x.createTasks project, "Test"

        then:
        calls == 1
    }

    def "createAllTasks creates the resource copy task, dockerfile task, buildImage and pushAllImages"() {
        def x = Spy(NebulaDockerPlugin)

        when:
        x.createAllTasks project

        then:
        1 * x.taskCopyDistResources(project) >> {}
        1 * x.taskCreateDockerfile(project) >> {}
        1 * x.taskBuildImage(project) >> {}
        1 * x.taskPushAllImages(project) >> {}
    }

    def "taskBuildImage creates the task and set it to depend on createDockerfile"() {
        def x = new NebulaDockerPlugin()
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }

        def mockParent = Mock(File)

        def mockFile = Stub(File) {
            getParentFile() >> mockParent
        }
        def dockerFileTask = project.tasks.create('createDockerfile', Dockerfile)
        dockerFileTask.destFile = mockFile

        when:
        x.taskBuildImage(project)
        def task = project.tasks['buildImage']

        then:
        task.dependsOn.find({ it.hasProperty('name') && it.name == 'createDockerfile' })
        task.inputDir.is(mockParent)
    }

    def "taskPushAllImages creates tasks for environments and then creates pushAllImages and makes it depend on all push... tasks"() {
        def x = Spy(NebulaDockerPlugin)
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.nebulaDocker.dockerRepo = [test: 'abc', PROD: 'xyz', 'devEnv': '123']
        project.tasks.create 'pushImageTestLatest'
        project.tasks.create 'pushImageProdLatest'
        project.tasks.create 'pushImageDevenvLatest'

        when:
        x.taskPushAllImages project
        def task = project.tasks['pushAllImages']

        then:
        1 * x.createTasks(project, 'test') >> {}
        1 * x.createTasks(project, 'PROD') >> {}
        1 * x.createTasks(project, 'devEnv') >> {}
        def dpp = task.dependsOn.find({ it instanceof List })
        dpp.size() == 3
        dpp.find({ it.name == 'pushImageTestLatest' })
        dpp.find({ it.name == 'pushImageProdLatest' })
        dpp.find({ it.name == 'pushImageDevenvLatest' })
    }

    def "dockerTagImageLatest depends on buildImage if tagWithoutPush set"() {
        def x = Spy(NebulaDockerPlugin)
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.nebulaDocker.dockerRepo = [test: 'abc', PROD: 'xyz', 'devEnv': '123']
        project.nebulaDocker.tagWithoutPush = true
        project.tasks.create 'buildImage'

        when:
        x.createTasks project, "test"
        def task = project.tasks['dockerTagImageTestLatest']

        then:
        def dpp = task.dependsOn.find({ it instanceof Task && it.name == "buildImage" })
        dpp != null
    }

    def "dockerTagImageLatest depends on pushImageLatest if tagWithoutPush is notset"() {
        def x = Spy(NebulaDockerPlugin)
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.nebulaDocker.dockerRepo = [test: 'abc', PROD: 'xyz', 'devEnv': '123']
        project.nebulaDocker.tagWithoutPush = false
        project.tasks.create 'buildImage'

        when:
        x.createTasks project, "test"
        def task = project.tasks['dockerTagImageTestLatest']

        then:
        def dpp = task.dependsOn.find({ it instanceof Task && it.name == "pushImageTest" })
        dpp != null
    }

    def "taskCreateDockerfile uses the property from NebulaDockerExtension and sets entry point and sets up right dependencies"() {
        def x = new NebulaDockerPlugin()
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'application'
            apply plugin: 'distribution'
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.applicationName = 'xyz'

        project.nebulaDocker.dockerFile = 'some docker file'
        project.nebulaDocker.dockerBase = 'base docker'
        project.nebulaDocker.maintainerEmail = 'some email'
        project.nebulaDocker.appDir = 'app directory'
        project.nebulaDocker.appDirLatest = 'latest directory'

        project.tasks.create('nebulaDockerCopyDistResources', Sync)

        when:
        def task = x.taskCreateDockerfile(project)

        then:
        task.dependsOn.find { (it instanceof Task) && (it.name == 'nebulaDockerCopyDistResources') }
        task.destFile.absolutePath.contains(project.nebulaDocker.dockerFile)
        task.instructions.find { (it instanceof Dockerfile.FromInstruction) && (it.build() == 'FROM base docker') }
        task.instructions.find {
            (it instanceof Dockerfile.LabelInstruction) && (it.build() == 'LABEL maintainer="some email"')
        }
        task.instructions.find { (it instanceof Dockerfile.FileInstruction) && (it.build() == "ADD xyz.tar /") }
        task.instructions.find {
            (it instanceof Dockerfile.RunCommandInstruction) && (it.build() == "RUN ln -s 'app directory' 'latest directory'")
        }
        task.instructions.find {
            (it instanceof Dockerfile.EntryPointInstruction) && (it.build() == "ENTRYPOINT [\"app directory/bin/xyz\"]")
        }
    }

    def "taskCreateDockerfile also invokes the dockerImage closure if set"() {
        def x = new NebulaDockerPlugin()
        project.extensions.create("nebulaDocker", NebulaDockerExtension)
        project.configure(project) {
            apply plugin: 'application'
            apply plugin: 'distribution'
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.applicationName = 'xyz'

        project.nebulaDocker.dockerFile = 'some docker file'
        project.nebulaDocker.dockerBase = 'base docker'
        project.nebulaDocker.maintainerEmail = 'some email'
        project.nebulaDocker.appDir = 'app directory'
        project.nebulaDocker.appDirLatest = 'latest directory'
        def called = 0
        project.nebulaDocker.dockerImage = { task ->
            called++
        }
        project.tasks.create('nebulaDockerCopyDistResources', Sync)

        when:
        def task = x.taskCreateDockerfile(project)

        then:
        task.dependsOn.find { (it instanceof Task) && (it.name == 'nebulaDockerCopyDistResources') }
        task.destFile.absolutePath.contains(project.nebulaDocker.dockerFile)
        task.instructions.find { (it instanceof Dockerfile.FromInstruction) && (it.build() == 'FROM base docker') }
        task.instructions.find {
            (it instanceof Dockerfile.LabelInstruction) && (it.build() == 'LABEL maintainer="some email"')
        }
        task.instructions.find { (it instanceof Dockerfile.FileInstruction) && (it.build() == "ADD xyz.tar /") }
        task.instructions.find {
            (it instanceof Dockerfile.RunCommandInstruction) && (it.build() == "RUN ln -s 'app directory' 'latest directory'")
        }
        task.instructions.find {
            (it instanceof Dockerfile.EntryPointInstruction) && (it.build() == "ENTRYPOINT [\"app directory/bin/xyz\"]")
        }
        called == 1
    }

    def "applying the plugin creates nebulaDocker configuration and assigns it to the docker.ext.classpath"() {
        def x = new NebulaDockerPlugin()

        when:
        x.apply(project)

        then:
        project.configurations['nebulaDocker']
        project.configurations['nebulaDocker'].visible
        project.configurations['nebulaDocker'].transitive
        project.extensions['docker']
        project.extensions['docker'].classpath
        //TODO: how to check the list of classpath?
    }

    def "enabling repoAuth and setting the username password and email on docker.registryCredentials"() {
        def x = new NebulaDockerPlugin()
        x.apply(project)
        project.configure(project) {
            apply plugin: 'application'
            apply plugin: 'distribution'
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.applicationName = 'xyz'
        project.nebulaDocker.dockerFile = 'some docker file'
        project.nebulaDocker.dockerBase = 'base docker'
        project.nebulaDocker.maintainerEmail = 'some email'
        project.nebulaDocker.appDir = 'app directory'
        project.nebulaDocker.appDirLatest = 'latest directory'

        project.nebulaDocker.dockerRepoAuth = true
        project.nebulaDocker.dockerRepoUsername = "username"
        project.nebulaDocker.dockerRepoPassword = "password"
        project.nebulaDocker.dockerRepoEmail = "joe@bloggs.com"

        when: "triggering a project.evaluate"
        project.getTasksByName("tasks", false)

        then: "the configurations should be passed through to docker-java-application"
        project.extensions['docker']
        project.extensions['docker'].registryCredentials
        project.extensions['docker'].registryCredentials.username == "username"
        project.extensions['docker'].registryCredentials.password == "password"
        project.extensions['docker'].registryCredentials.email == "joe@bloggs.com"

    }

    def "when repoAuth is not explicitly enabled then docker.registryCredentials is not populated"() {
        def x = new NebulaDockerPlugin()
        x.apply(project)
        project.configure(project) {
            apply plugin: 'application'
            apply plugin: 'distribution'
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.applicationName = 'xyz'
        project.nebulaDocker.dockerFile = 'some docker file'
        project.nebulaDocker.dockerBase = 'base docker'
        project.nebulaDocker.maintainerEmail = 'some email'
        project.nebulaDocker.appDir = 'app directory'
        project.nebulaDocker.appDirLatest = 'latest directory'

        when: "triggering a project.evaluate"
        project.getTasksByName("tasks", false)

        then: "dockerRepoAuth should be false, and registryCredentials should be null"
        !project.nebulaDocker.dockerRepoAuth
        project.extensions['docker']
        !project.extensions['docker'].registryCredentials
    }
}
