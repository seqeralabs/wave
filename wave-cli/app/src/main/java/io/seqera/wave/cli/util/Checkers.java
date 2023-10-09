/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.cli.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class Checkers {

    private static final Pattern ENV_REGEX = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*=.*$");

    static public boolean isEmpty(String value) {
        return value==null || "".equals(value.trim());
    }

    static public boolean isEmpty(List list) {
        return list==null || list.size()==0;
    }

    static public boolean isEnvVar(String value) {
        return value!=null && ENV_REGEX.matcher(value).matches();
    }
}
