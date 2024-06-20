@doc("request payload of validate credentials request")
model ValidateRegistryCredsRequest {
    userName: string;
    password: string;
    registry: string;
  }
  