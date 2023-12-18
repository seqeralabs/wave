# {{jreleaserCreationStamp}}
class {{brewFormulaName}} < Formula
    desc "{{projectDescription}}"
    homepage "{{projectLinkHomepage}}"
    version "{{projectVersion}}"
    license "{{projectLicense}}"

    if OS.mac? && Hardware::CPU.arm?
        url "https://github.com/seqeralabs/wave-cli/releases/download/v{{projectEffectiveVersion}}/wave-{{projectEffectiveVersion}}-macos-arm64.zip"
        sha256 "{{distributionChecksumSha256}}"

        def install
            bin.install "wave" => "wave"
        end
    end

    if OS.mac? && Hardware::CPU.intel?
        url "https://github.com/seqeralabs/wave-cli/releases/download/v{{projectEffectiveVersion}}/wave-{{projectEffectiveVersion}}-macos-x86_64.zip"
        sha256 "{{distributionChecksumSha256}}"

        def install
            bin.install "wave" => "wave"
        end
    end

    test do
        output = shell_output("#{bin}/wave --version")
        assert_match "{{projectVersion}}", output
    end

    def caveats
        <<~EOS
            wave cli has been installed!
            To run it, type:
            wave --help
        EOS
    end
end
