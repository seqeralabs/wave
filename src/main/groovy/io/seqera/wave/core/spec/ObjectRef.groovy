package io.seqera.wave.core.spec

import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model a container object reference i.e. manifest or blob
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ObjectRef {

    String mediaType
    String digest
    Long size

    static ObjectRef of(String json) {
        return of(new JsonSlurper().parseText(json) as Map)
    }

    static List<ObjectRef> of(List<Map> values) {
        return values
            ? values.collect(it-> of(it))
            : List.<ObjectRef>of()
    }

    static ObjectRef of(Map<String,?> object) {
        return new ObjectRef(
                object.get('mediaType') as String,
                object.get('digest') as String,
                object.get('size') as Long
        )
    }
}
