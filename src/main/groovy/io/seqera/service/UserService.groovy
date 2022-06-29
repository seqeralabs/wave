package io.seqera.service

import io.seqera.tower.User

/**
 * Declare a service to access a Tower user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface UserService {

    User getUserByAccessToken(String accessToken)

}
