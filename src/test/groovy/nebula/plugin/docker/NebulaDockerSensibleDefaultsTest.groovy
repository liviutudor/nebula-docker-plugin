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

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit test for {@link NebulaDockerSensibleDefaults}.
 *
 * @author ltudor
 */
class NebulaDockerSensibleDefaultsTest extends Specification {
    def "assignDefaults sets defaults if not set"() {
        def appName = "myapp"
        def group = "mygroup"
        def x = new Object() as NebulaDockerSensibleDefaults
        def ext = new NebulaDockerExtension()
        def props = [applicationName: appName, group: group, nebulaDocker: ext]
        def project = ProjectBuilder.builder().withName(appName).build()
        project.group = group
        project.ext["applicationName"] = appName
        project.extensions.add "nebulaDocker", ext
        project.version = '1.2.3'

        when:
        x.assignDefaults project, ext

        then:
        ext.maintainerEmail == null //we don't change this
        ext.dockerUrl == x.DOCKER_URL_LOCALHOST
        ext.environments == ['test', 'prod'] as Set
        ext.dockerRepo == [test: x.DOCKER_REG_TEST + "/$group/$appName", prod: x.DOCKER_REG_PROD + "/$group/$appName"]
        ext.dockerBase == x.DOCKER_BASE_OPEN_JRE
        ext.dockerFile == x.DEF_DOCKER_FILE
        ext.appDirLatest == "/${appName}-latest"
        ext.appDir == "/${appName}-1.2.3"
        !ext.dockerRepoAuth
        ext.dockerRepoUsername == null
        ext.dockerRepoPassword == null
        ext.dockerRepoEmail == null
    }
}
