@doc("Details about the root filesystem of a container.")
model RootFS {
  type: string;
  diff_ids: string[];
}
