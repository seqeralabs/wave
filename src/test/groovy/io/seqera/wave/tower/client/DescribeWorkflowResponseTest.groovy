/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.tower.client

import spock.lang.Specification

import java.time.OffsetDateTime

import io.seqera.wave.util.JacksonHelper

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DescribeWorkflowResponseTest extends Specification {

    def 'should deserialize workflow' () {
        given:
        def PAYLOAD = '{"workflow":{"id":"1ApI9mt8QUZROT","submit":"2024-12-26T11:54:01.659781+01:00","start":"2024-12-26T11:54:26.386791+01:00","complete":null,"dateCreated":"2024-12-26T11:54:01.668816+01:00","lastUpdated":"2024-12-26T11:54:26.387903+01:00","runName":"angry_curie","sessionId":"8bbfd641-1992-40c1-899b-8d90d0eb86c3","profile":"standard","workDir":"/some/work","commitId":"e16e068d7e0d23cea3f520e7f3ec9d8fc5f75dd0","userName":"paolo-ditommaso","scriptId":"c86562f1d8e81f868ab5e1b02ccf0143","revision":"master","commandLine":"nextflow run \'https://github.com/pditommaso/nf-sleep\' -name angry_curie -params-file \'http://localhost:8000/api/ephemeral/vn1YShW5g3WXlm7aWPn2TA.yaml\' -with-tower \'http://localhost:8000/api\'","projectName":"pditommaso/nf-sleep","scriptName":"main.nf","launchId":"1UXIbhO3N0yZqxGtxPPHM1","status":"RUNNING","requiresAttention":false,"configFiles":["/Users/pditommaso/.nextflow/assets/pditommaso/nf-sleep/nextflow.config","/Users/pditommaso/Projects/nf-tower-cloud/tower-backend/work/nf-1ApI9mt8QUZROT.config"],"params":{"forks":1,"exit":0,"times":1,"cmd":"echo \'Hello (timeout 200)\'","timeout":200},"configText":"process {\\n   container = \'quay.io/nextflow/bash\'\\n}\\n\\ntimeline {\\n   enabled = true\\n   file = \'timeline-1ApI9mt8QUZROT.html\'\\n}\\n\\nwave {\\n   enabled = true\\n   endpoint = \'https://reg.ngrok.io\'\\n}\\n\\ndocker {\\n   enabled = true\\n   envWhitelist = \'AWS_ACCESS_KEY_ID,AWS_SECRET_ACCESS_KEY\'\\n}\\n\\nparams {\\n   timeout = 200\\n}\\n\\nrunName = \'angry_curie\'\\nworkDir = \'/Users/pditommaso/Projects/nf-tower-cloud/tower-backend/work\'\\n\\ntower {\\n   enabled = true\\n   endpoint = \'http://localhost:8000/api\'\\n}\\n","manifest":{"nextflowVersion":null,"defaultBranch":null,"version":null,"homePage":null,"gitmodules":null,"description":null,"name":null,"mainScript":"main.nf","author":null},"nextflow":{"version":"24.10.3","build":"5933","timestamp":"2024-12-16T15:34:00Z"},"stats":null,"errorMessage":null,"errorReport":null,"deleted":null,"peakLoadCpus":null,"peakLoadTasks":null,"peakLoadMemory":null,"projectDir":"/Users/pditommaso/.nextflow/assets/pditommaso/nf-sleep","homeDir":"/Users/pditommaso","container":"quay.io/nextflow/bash","repository":"https://github.com/pditommaso/nf-sleep","containerEngine":"docker","scriptFile":"/Users/pditommaso/.nextflow/assets/pditommaso/nf-sleep/main.nf","launchDir":"/Users/pditommaso/Projects/nf-tower-cloud/tower-backend/work","duration":null,"exitStatus":null,"resume":false,"success":null,"logFile":null,"outFile":null,"operationId":null,"ownerId":1},"progress":{"workflowProgress":{"cpus":0,"cpuTime":0,"cpuLoad":0,"memoryRss":0,"memoryReq":0,"readBytes":0,"writeBytes":0,"volCtxSwitch":0,"invCtxSwitch":0,"cost":null,"loadTasks":0,"loadCpus":1,"loadMemory":0,"peakCpus":1,"peakTasks":1,"peakMemory":0,"executors":["local"],"dateCreated":"2024-12-26T11:54:35.038805+01:00","lastUpdated":"2024-12-26T11:54:35.038806+01:00","running":1,"cached":0,"failed":0,"pending":0,"submitted":0,"succeeded":0,"memoryEfficiency":0.0,"cpuEfficiency":0.0},"processesProgress":[{"process":"foo","cpus":0,"cpuTime":0,"cpuLoad":0,"memoryRss":0,"memoryReq":0,"readBytes":0,"writeBytes":0,"volCtxSwitch":0,"invCtxSwitch":0,"loadTasks":0,"loadCpus":1,"loadMemory":0,"peakCpus":1,"peakTasks":1,"peakMemory":0,"dateCreated":"2024-12-26T11:55:36.201251+01:00","lastUpdated":"2024-12-26T11:55:36.201252+01:00","running":1,"cached":0,"failed":0,"pending":0,"submitted":0,"succeeded":0,"memoryEfficiency":0.0,"cpuEfficiency":0.0}]},"platform":{"id":"local-platform","name":"Local Launch Platform for testing"}}'

        when:
        def resp = JacksonHelper.fromJson(PAYLOAD,DescribeWorkflowResponse)
        then:
        resp.workflow
        and:
        resp.workflow.id == '1ApI9mt8QUZROT'
        resp.workflow.submit == OffsetDateTime.parse('2024-12-26T11:54:01.659781+01:00')
        resp.workflow.start == OffsetDateTime.parse('2024-12-26T11:54:26.386791+01:00')
        resp.workflow.dateCreated == OffsetDateTime.parse('2024-12-26T11:54:01.668816+01:00')
        resp.workflow.lastUpdated == OffsetDateTime.parse('2024-12-26T11:54:26.387903+01:00')
        resp.workflow.runName == 'angry_curie'
        resp.workflow.sessionId == '8bbfd641-1992-40c1-899b-8d90d0eb86c3'
        resp.workflow.launchId == '1UXIbhO3N0yZqxGtxPPHM1'
        resp.workflow.workDir == '/some/work'
    }

}
