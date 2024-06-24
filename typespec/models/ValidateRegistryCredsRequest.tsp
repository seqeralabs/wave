@doc("request payload of validate credentials request")
model ValidateRegistryCredsRequest {
    password: string;
    registry: string;
    userName: string;
  }
  