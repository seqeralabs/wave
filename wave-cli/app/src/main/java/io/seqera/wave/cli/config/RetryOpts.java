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

package io.seqera.wave.cli.config;

import java.time.Duration;

/**
 * HTTP retry options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class RetryOpts {

    public Duration delay = Duration.ofMillis(150);

    public Integer maxAttempts = 5;

    public Duration maxDelay = Duration.ofSeconds(90);

    public double jitter = 0.25;
}
