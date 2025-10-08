---
title: Wave database migration
description: Migrate your Wave installation from SurrealDB to PostgreSQL
tags: [wave, surrealdb, postgresql, migration]
---

[Wave 1.21.0](https://docs.seqera.io/changelog/wave/v1.21.0) introduces support for PostgreSQL as the primary database backend, replacing SurrealDB.

This guide outlines the steps to migrate your existing Wave installation from SurrealDB to PostgreSQL.

:::info[**Prerequisites**]
You will need the following to get started:

- [Wave CLI](./cli/index.md)
- A PostgreSQL database accessible to Wave
:::

## Database migration

1. Generate build ID, scan ID, token, and mirror ID data to verify your migration in step 3.

    1. Run a Wave build operation and capture the `buildId`:

        ```bash
        wave --conda-package bwa --wave-endpoint <WAVE_ENDPOINT>
        ```

    1. Verify the build and record the build ID:

        ```bash
        curl <WAVE_ENDPOINT>/view/builds/<BUILD_ID>
        ```

    1. Verify the scan and record the scan ID:

        ```bash
        curl <WAVE_ENDPOINT>/view/scans/<SCAN_ID>
        ```

    1. Create a container augmentation and record the token:

        ```bash
        wave -i ubuntu --config-file <CONFIG_FILE> --wave-endpoint <WAVE_ENDPOINT>
        ```

    1. Verify the container and record the token:

        ```bash
        curl <WAVE_ENDPOINT>/view/containers/<TOKEN>
        ```

    1. Create a mirror operation and note the mirror ID:

        ```bash
        wave --mirror -i ubuntu --build-repo <BUILD_REPO> --wave-endpoint <WAVE_ENDPOINT>
        ```

    1. Verify the mirror and record the mirror ID:

        ```bash
        curl <WAVE_ENDPOINT>/view/mirrors/<MIRROR_ID>
        ```

1. Migrate your database:

    1. Add the following to your `MICRONAUT_ENVIRONMENTS`:
        - `postgres`
        - `surrealdb`
        - `migrate`
        - `redis`

    1. Start the Wave application:

        ```console
        INFO  i.s.w.s.p.m.DataMigrationService - Data migration service initialized
        ```

    1. Check the logs for the migration status:

        ```console
        INFO  i.s.w.s.p.m.DataMigrationService - All wave_request records migrated.
        INFO  i.s.w.s.p.m.DataMigrationService - All wave_scan records migrated.
        INFO  i.s.w.s.p.m.DataMigrationService - All wave_build records migrated.
        INFO  i.s.w.s.p.m.DataMigrationService - All wave_mirror records migrated.
        ```

    1. When all records are migrated, remove `migrate` and `surrealdb` from your `MICRONAUT_ENVIRONMENTS`, then restart Wave.

1. Use the build ID, scan ID, token, and mirror ID you generated in step 1 to verify the migration:

    1. Verify the build data:

        ```bash
        curl <WAVE_ENDPOINT>/view/builds/<BUILD_ID>
        ```

    1. Verify the scan data:

        ```bash
        curl <WAVE_ENDPOINT>/view/scans/<SCAN_ID>
        ```

    1. Verify the container data:

        ```bash
        curl <WAVE_ENDPOINT>/view/containers/<TOKEN>
        ```

    1. Verify the mirror data:

        ```bash
        curl <WAVE_ENDPOINT>/view/mirrors/<MIRROR_ID>
        ```

## Wave configuration

Add the following properties to your Wave configuration file:

`wave.build.logs.path`
: Sets the path inside  `wave.build.logs.bucket`, where build logs will be stored.

`wave.build.locks.path`
: Sets the path inside `wave.build.logs.bucket`, where conda lock files will be stored.
