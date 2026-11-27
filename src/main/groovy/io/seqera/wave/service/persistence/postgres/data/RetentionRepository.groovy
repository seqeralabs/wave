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

package io.seqera.wave.service.persistence.postgres.data

import java.time.Instant

import io.micronaut.data.model.Pageable
import io.micronaut.data.repository.GenericRepository
/**
 * The age-based queries data retention needs, shared by every record table.
 *
 * All four tables have the same shape, so both methods are derived from their names and the
 * table comes from the entity type - a literal {@code @Query} could not be shared this way.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface RetentionRepository<E> extends GenericRepository<E, String> {

    // oldest first, so a bounded pass always makes progress from the far end
    List<String> findIdByCreatedAtLessThanOrderByCreatedAt(Instant createdAt, Pageable pageable)

    long deleteByIdIn(Collection<String> ids)

}
