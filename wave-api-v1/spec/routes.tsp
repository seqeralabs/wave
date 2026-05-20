import "./models/models.tsp";

using TypeSpec.Http;
using TypeSpec.Rest;
using TypeSpec.Versioning;

@versioned(wave.Versions)
@service({
  title: "Wave API"
})
namespace wave {
    enum Versions {
      @doc("1.0.0") v1: "1.0.0",
    }
  @route("/w1/containers")
  interface  ContainerService {

    @doc("This endpoint allows you to submit a request to access a private container registry via Wave, or build a container image on-the-fly with a Dockerfile or Conda recipe file and returns the name of the container request made available by Wave.")
    @post op createContainer(@body requestBody: ContainerRequest): {
    @body response: ContainerResponse;
    @statusCode statusCode: 200;
    };

    @route("/{id}")
    @doc("This endpoint allows you to get the details of a container request made to Wave.")
    @get op getContainerDetails(@path id: string): {
      @body response: WaveContainerRecord;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
      };

    @route("/{id}/status")
    @doc("This endpoint allows you to get the status of a container request made to Wave.")
    @get op getContainerStatus(@path id: string): {
      @body response: ContainerStatusResponse;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
      };
  }

  @route("/w1/builds/{id}")
  interface BuildService {

    @doc("Provides the record of a build identified by the id path variable.")
    @get op getBuildRecord(@path id: string): {
      @body response: WaveBuildRecord;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
     };

    @route("/status")
    @doc("Provides the status of a build identified by the id path variable.")
    @get op getBuildStatus(@path id: string): {
      @body response: BuildStatusResponse;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
     };

    @route("/logs")
    @doc("Supply build logs identified by the id path variable.")
    @get op getBuildLogs(@path id: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
     };

  }

  @route("/w1/scans")
  interface scanService{

    @route("{id}")
    @doc("This endpoint allows you to get the details of a container scan request made to Wave.")
    @get op scanImage(@path id: string) : {
      @body response: WaveScanRecord;
      @statusCode statusCode: 200;
     }|{
      @statusCode statusCode: 404;
     };

    @route("{id}/logs")
    @doc("This endpoint allows you to get the logs of a container scan request made to Wave.")
    @get op getScanLogs(@path id: string): {
        @body response: string;
        @statusCode statusCode: 200;
        @header("Content-Disposition")
        contentDisposition: string
         }|{
        @statusCode statusCode: 404;
    };
    }

  @route("/w1/inspections")
  interface InspectService {
    @doc("This endpoint returns the metadata about provided container image.")
    @post op inspectContainer(@body requestBody: ContainerInspectRequest): {
      @body response: ContainerInspectResponse;
      @statusCode statusCode: 200;
     }|{
      @statusCode statusCode: 404;
     };

  }

  @route("/w1/credentials/validate")
  @doc("This endpoint allows you to validate the credentials of a container registry.")
  @post op validateCredsV2(@body request: ValidateRegistryCredsRequest):  boolean;

  @route("/w1/mirrors")
  interface getMirrorRecord {

    @route("/{id}")
    @doc("This endpoint allows you to get the details of a container mirror request made to Wave.")
    @get op containerMirror(@path id: string): {
      @body response: ContainerMirrorResponse;
      @statusCode statusCode: 200;
     }|{
      @statusCode statusCode: 404;
    };

    @route("/{id}/logs")
    @doc("This endpoint allows you to get the logs of a container mirror request made to Wave.")
    @get op getMirrorLogs(@path id: string): {
        @body response: string;
        @statusCode statusCode: 200;
        @header("Content-Disposition")
         contentDisposition: string
         }|{
        @statusCode statusCode: 404;
    }
    }
}
