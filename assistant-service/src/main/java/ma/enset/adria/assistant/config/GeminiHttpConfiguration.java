package ma.enset.adria.assistant.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class GeminiHttpConfiguration {
    @Bean
    public RestClientCustomizer geminiTimeouts(ObjectMapper mapper) {
        // Spring AI 1.0 drops Gemini's extra_content field. Retain the original
        // thought signature by tool-call ID and return it unchanged to Google.
        Map<String, JsonNode> signatures = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, JsonNode> entry) {
                return size() > 256;
            }
        });
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(30000);
            builder.requestFactory(factory);
            builder.requestInterceptor((request, body, execution) -> {
                if (!"generativelanguage.googleapis.com".equals(request.getURI().getHost())) {
                    return execution.execute(request, body);
                }
                JsonNode payload = mapper.readTree(body);
                for (JsonNode message : payload.path("messages")) {
                    for (JsonNode tool : message.path("tool_calls")) {
                        JsonNode extra = signatures.get(tool.path("id").asText());
                        if (extra != null) ((ObjectNode) tool).set("extra_content", extra);
                    }
                }
                byte[] outgoing = mapper.writeValueAsBytes(payload);
                request.getHeaders().setContentLength(outgoing.length);
                ClientHttpResponse response = execution.execute(request, outgoing);
                byte[] incoming = response.getBody().readAllBytes();
                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode result = mapper.readTree(incoming);
                    for (JsonNode choice : result.path("choices")) {
                        for (JsonNode tool : choice.path("message").path("tool_calls")) {
                            if (tool.has("extra_content") && tool.hasNonNull("id")) {
                                signatures.put(tool.get("id").asText(), tool.get("extra_content"));
                            }
                        }
                    }
                }
                return new ClientHttpResponse() {
                    public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
                    public String getStatusText() throws IOException { return response.getStatusText(); }
                    public HttpHeaders getHeaders() { return response.getHeaders(); }
                    public InputStream getBody() { return new ByteArrayInputStream(incoming); }
                    public void close() { response.close(); }
                };
            });
        };
    }
}
