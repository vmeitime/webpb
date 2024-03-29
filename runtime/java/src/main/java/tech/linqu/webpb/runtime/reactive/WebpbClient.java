/*
 * Copyright (c) 2020 linqu.tech, All Rights Reserved.
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
 */
package tech.linqu.webpb.runtime.reactive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tech.linqu.webpb.commons.ParamGroup;
import tech.linqu.webpb.commons.PathParam;
import tech.linqu.webpb.runtime.WebpbMessage;
import tech.linqu.webpb.runtime.WebpbMeta;
import tech.linqu.webpb.runtime.common.InQuery;

import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.springframework.util.StringUtils.hasLength;

/**
 * WebpbClient
 */
public class WebpbClient {

    private static class MessageContext {

        public static final MessageContext NULL_CONTEXT = new MessageContext();

        HttpMethod method;

        String context;

        String path;

        ParamGroup paramGroup;
    }

    private final static Map<Class<?>, MessageContext> contextMap = new ConcurrentHashMap<>();

    private final WebClient webClient;

    private final URL baseUrl;

    private final Consumer<Map<String, Object>> attributes;

    private final ObjectMapper formatMapper = new ObjectMapper();

    protected final ObjectMapper objectMapper = createObjectMapper();

    protected ObjectMapper createObjectMapper() {
        return new ObjectMapper()
            .configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true)
            .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
                @Override
                public boolean hasIgnoreMarker(AnnotatedMember m) {
                    return super.hasIgnoreMarker(m) || m.hasAnnotation(InQuery.class);
                }
            });
    }

    /**
     * WebpbClient
     *
     * @param webClient WebClient
     * @param baseUrl   URL
     */
    public WebpbClient(WebClient webClient, URL baseUrl) {
        this(webClient, baseUrl, map -> {
        });
    }

    /**
     * WebpbClient
     *
     * @param webClient  WebClient
     * @param baseUrl    URL
     * @param attributes attributes
     */
    public WebpbClient(WebClient webClient, URL baseUrl, Consumer<Map<String, Object>> attributes) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.attributes = attributes;
    }

    /**
     * request
     *
     * @param message      WebpbMessage
     * @param responseType Class
     * @param <T>          type
     * @return Response
     */
    public <T extends WebpbMessage> T request(WebpbMessage message, Class<T> responseType) {
        return requestAsync(message, responseType).block();
    }

    /**
     * requestAsync
     *
     * @param message      WebpbMessage
     * @param responseType Class
     * @param <T>          type
     * @return Response
     */
    public <T extends WebpbMessage> Mono<T> requestAsync(WebpbMessage message, Class<T> responseType) {
        MessageContext context = getContext(message);
        if (context == null) {
            throw new RuntimeException("Request method and path is required");
        }
        return Mono
            .just(uncheckedCall(() -> objectMapper.writeValueAsString(message)))
            .flatMap(body -> {
                String url = formatUrl(baseUrl, formatMapper, message);
                return webClient
                    .method(context.method)
                    .uri(url)
                    .bodyValue(body)
                    .attributes(attributes)
                    .retrieve()
                    .onStatus(
                        httpStatus -> httpStatus.series() != HttpStatus.Series.SUCCESSFUL,
                        this::createException
                    )
                    .bodyToMono(byte[].class)
                    .map(data -> uncheckedCall(() -> objectMapper.readValue(data, responseType)));
            });
    }

    /**
     * formatUrl
     *
     * @param baseUrl URL
     * @param message WebpbMessage
     * @return String
     */
    public String formatUrl(URL baseUrl, WebpbMessage message) {
        return formatUrl(baseUrl, this.formatMapper, message);
    }

    /**
     * formatUrl
     *
     * @param message WebpbMessage
     * @return String
     */
    public String formatUrl(WebpbMessage message) {
        return formatUrl(null, this.formatMapper, message);
    }

    protected Mono<? extends Throwable> createException(ClientResponse clientResponse) {
        return clientResponse.createException();
    }

    /**
     * formatUrl
     *
     * @param baseUrl      URL
     * @param objectMapper ObjectMapper
     * @param message      WebpbMessage
     * @return String
     */
    public static String formatUrl(URL baseUrl, ObjectMapper objectMapper, WebpbMessage message) {
        MessageContext context = getContext(message);
        if (context == null) {
            throw new RuntimeException("Request method and path is required");
        }
        if (context.paramGroup == null) {
            return context.path;
        }
        JsonNode data = objectMapper.convertValue(message, JsonNode.class);
        String path = formatPath(data, context.paramGroup, baseUrl == null ? null : baseUrl.getQuery());
        String file = (baseUrl == null ? "" : emptyOrDefault(baseUrl.getPath(), ""))
            + emptyOrDefault(context.context, "") + path;
        if (baseUrl == null) {
            return file;
        }
        URL url = uncheckedCall(() ->
            new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(), file, null)
        );
        return url.toString();
    }

    private static String emptyOrDefault(String value, String orelse) {
        return hasLength(value) ? value : orelse;
    }

    private static MessageContext getContext(WebpbMessage message) {
        MessageContext context = contextMap.computeIfAbsent(message.getClass(), k -> {
            WebpbMeta meta = message.webpbMeta();
            if (meta == null) {
                return MessageContext.NULL_CONTEXT;
            }
            if (!hasLength(meta.getMethod()) || !hasLength(meta.getPath())) {
                return MessageContext.NULL_CONTEXT;
            }
            MessageContext messageContext = new MessageContext();
            messageContext.method = HttpMethod.valueOf(meta.getMethod().toUpperCase());
            messageContext.context = meta.getContext();
            messageContext.path = meta.getPath();
            ParamGroup group = ParamGroup.of(meta.getPath());
            messageContext.paramGroup = group.getParams().isEmpty() ? null : group;
            return messageContext;
        });
        return context == MessageContext.NULL_CONTEXT ? null : context;
    }

    private static String formatPath(JsonNode data, ParamGroup paramGroup, String query) {
        StringBuilder builder = new StringBuilder();
        Iterator<PathParam> iterator = paramGroup.getParams().iterator();
        String link = "";
        while (iterator.hasNext()) {
            PathParam param = iterator.next();
            builder.append(param.getPrefix());
            if (hasLength(query)) {
                builder.append("?").append(query);
            }
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '?') {
                builder.deleteCharAt(builder.length() - 1);
            }
            if (hasLength(param.getKey())) {
                link = "?";
                do {
                    param = param == null ? iterator.next() : param;
                    String value = resolve(data, param.getAccessor());
                    if (hasLength(value) && !"null".equals(value)) {
                        builder.append(link).append(param.getKey()).append("=").append(value);
                        link = "&";
                    }
                    param = null;
                } while (iterator.hasNext());
                if (hasLength(paramGroup.getSuffix())) {
                    builder.append('&').append(paramGroup.getSuffix());
                }
                return builder.toString();
            }
            String value = resolve(data, param.getAccessor());
            if (hasLength(value) && !"null".equals(value)) {
                builder.append(value);
            }
        }
        if (hasLength(paramGroup.getSuffix())) {
            builder.append(link).append(paramGroup.getSuffix());
        }
        return builder.toString();
    }

    private static String resolve(JsonNode jsonNode, String accessor) {
        for (String part : accessor.split("\\.")) {
            jsonNode = jsonNode.get(part);
            if (jsonNode == null) {
                return null;
            }
        }
        return jsonNode.asText();
    }

    protected static <T> T uncheckedCall(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
