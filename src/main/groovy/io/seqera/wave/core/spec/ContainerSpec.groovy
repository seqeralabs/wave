package io.seqera.wave.core.spec

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.model.ContentType

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ContainerSpec {

    String registry
    String imageName
    String reference
    String digest
    ConfigSpec config
    ManifestSpec manifest
    List<String> layerUrls

    boolean isV1() { manifest.schemaVersion==1 }

    boolean isV2() { manifest.schemaVersion==2 }

    boolean isOci() { manifest.mediaType == ContentType.OCI_IMAGE_MANIFEST_V1 }
}
