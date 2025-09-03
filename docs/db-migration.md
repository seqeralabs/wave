---
title: Wave database migration from SurrealDB to PostgreSQL
---

## Pre-requisites
- Install [wave-cli] (https://github.com/seqeralabs/wave-cli)
- Ensure you have a PostgreSQL database set up and accessible from wave.

## Migration Steps
1. Create verification data, run some builds, scans, augmentation and mirror operations in Wave and note down buildId, scanId, token and mirrorId respectively for verification.
#### commands:
- ```bash
    wave --conda-package bwa --wave-endpoint <WAVE_URL>
    ```
- ```bash
    curl <WAVE_URL>/view/builds/<buildId>
    ```
- ```bash
    curl <WAVE_URL>/view/scans/<scanId>
    ```
- ```bash
    wave -i ubuntu --config-file <path_to_config_file> --wave-endpoint <WAVE_URL>
    ```
- ```bash
    curl <WAVE_URL>/view/containers/<token>
    ```
- ```bash
    wave --mirror  -i ubuntu --build-repo <repo_where_you_w>--wave-endpoint <WAVE_URL>
    ```
- ```bash
    curl <WAVE_URL>/view/mirrors/<mirrorId>
    ```
1. Ensure you have all necessary credentials for both [SurrealDB](configuration.md#SurrealDB configuration) and [PostgreSQL](configuration.md#PostgreSQL configuration).
2. Enable the following environment by adding them in `MICRONAUT_ENVIRONMENTS`:
  - `postgres`
  - `surrealdb`
  - `migrate`
  - `redis`
3. Start Wave application and check whether the migration is successful by checking the logs.
   ```
   INFO  i.s.w.s.p.m.DataMigrationService - Data migration service initialized
   ```
4. check for the following logs:
   ```
   INFO  i.s.w.s.p.m.DataMigrationService - All wave_request records migrated.
   INFO  i.s.w.s.p.m.DataMigrationService - All wave_scan records migrated.
   INFO  i.s.w.s.p.m.DataMigrationService - All wave_build records migrated.
   INFO  i.s.w.s.p.m.DataMigrationService - All wave_mirror records migrated.
   ```
5. Remove the `migrate` and `surrealdb`environment from `MICRONAUT_ENVIRONMENTS` to stop the migration process and restart wave.

6. Verify the migration by checking the PostgreSQL database for the migrated data and also run same curl from step one.
- ```bash
  curl <WAVE_URL>/view/builds/<buildId>
    ```
- ```bash
  curl <WAVE_URL>/view/scans/<scanId>
    ```
- ```bash
  curl <WAVE_URL>/view/containers/<token>
    ```
- ```bash
  curl <WAVE_URL>/view/mirrors/<mirrorId>
    ```

