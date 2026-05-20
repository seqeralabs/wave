@doc("Details about the root filesystem of a container.")
@example(#{
    diff_ids: #[
        "sha256:6c084f2e43f86a78",
        "sha256:6c084f2e43f86a78"
    ],
    type: "layers"
})
model RootFS {
  diff_ids: string[];
  type: string;
}
