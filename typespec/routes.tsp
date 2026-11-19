import "./models/models.tsp";

using TypeSpec.Http;
using TypeSpec.Rest;
using TypeSpec.Versioning;

@versioned(wave.Versions)
@service(#{
  title: "Wave service"
})
namespace wave {
    enum Versions {
      v1: "1.30.0",
    }
  @route("/v1alpha2/container")
  interface  ContainerService {

    @doc("This endpoint allows you to submit a request to access a private container registry via Wave, or build a container image on-the-fly with a Dockerfile or Conda recipe file and returns the name of the container request made available by Wave.")
    @post op createContainer(@body requestBody: ContainerRequest): {
    @body response: ContainerResponse;
    @statusCode statusCode: 200;
    };

    @route("/{requestId}")
    @doc("This endpoint allows you to get the details of a container request made to Wave.")
    @get op getContainerDetails(@path requestId: string): {
      @body response: WaveContainerRecord;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
      };

    @route("/{requestId}/status")
    @doc("This endpoint allows you to get the status of a container request made to Wave.")
    @get op getContainerStatus(@path requestId: string): {
      @body response: ContainerStatusResponse;
      @statusCode statusCode: 200;
    }|{
      @statusCode statusCode: 404;
      };
  }


  @route("/v1")
  @doc("Reads that apply the record access check. A record is served to anyone while it is public; a private one needs a Seqera Platform token for the workspace that owns it, sent as a bearer token. Supersedes the v1alpha1 and v1alpha2 reads, which carry a Deprecation header.")
  interface ReadService {

    @route("/builds/{buildId}")
    @doc("The build record.")
    @get op getBuildRecordV1(@path buildId: string): {
      @body response: WaveBuildRecord;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/builds/{buildId}/status")
    @doc("The build status.")
    @get op getBuildStatusV1(@path buildId: string): {
      @body response: BuildStatusResponse;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/builds/{buildId}/logs")
    @doc("The build logs.")
    @get op getBuildLogsV1(@path buildId: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/builds/{buildId}/condalock")
    @doc("The Conda lock file produced by the build.")
    @get op getCondaLockV1(@path buildId: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/mirrors/{mirrorId}")
    @doc("The mirror record.")
    @get op getMirrorRecordV1(@path mirrorId: string): {
      @body response: ContainerMirrorResponse;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/mirrors/{mirrorId}/logs")
    @doc("The mirror logs.")
    @get op getMirrorLogsV1(@path mirrorId: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/scans/{scanId}")
    @doc("The scan record.")
    @get op getScanRecordV1(@path scanId: string): {
      @body response: WaveScanRecord;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/scans/{scanId}/spdx")
    @doc("The SPDX software bill of materials for the scan.")
    @get op getScanSpdxV1(@path scanId: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/scans/{scanId}/logs")
    @doc("The scan logs.")
    @get op getScanLogsV1(@path scanId: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/container/{requestId}")
    @doc("The container request record.")
    @get op getContainerRecordV1(@path requestId: string): {
      @body response: WaveContainerRecord;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

    @route("/container/{requestId}/status")
    @doc("The container request status.")
    @get op getContainerStatusV1(@path requestId: string): {
      @body response: ContainerStatusResponse;
      @statusCode statusCode: 200;
    }|{ @statusCode statusCode: 401; }|{ @statusCode statusCode: 403; }|{ @statusCode statusCode: 404; }|{ @statusCode statusCode: 503; };

  }

  @route("/v1alpha1/builds/{buildId}")
  interface BuildService {

    @doc("Provides status of build against buildId passed as path variable.")
    @get op getBuildRecord(@path buildId: string): {
      @body response: WaveBuildRecord;
      @statusCode statusCode: 200;
    }|{ 
      @statusCode statusCode: 404;
     };

    @route("/status")
    @doc("Provides status of build against buildId passed as path variable.")
    @get op getBuildStatus(@path buildId: string): {
      @body response: BuildStatusResponse;
      @statusCode statusCode: 200;
    }|{ 
      @statusCode statusCode: 404;
     };
    
    @route("/logs")
    @doc("Supply logs corresponding to the specified buildId within the API request.")
    @get op getBuildLogs(@path buildId: string): {
      @body response: string;
      @statusCode statusCode: 200;
    }|{ 
      @statusCode statusCode: 404;
     };

  }

  @route("/v1alpha1/scans")
  interface scanService{

    @route("{scanId}")
    @doc("This endpoint allows you to get the details of a container scan request made to Wave.")
    @get op scanImage(@path scanId: string) : { 
      @body response: WaveScanRecord;
      @statusCode statusCode: 200;
     }|{ 
      @statusCode statusCode: 404;
     };

    @route("{scanId}/logs")
    @doc("This endpoint allows you to get the logs of a container scan request made to Wave.")
    @get op getScanLogs(@path scanId: string): {
        @body response: string;
        @statusCode statusCode: 200;
        @header("Content-Disposition")
        contentDisposition: string
         }|{
        @statusCode statusCode: 404;
    };
    }

  @route("/v1alpha1/inspect")
  interface InspectService {
    @doc("This endpoint returns the metadata about provided container image.")
    @post op inspectContainer(@body requestBody: ContainerInspectRequest): {
      @body response: ContainerInspectResponse;
      @statusCode statusCode: 200;
     }|{ 
      @statusCode statusCode: 404;
     };

  }

  @route("/v1alpha2/validate-creds")
  @doc("This endpoint allows you to validate the credentials of a container registry.")
  @post op validateCredsV2(@body request: ValidateRegistryCredsRequest): {
    @header contentType: "application/json";
    @body valid: boolean;
  };

  @route("/v1alpha1/mirrors")
  interface getMirrorRecord {

    @route("/{mirrorId}")
    @doc("This endpoint allows you to get the details of a container mirror request made to Wave.")
    @get op containerMirror(@path mirrorId: string): {
      @body response: ContainerMirrorResponse;
      @statusCode statusCode: 200;
     }|{
      @statusCode statusCode: 404;
    };

    @route("/{mirrorId}/logs")
    @doc("This endpoint allows you to get the logs of a container mirror request made to Wave.")
    @get op getMirrorLogs(@path mirrorId: string): {
        @body response: string;
        @statusCode statusCode: 200;
        @header("Content-Disposition")
         contentDisposition: string
         }|{
        @statusCode statusCode: 404;
    }
    }
}
