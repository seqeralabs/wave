@doc("request payload of validate credentials request")
@example(#{
    password: "password",
    registry: "docker.io/wave",
    userName: "username"
})
model ValidateRegistryCredsRequest {
    password: string;
    registry: string;
    userName: string;
  }
