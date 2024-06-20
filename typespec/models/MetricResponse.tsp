@doc("Response payload for metrics.")
model MetricResponse {
  metric: "builds" | "pulls" | "fusion";
  count: int64;
  orgs: Orgs;
}

model Orgs {
  key: string;
  value: int64;
}