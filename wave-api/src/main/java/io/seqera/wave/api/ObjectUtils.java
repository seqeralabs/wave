package io.seqera.wave.api;

import java.util.List;
import java.util.Map;

/**
 * Helper class for object checks
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ObjectUtils {
    static public boolean isEmpty(String value) {
        return value==null || value.length()==0;
    }
    static public boolean isEmpty(Integer value) {
        return value==null || value==0;
    }
    static public boolean isEmpty(Long value) {
        return value==null || value==0;
    }
    static public boolean isEmpty(List value) {
        return value==null || value.size()==0;
    }

    static public boolean isEmpty(Map value) {
        return value==null || value.size()==0;
    }
}
