package io.seqera.wave.core.spec

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
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
    ManifestSpec manifestSpec
    List<String> layerUrls
}
