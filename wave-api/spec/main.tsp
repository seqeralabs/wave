import "@typespec/http";
import "@typespec/rest";
import "@typespec/openapi3";

using TypeSpec.Http;
using TypeSpec.Rest;

/**
 * Wave Containers Provisioning Service API
 * A comprehensive container orchestration and build service
 */
@service({
  title: "Wave API",
  version: "1.0.0",
})
namespace Wave;

/**
 * Service information model
 */
model ServiceInfo {
  /**
   * Application version
   */
  version: string;

  /**
   * Build commit ID
   */
  commitId: string;
}

/**
 * Service information response
 */
model ServiceInfoResponse {
  serviceInfo: ServiceInfo;
}

/**
 * Container layer meta-info
 */
model ContainerLayer {
  /**
   * The layer location, it can be either `http:` or `https:` prefixed URI
   * or a `data:` pseudo-protocol followed by a base64 encoded tar gzipped layer payload
   */
  location: string;

  /**
   * The layer gzip sha256 checksum
   */
  gzipDigest?: string;

  /**
   * The layer gzip size in bytes
   */
  gzipSize?: int32;

  /**
   * The layer tar sha256 checksum
   */
  tarDigest?: string;

  /**
   * When true, this layer is not added in the final config fingerprint
   */
  skipHashing?: boolean;
}

/**
 * Container build context
 */
model BuildContext extends ContainerLayer {}

/**
 * Container configuration
 */
model ContainerConfig {
  /**
   * Container entrypoint
   */
  entrypoint?: string[];

  /**
   * Container command
   */
  cmd?: string[];

  /**
   * Environment variables
   */
  env?: string[];

  /**
   * Working directory
   */
  workingDir?: string;

  /**
   * Container layers
   */
  layers?: ContainerLayer[];
}

/**
 * Package specifications
 */
model PackagesSpec {
  /**
   * Package specifications type
   */
  type?: string;

  /**
   * Package entries
   */
  entries?: string[];

  /**
   * Channels for conda packages
   */
  channels?: string[];

  /**
   * Conda options
   */
  condaOpts?: Record<string>;
}

/**
 * Security scan mode
 */
enum ScanMode {
  /**
   * No scan is performed
   */
  none,

  /**
   * Scan is carried out asynchronously once the build is complete
   */
  async,

  /**
   * Scan completion is required for the container request to reach 'DONE' status
   */
  required,
}

/**
 * Security scan vulnerability level
 */
enum ScanLevel {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}

/**
 * Image naming strategy
 */
enum ImageNameStrategy {
  none,
  tagPrefix,
  imageSuffix
}

/**
 * Build compression mode
 */
enum BuildCompression {
  gzip,
  none
}

/**
 * Container status
 */
enum ContainerStatus {
  PENDING,
  BUILDING,
  DONE,
  ERROR
}

/**
 * Submit container token request
 */
model SubmitContainerTokenRequest {
  /**
   * Tower access token required to enable the service
   */
  towerAccessToken?: string;

  /**
   * Tower refresh token used to refresh the authorization
   */
  towerRefreshToken?: string;

  /**
   * Tower endpoint: the public address of the tower instance to integrate with wave
   */
  towerEndpoint?: string;

  /**
   * Tower workspace id
   */
  towerWorkspaceId?: int64;

  /**
   * Container image to be pulled
   */
  containerImage?: string;

  /**
   * Container build file i.g. Dockerfile of the container to be build
   */
  containerFile?: string;

  /**
   * List of layers to be added in the pulled image
   */
  containerConfig?: ContainerConfig;

  /**
   * Conda recipe file used to build the container
   * @deprecated
   */
  condaFile?: string;

  /**
   * The container platform to be used
   */
  containerPlatform?: string;

  /**
   * The repository where the build container should be pushed
   */
  buildRepository?: string;

  /**
   * The repository where the build container should be pushed
   */
  cacheRepository?: string;

  /**
   * Request timestamp
   */
  timestamp?: string;

  /**
   * Request unique fingerprint
   */
  fingerprint?: string;

  /**
   * Enable freeze mode that cause the container build to include all containerConfig dependencies
   */
  freeze?: boolean = false;

  /**
   * A layer holding the build context for this container request
   */
  buildContext?: BuildContext;

  /**
   * Format of the target container build. Use `sif` for Singularity. default: docker/oci
   */
  format?: string;

  /**
   * When true build requests are carried out in dry-run mode
   */
  dryRun?: boolean;

  /**
   * Id of workflow in tower
   */
  workflowId?: string;

  /**
   * One or more container should be included in upstream container request
   */
  containerIncludes?: string[];

  /**
   * Defines the packages to be included in this container request
   */
  packages?: PackagesSpec;

  /**
   * The strategy applied to name a container build by wave when using the freeze option
   */
  nameStrategy?: ImageNameStrategy;

  /**
   * Whenever use container "mirror" mode
   */
  mirror?: boolean = false;

  /**
   * The request security scan mode
   */
  scanMode?: ScanMode;

  /**
   * Define the allows security vulnerabilities in the container request
   */
  scanLevels?: ScanLevel[];

  /**
   * The compression mode to be used when building a container image
   */
  buildCompression?: BuildCompression;
}

/**
 * Submit container token response
 */
model SubmitContainerTokenResponse {
  /**
   * Unique Id for this request
   */
  requestId?: string;

  /**
   * A unique authorization token assigned to this request
   */
  containerToken?: string;

  /**
   * The fully qualified wave container name to be used
   */
  targetImage?: string;

  /**
   * The time instant when the container token is going to expire
   */
  expiration?: utcDateTime;

  /**
   * The source container image that originated this request
   */
  containerImage?: string;

  /**
   * The ID of the build associated with this request or null of the image already exists
   */
  buildId?: string;

  /**
   * Whenever it's a cached build image
   */
  cached?: boolean;

  /**
   * When the result is a freeze container
   */
  freeze?: boolean;

  /**
   * When the result is a mirror container
   */
  mirror?: boolean;

  /**
   * The id of the security scan associated with this container
   */
  scanId?: string;

  /**
   * Whenever the container has been provisioned successfully or not
   */
  succeeded?: boolean;
}

/**
 * Container status response
 */
model ContainerStatusResponse {
  /**
   * Request ID
   */
  id?: string;

  /**
   * Container status
   */
  status?: ContainerStatus;

  /**
   * Status message
   */
  statusMessage?: string;

  /**
   * Target image
   */
  targetImage?: string;

  /**
   * Expiration time
   */
  expiration?: utcDateTime;

  /**
   * Build ID
   */
  buildId?: string;

  /**
   * Build status
   */
  buildStatus?: string;

  /**
   * Scan ID
   */
  scanId?: string;

  /**
   * Scan status
   */
  scanStatus?: string;
}

/**
 * Container inspect request
 */
model ContainerInspectRequest {
  /**
   * Container image to inspect
   */
  containerImage: string;

  /**
   * Container platform
   */
  containerPlatform?: string;
}

/**
 * Container inspect response
 */
model ContainerInspectResponse {
  /**
   * Container configuration
   */
  config?: ContainerConfig;

  /**
   * Container manifest
   */
  manifest?: Record<unknown>;
}

/**
 * Build status response
 */
model BuildStatusResponse {
  /**
   * Build ID
   */
  id?: string;

  /**
   * Build status
   */
  status?: string;

  /**
   * Status message
   */
  statusMessage?: string;

  /**
   * Start time
   */
  startTime?: utcDateTime;

  /**
   * Complete time
   */
  completeTime?: utcDateTime;

  /**
   * Duration in milliseconds
   */
  duration?: int64;

  /**
   * Succeeded flag
   */
  succeeded?: boolean;
}

/**
 * Registry credentials validation request
 */
model ValidateRegistryCredsRequest {
  /**
   * Registry server
   */
  registry?: string;

  /**
   * Username
   */
  userName?: string;

  /**
   * Password
   */
  password?: string;
}

/**
 * Error response
 */
@error
model ErrorResponse {
  /**
   * Error message
   */
  message: string;

  /**
   * Error details
   */
  details?: string;
}

/**
 * Service information operations
 */
@tag("Service")
interface ServiceApi {
  /**
   * Get service information
   */
  @get
  @route("/service-info")
  getServiceInfo(): ServiceInfoResponse;
}

/**
 * Container management operations
 */
@tag("Container")
interface ContainerApi {
  /**
   * Submit container token request (legacy)
   */
  @post
  @route("/container")
  submitContainerToken(@body request: SubmitContainerTokenRequest): SubmitContainerTokenResponse | ErrorResponse;

  /**
   * Submit container token request v1
   */
  @post
  @route("/v1alpha1/containers")
  submitContainerTokenV1(@body request: SubmitContainerTokenRequest): SubmitContainerTokenResponse | ErrorResponse;

  /**
   * Submit container token request v2
   */
  @post
  @route("/v1alpha2/containers")
  submitContainerTokenV2(@body request: SubmitContainerTokenRequest): SubmitContainerTokenResponse | ErrorResponse;

  /**
   * Get container status
   */
  @get
  @route("/v1alpha1/containers/{requestId}/status")
  getContainerStatus(@path requestId: string): ContainerStatusResponse | ErrorResponse;

  /**
   * Delete container
   */
  @delete
  @route("/v1alpha1/containers/{requestId}")
  deleteContainer(@path requestId: string): void | ErrorResponse;
}

/**
 * Build management operations
 */
@tag("Build")
interface BuildApi {
  /**
   * Get build record
   */
  @get
  @route("/v1alpha1/builds/{buildId}")
  getBuild(@path buildId: string): Record<unknown> | ErrorResponse;

  /**
   * Get build status
   */
  @get
  @route("/v1alpha1/builds/{buildId}/status")
  getBuildStatus(@path buildId: string): BuildStatusResponse | ErrorResponse;

  /**
   * Get build logs
   */
  @get
  @route("/v1alpha1/builds/{buildId}/logs")
  getBuildLogs(@path buildId: string): string | ErrorResponse;

  /**
   * Get conda lock file
   */
  @get
  @route("/v1alpha1/builds/{buildId}/condalock")
  getCondaLock(@path buildId: string): string | ErrorResponse;
}

/**
 * Container inspection operations
 */
@tag("Inspect")
interface InspectApi {
  /**
   * Inspect container
   */
  @post
  @route("/v1alpha1/inspect")
  inspectContainer(@body request: ContainerInspectRequest): ContainerInspectResponse | ErrorResponse;
}

/**
 * Registry validation operations
 */
@tag("Validate")
interface ValidateApi {
  /**
   * Validate registry credentials
   */
  @post
  @route("/validate-creds")
  validateCredentials(@body request: ValidateRegistryCredsRequest): Record<unknown> | ErrorResponse;
}