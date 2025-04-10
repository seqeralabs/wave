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

package io.seqera.wave.service.job
/**
 * Define events and properties for jobs managed via {@link JobManager}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface JobHandler<R extends JobEntry> {

    /**
     * Retrieve the {@link JobEntry} instance associated with the specified {@link JobSpec} object
     *
     * @param job
     *      The {@link JobSpec} object for which the corresponding record is needed
     * @return
     *      The associated job record or {@link null} otherwise
     */
    R getJobEntry(JobSpec job)

    /**
     * Launch a job execution to the underlying execution platform
     *
     * @param job
     *      A {@link JobSpec} object modelling the job to be executed
     * @param entry
     *      A {@link JobEntry} object modelling the state of the job to be executed
     * @return
     *      A {@link JobSpec} object modelling the submitted job or {@code null} if
     *      the job could not be executed.
     */
    JobSpec launchJob(JobSpec job, R entry)

    /**
     * Event invoked when a job complete either successfully or with a failure
     *
     * @param job
     *      The {@link JobSpec} object
     * @param entry
     *      The associate state record
     * @param state
     *      The job execution state
     */
    void onJobCompletion(JobSpec job, R entry, JobState state)

    /**
     * Event invoked when a job execution reports an exception
     *
     * @param job
     *      The {@link JobSpec} object
     * @param entry
     *      The associate state record
     * @param error
     *      The job job exception
     */
    void onJobException(JobSpec job, R entry, Throwable error)

    /**
     * Event invoked when a job exceed the expected max execution duration
     *
     * @param job
     *      The {@link JobSpec} object
     * @param jobRecord
     *      The associate state record
     */
    void onJobTimeout(JobSpec job, R entry)

}
