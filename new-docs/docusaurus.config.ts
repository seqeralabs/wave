import type { Config } from "@docusaurus/types";
import { createSeqeraConfig, getSeqeraThemeConfig, getSeqeraPresetOptions } from "@seqeralabs/docusaurus-preset-seqera";

export default async function createConfigAsync(): Promise<Config> {
    return createSeqeraConfig({
        plugins: [],
        presets: [
            [
                "@seqeralabs/docusaurus-preset-seqera",
                await getSeqeraPresetOptions({
                    docs: {
                        path: "wave-docs",
                        routeBasePath: "/wave/",
                        sidebarPath: "./sidebar.json",
                    },
                    openapi: false,
                }),
            ],
        ],

        themeConfig: getSeqeraThemeConfig({}),
    }) satisfies Config;
}
