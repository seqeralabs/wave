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

package io.seqera.wave.service

import java.util.concurrent.CompletableFuture

import io.seqera.wave.tower.User

/**
 * Declare a service to access a Tower user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface UserService {

    User getUserByAccessToken(String endpoint, String accessToken)

    CompletableFuture<User> getUserByAccessTokenAsync(String endpoint, String accessToken)

}
