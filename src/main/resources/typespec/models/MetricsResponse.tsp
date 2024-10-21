@doc("Response payload for metrics.")
model MetricsResponse {
  count: int64;
  metric: "builds" | "fusion" | "pulls";
  orgs: Orgs;
}

model Orgs {
  key: string;
  value: int64;
}