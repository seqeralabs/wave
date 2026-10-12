@doc("Represents os platform of a container.")
@example(#{
  os: "linux",
  arch: "amd64",
  variant: "v1"
})
model ContainerPlatform {
  os: string;
  arch: string;
  variant?: string;
}

enum Architecture {
  ARM64,
  AARCH64,
  AMD64,
  X86_64,
  X86_64_ALT,
  ARM
}

enum OS {
  LINUX,
  WINDOWS,
  MACOS
}
