The following covers mandatory steps to upgrade an existing Wave installation to the newer version.

### Upgrade to Wave 1.21.0

Wave 1.21.0 introduces support for PostgreSQL as the primary database backend, replacing SurrealDB. This upgrade requires migrating existing data from SurrealDB to PostgreSQL.

1. Follow the steps in the [Database Migration](../db-migration.md) guide to complete the migration process.
2. Add the following properties to your Wave configuration:

- **`wave.build.logs.path`**: the path inside  `wave.build.logs.bucket`, where build logs will be stored. *Mandatory*.

- **`wave.build.locks.path`**: the path inside `wave.build.logs.bucket`, where conda lock files will be stored. *Mandatory*.


### Upgrade to Wave 1.24.0

Wave 1.24.0 adds support for SBOM generation using Syft. This requires enabling the build service and configuring Syft.

Add the following to your Wave configuration:

- **`wave.scan.reports.path`**: the path inside the S3 bucket where Wave will store SBOM reports. For example, `s3://wave-store/scan-reports`. *Mandatory*.

### Upgrade to Wave 1.25.0

Wave 1.25.0 upgrades micronaut to 4.9.2 and netty to 4.2.0. This includes a change to the default Netty ByteBuf allocator from PooledByteBufAllocator to AdaptiveRecvByteBufAllocator, which may impact memory usage patterns.

To maintain stable memory usage patterns, it is recommended to explicitly set the Netty ByteBuf allocator back to PooledByteBufAllocator. Update your Wave configuration as follows:

add the following in `WAVE_JVM_OPTS`

- **`-Dio.netty.allocator.type=pooled`**: More details can be found [here](https://github.com/seqeralabs/wave/blob/master/adr/mv-4.9-netty-memory.md). *Mandatory*.
