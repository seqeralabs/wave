@doc("Manifest layer details of a container.")
@example(#{
    digest: "sha256:6c084f2e43f86a78",
    mediaType: "application/vnd.docker.container.image.v1+json",
    size: 1234
})
model ManifestLayer {
  digest: string;
  mediaType: string;
  size: int64;
}
