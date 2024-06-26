package io.seqera.wave.service.builder.model

import io.seqera.wave.service.persistence.WaveBuildRecord

class BuildsResponse {

    List<WaveBuildRecord> builds

    //for testing
    BuildsResponse() {}

    BuildsResponse(List<WaveBuildRecord> builds) {
        this.builds = builds
    }

}
