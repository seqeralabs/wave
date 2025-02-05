import "./models/models.tsp";

using TypeSpec.Http;
using TypeSpec.Rest;
using TypeSpec.Versioning;

@versioned(wave.Versions)
@service({
  title: "Wave service"
})
namespace wave {
    enum Versions {
      v1: "0.0.0",
    }
  @route("/v1alpha2/container")
  interface  ContainerService {

    @post op createContainer(@body requestBody: ContainerRequest): {
    @body response: ContainerResponse;
    @statusCode statusCode: 200;
    };

    @route("/container/{requestId}")
    @get op getContainerDetails(@path requestId: string): {
      @body response: WaveContainerRecord;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
      };

    @route("/{requestId}/status")
    @get op getContainerStatus(@path requestId: string): {
      @body response: ContainerStatusResponse;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
      };
  }

  @route("/v1alpha1/builds/{buildId}")
  interface BuildService {

    @get op getBuildRecord(@path buildId: string): {
      @body response: WaveBuildRecord;
      @statusCode statusCode: 200;
    }|{ 
      @statusCode statusCode: 404;
     };

    @route("/status")
    @get op getBuildStatus(@path buildId: string): {
      @body response: BuildStatusResponse;
      @statusCode statusCode: 200;
    }|{ 
      @statusCode statusCode: 404;
     };
    
    @route("/logs")
    @get op getBuildLogs(@path buildId: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{ 
      @statusCode statusCode: 404;
     };

  }

  @route("/v1alpha1/scans/{scanId}")
  interface scanService{

    @get op scanImage(@path scanId: string) : { 
      @body response: WaveScanRecord;
      @statusCode statusCode: 200;
     }|{ 
      @statusCode statusCode: 404;
     };

  }

  @route("/v1alpha1/inspect")
  interface InspectService {

    @post op inspectContainer(@body requestBody: ContainerInspectRequest): {
      @body response: ContainerInspectResponse;
      @statusCode statusCode: 200;
     }|{ 
      @statusCode statusCode: 404;
     };

  }

  @route("/v1alpha2/metrics")
  interface MetricsService {

    @route("/builds")
    @get op getBuildMetrics(@query date?: string, @query org?: string): {
      @body response: MetricsResponse;
      @statusCode statusCode: 200;
    };

    @route("/pulls")
    @get op getPullMetrics(@query date?: string, @query org?: string):  {
      @body response: MetricsResponse;
      @statusCode statusCode: 200;
    };

    @route("/fusion/pulls")
    @get op getFusionPullMetrics(@query date?: string, @query org?: string):  {
      @body response: MetricsResponse;
      @statusCode statusCode: 200;
    };
  }

  @route("/v1alpha2/validate-creds")
  @post op validateCredsV2(@body request: ValidateRegistryCredsRequest):  boolean;

  @route("/v1alpha1/mirrors")
  interface getMirrorRecord {

    @route("/{mirrorId}")
    @get op containerMirror(@path mirrorId: string): {
      @body response: ContainerMirrorResponse;
      @statusCode statusCode: 200;
     }|{
      @statusCode statusCode: 404;
    };
  }

}
