/*
 * Copyright 2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.cli.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import io.seqera.wave.api.PackagesSpec;
import io.seqera.wave.cli.json.JsonHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class GptHelper {

    private static Logger log = LoggerFactory.getLogger(GptHelper.class);

    static private OpenAiChatModel client() {
        String key = System.getenv("OPENAI_API_KEY");
        if( StringUtils.isEmpty(key) )
            throw new IllegalArgumentException("Missing OPENAI_API_KEY environment variable");
        String model = System.getenv("OPENAI_MODEL");
        if( model==null )
            model = "gpt-3.5-turbo";

       return OpenAiChatModel.builder()
                .apiKey(key)
                .modelName(model)
                .build();
    }


    static public PackagesSpec grabPackages(String prompt) {
        final Map<String,Object> items = Map.of("type","string", "description", "A Conda package specification provided as the pair name and version, separated by the equals character, for example: foo=1.2.3");
        final Map<String,Object> PACKAGES = Map.of("type", "array", "description", "A list of one more Conda package", "items", items);
        final Map<String,Object> CHANNELS = Map.of("type", "array", "description", "A list of one more Conda channels", "items", Map.of("type", "string", "description", "A Conda channel name"));
        final Map<String,Map<String,Object>> properties = Map.of("packages", PACKAGES, "channels", CHANNELS);
        final ToolParameters params = ToolParameters
                .builder()
                .properties(properties)
                .required(List.of("packages"))
                .build();
        final ToolSpecification toolSpec = ToolSpecification
                .builder()
                .name("wave_container")
                .description(prompt)
                .parameters(params)
                .build();
        final AiMessage msg = AiMessage.from(prompt);

        final OpenAiChatModel client = client();
        final Response<AiMessage> resp = client.generate(List.<ChatMessage>of(msg), toolSpec);
        if( Checkers.isEmpty(resp.content().toolExecutionRequests()) )
            throw new IllegalArgumentException("Unable to resolve container for prompt: " + prompt);
        ToolExecutionRequest tool = resp.content().toolExecutionRequests().get(0);
        String json = tool.arguments();
        log.debug("GPT response: {}", json);

        return jsonToPackageSpec(json);
    }

    static protected PackagesSpec jsonToPackageSpec(String json) {
        try {
            Map object = JsonHelper.fromJson(json, Map.class);
            List<String> packages = (List<String>) object.get("packages");
            if( Checkers.isEmpty(packages) )
                throw new IllegalArgumentException("Unable to resolve packages from json response: " + json);
            List<String> channels = (List<String>) object.get("channels");
            if( Checkers.isEmpty(channels) )
                channels = List.of("bioconda","conda-forge","defaults");
            return new PackagesSpec()
                    .withType(PackagesSpec.Type.CONDA)
                    .withEntries(packages)
                    .withChannels(channels);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse json object: " + json);
        }
    }

}
