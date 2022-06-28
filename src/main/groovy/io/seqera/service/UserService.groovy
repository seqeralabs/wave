package io.seqera.service

import io.seqera.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface UserService {

    User getUserByAccessToken(String accessToken)

}
