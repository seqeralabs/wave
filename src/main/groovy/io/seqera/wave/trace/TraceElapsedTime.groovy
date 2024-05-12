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

package io.seqera.wave.trace

import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.Target

import io.micronaut.aop.Around
import static java.lang.annotation.ElementType.METHOD
import static java.lang.annotation.ElementType.TYPE
import static java.lang.annotation.RetentionPolicy.RUNTIME
/**
 * When applied to a method or a class the elapsed time to carry out the method execution
 * is reported in the application log file
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Documented
@Retention(RUNTIME)
@Target([TYPE, METHOD])
@Around
@interface TraceElapsedTime {
    int thresholdMillis() default 0
}
