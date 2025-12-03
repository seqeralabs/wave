import type { Config } from "@docusaurus/types";
import { createSeqeraConfig, getSeqeraThemeConfig, getSeqeraPresetOptions } from "@seqera/docusaurus-preset-seqera";

export default async function createConfigAsync(): Promise<Config> {
    return createSeqeraConfig({
        plugins: [],
        presets: [
            [
                "@seqera/docusaurus-preset-seqera",
                getSeqeraPresetOptions({
                    docs: {
                        path: "wave-docs", 
                        routeBasePath: "/", 
                        sidebarPath: "./sidebars.ts",
                    },
                }),
            ],
        ],

        themeConfig: getSeqeraThemeConfig({}),
    }) satisfies Config;
}