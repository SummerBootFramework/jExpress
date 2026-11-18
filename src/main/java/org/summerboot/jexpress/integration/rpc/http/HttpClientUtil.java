/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.summerboot.jexpress.integration.rpc.http;


import org.apache.tika.utils.StringUtils;
import org.summerboot.jexpress.integration.rpc.http.config.HttpClientConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class HttpClientUtil {
    /**
     * Convert form data in key-pairs (Map) to form request body (string), also
     * need to set request header:
     * Content-Type=application/x-www-form-urlencoded
     *
     * @param data
     * @return
     */
    public static String convertFormDataToString(Map<Object, Object> data) {
        StringBuilder sb = new StringBuilder();
        data.entrySet().forEach(entry -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /**
     * set default headers; proxy auth; timeout
     *
     * @param reqBuilder
     */
    public static void configure(HttpRequest.Builder reqBuilder, HttpClientConfig httpCfg) {
        Map<String, String> httpClientDefaultRequestHeaders = httpCfg.getHttpClientDefaultRequestHeaders();
        httpClientDefaultRequestHeaders.keySet().forEach(key -> {
            String value = httpClientDefaultRequestHeaders.get(key);
            reqBuilder.setHeader(key, value);
        });
        reqBuilder.timeout(Duration.ofMillis(httpCfg.getHttpRequestTimeoutMs()));
    }

    public static String getHttpRequestBody(HttpRequest httpRequest) {
        String reqBody = null;
        Optional<HttpRequest.BodyPublisher> pub = httpRequest.bodyPublisher();
        if (pub.isPresent()) {
            reqBody = pub.map(p -> {
                var bodySubscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
                var flowSubscriber = new HttpClientStringSubscriber(bodySubscriber);
                p.subscribe(flowSubscriber);
                return bodySubscriber.getBody().toCompletableFuture().join();
            }).get();
        }
        return reqBody;
    }

    public static String resolveHostIps(URI uri) {
        if (uri == null) {
            return "unknown";
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            return "unknown";
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                return "empty";
            }
            return Arrays.stream(addresses)
                    .map(InetAddress::getHostAddress)
                    .collect(Collectors.joining(","));
        } catch (Exception ignored) {
            return "resolve_failed";
        }
    }

    public enum ResponseBodyType {
        INPUT_STREAM(true),
        LINE_STREAM(true),
        STRING(false),
        BYTE_ARRAY(false),
        FILE_PATH(false),
        REACTIVE_PUBLISHER(false), // Must be released via Subscription.cancel(), rather than through Closeable.
        EMPTY(false),
        CUSTOM_OBJECT(false);

        private final boolean requiresClose;

        ResponseBodyType(boolean requiresClose) {
            this.requiresClose = requiresClose;
        }

        /**
         * Indicates whether the response body implements AutoCloseable and must be closed
         * to prevent underlying HTTP connection leaks.
         */
        public boolean requiresClose() {
            return this.requiresClose;
        }
    }

    /**
     * 判断 HttpResponse 的实际 Body 类型
     */
    public static ResponseBodyType detectBodyType(HttpResponse<?> response) {
        if (response == null) {
            return ResponseBodyType.EMPTY;
        }
        // JDK 21
        return switch (response.body()) {
            case null -> ResponseBodyType.EMPTY;
            case InputStream is -> ResponseBodyType.INPUT_STREAM;
            case String str -> ResponseBodyType.STRING;
            case byte[] bytes -> ResponseBodyType.BYTE_ARRAY;
            case Path path -> ResponseBodyType.FILE_PATH;
            case Stream<?> stream -> ResponseBodyType.LINE_STREAM;
            case Flow.Publisher<?> publisher -> ResponseBodyType.REACTIVE_PUBLISHER;
            default -> ResponseBodyType.CUSTOM_OBJECT;
        };

        /*// JDK 22+
        return switch (response.body()) {
            case null -> ResponseBodyType.EMPTY;
            case InputStream _ -> ResponseBodyType.STREAM;   // JDK 22+ unnamed var `_`
            case String _ -> ResponseBodyType.STRING;
            default -> ResponseBodyType.UNSUPPORTED;
        };*/
    }

    /**
     * Safely closes or cancels any underlying stream/publisher held by the HttpResponse
     * to prevent HTTP connection leaks.
     */
    public static Exception closeHttpResponseStream(HttpResponse<?> httpResponse) {
        if (httpResponse == null || httpResponse.body() == null) {
            return null;
        }
        ResponseBodyType type = detectBodyType(httpResponse);
        Object body = httpResponse.body();
        try {
            if (type.requiresClose() && body instanceof AutoCloseable closeable) {
                closeable.close();
            } else if (type == ResponseBodyType.REACTIVE_PUBLISHER && body instanceof Flow.Publisher<?> publisher) {
                cancelPublisher(publisher);
            }
        } catch (Exception closeEx) {
            return closeEx;
        }
        return null;
    }

    // Limit error body to 64KB to prevent OutOfMemoryError on massive error pages
    private static final int MAX_SAFE_READ_BYTES = 2 * 1024 * 1024;

    /**
     * Core internal reader supporting both bounded (error) and unbounded (full) modes.
     */
    public static String readBody(HttpResponse<?> response, Charset charset, boolean isSuccessMode) throws IOException {
        if (response == null) {
            return "";
        }

        ResponseBodyType category = detectBodyType(response);
        if (charset == null) {
            charset = extractCharset(response.headers());
        }
        Object body = response.body();

        return switch (category) {
            case EMPTY -> "";

            case INPUT_STREAM -> {
                if (isSuccessMode) {
                    /*try (InputStream is = (InputStream) body) {
                        byte[] bytes = is.readAllBytes();
                        yield new String(bytes, charset);
                    }*/
                    yield "success input streaming response";
                } else {
                    try (InputStream is = (InputStream) body) {
                        byte[] bytes = is.readNBytes(MAX_SAFE_READ_BYTES);
                        yield new String(bytes, charset);
                    }
                }
            }

            case LINE_STREAM -> {
                if (isSuccessMode) {
                    /*try (Stream<?> stream = (Stream<?>) body) {
                        StringBuilder sb = new StringBuilder();
                        var iterator = stream.iterator();
                        while (iterator.hasNext() ) {
                            if (!sb.isEmpty()) {
                                sb.append('\n');
                            }
                            sb.append(iterator.next());
                        }
                        yield sb.toString();
                    }*/
                    yield "success line streaming response";
                } else {
                    /*try (Stream<?> stream = (Stream<?>) body) {
                        Stream<?> targetStream = isSuccessMode
                                ? stream
                                : stream.limit(maxLines);
                        yield targetStream.map(String::valueOf)
                                .collect(Collectors.joining("\n"));
                    }*/
                    try (Stream<?> stream = (Stream<?>) body) {
                        // Collect line by line; cap total length at MAX_SAFE_READ_BYTES only when !isSuccessMode
                        StringBuilder sb = new StringBuilder();
                        var iterator = stream.iterator();
                        while (iterator.hasNext() && sb.length() < MAX_SAFE_READ_BYTES) {
                            if (!sb.isEmpty()) {
                                sb.append('\n');
                            }
                            sb.append(iterator.next());
                        }
                        yield sb.toString();
                    } catch (UncheckedIOException ex) {
                        throw ex.getCause() != null ? ex.getCause() : new IOException(ex);
                    }
                }
            }

            case STRING -> (String) body;

            case BYTE_ARRAY -> new String((byte[]) body, charset);

            case FILE_PATH -> ((Path) body).toAbsolutePath().normalize().toString();

            case REACTIVE_PUBLISHER -> {
                if (isSuccessMode) {
                    yield "Flow.Publisher cannot be read synchronously as String; subscribe to response.body() directly";
                } else {
                    //cancelPublisher(publisher);
                    //yield "[Reactive Publisher Body Omitted]";
                    yield readPublisherSafely((Flow.Publisher<?>) body, charset, MAX_SAFE_READ_BYTES);
                }
            }

            case CUSTOM_OBJECT -> String.valueOf(body);
        };
    }


    private static void cancelPublisher(Flow.Publisher<?> publisher) {
        publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.cancel();
            }

            @Override
            public void onNext(Object item) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    /**
     * Extract the charset from the `Content-Type` header in `HttpHeaders`; if extraction fails or the charset is absent, default to `UTF-8`.
     */
    public static Charset extractCharset(HttpHeaders headers) {
        if (headers == null) {
            return StandardCharsets.UTF_8;
        }
        return headers.firstValue("Content-Type")
                .flatMap(HttpClientUtil::parseCharsetFromContentType)
                .orElse(StandardCharsets.UTF_8);
    }

    public static java.util.Optional<Charset> parseCharsetFromContentType(String contentType) {
        // Typical format: "text/html; charset=GBK" 或 "application/json;charset=\"utf-8\""
        String[] parts = contentType.split(";");
        for (int i = 1; i < parts.length; i++) {
            String param = parts[i].trim();
            if (param.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                String charsetName = param.substring("charset=".length()).trim();
                // Remove any potential double quotes, such as in `charset="UTF-8"`
                if (charsetName.startsWith("\"") && charsetName.endsWith("\"") && charsetName.length() > 1) {
                    charsetName = charsetName.substring(1, charsetName.length() - 1).trim();
                }
                try {
                    return java.util.Optional.of(Charset.forName(charsetName));
                } catch (IllegalCharsetNameException | UnsupportedCharsetException ignored) {
                    // If an invalid or arbitrary encoding name is provided by the server (e.g., `charset=utf8mb4`), safely ignore it and trigger a fallback.
                    return java.util.Optional.empty();
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Extracts an InputStream from either INPUT_STREAM or LINE_STREAM ({@code Stream<?>}) for Jackson deserialization.
     */
    public static InputStream extractStreamForDeserialization(HttpResponse<?> httpResponse, Charset charset) {
        Object body = httpResponse.body();
        if (body instanceof InputStream is) {
            return is;
        }
        if (body instanceof Stream<?> lineStream) {
            // Convert Stream<String> lazily into an InputStream so Jackson can stream-parse it
            String joined = lineStream.map(String::valueOf).collect(Collectors.joining("\n"));
            return new ByteArrayInputStream(joined.getBytes(charset));
        }
        return null;
    }

    // 💡 for debugging:  Maximum number of characters to retrieve for reconstruction (typically 4KB–8KB is sufficient to clearly identify a malformed JSON structure)
    private static final int SNAPSHOT_SIZE = 8 * 1024;

    /**
     * Wraps the raw InputStream to simultaneously capture up to SNAPSHOT_SIZE bytes for error diagnosis.
     */
    public static InputStream wrapWithSnapshot(InputStream rawInputStream, ByteArrayOutputStream debugSnapshot) {
        return new InputStream() {
            private int bytesCopied = 0;

            @Override
            public int read() throws IOException {
                int b = rawInputStream.read();
                if (b != -1 && bytesCopied < SNAPSHOT_SIZE) {
                    debugSnapshot.write(b);
                    bytesCopied++;
                }
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int readBytes = rawInputStream.read(b, off, len);
                if (readBytes > 0 && bytesCopied < SNAPSHOT_SIZE) {
                    int availableSpace = SNAPSHOT_SIZE - bytesCopied;
                    int bytesToCopy = Math.min(readBytes, availableSpace);
                    debugSnapshot.write(b, off, bytesToCopy);
                    bytesCopied += bytesToCopy;
                }
                return readBytes;
            }
        };
    }

    /**
     * Subscribes to a reactive Flow.Publisher<List<ByteBuffer>>, collects up to maxBytes,
     * and immediately cancels the subscription to release the underlying HTTP connection.
     */
    private static String readPublisherSafely(Flow.Publisher<?> rawPublisher, Charset charset, int maxBytes) throws IOException {
        @SuppressWarnings("unchecked")
        Flow.Publisher<List<ByteBuffer>> publisher = (Flow.Publisher<List<ByteBuffer>>) rawPublisher;
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<List<ByteBuffer>>() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
            private final AtomicBoolean completed = new AtomicBoolean(false);
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                // Request 1 chunk (List<ByteBuffer>) at a time for strict backpressure control
                s.request(1);
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                if (completed.get()) {
                    return;
                }
                if (items != null) {
                    for (ByteBuffer bb : items) {
                        int remainingInBuffer = bb.remaining();
                        if (remainingInBuffer <= 0) {
                            continue;
                        }
                        int spaceLeft = maxBytes - buffer.size();
                        if (spaceLeft <= 0) {
                            break;
                        }
                        int bytesToRead = Math.min(remainingInBuffer, spaceLeft);
                        byte[] temp = new byte[bytesToRead];
                        bb.get(temp);
                        buffer.write(temp, 0, bytesToRead);
                    }
                }

                // Once we have collected enough error bytes (64KB), cancel immediately and complete!
                if (buffer.size() >= maxBytes) {
                    finishAndCancel();
                } else {
                    subscription.request(1);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (completed.compareAndSet(false, true)) {
                    // Even if the stream errors mid-way, return whatever partial bytes we already captured
                    if (buffer.size() > 0) {
                        future.complete(buffer.toByteArray());
                    } else {
                        future.completeExceptionally(throwable);
                    }
                }
            }

            @Override
            public void onComplete() {
                if (completed.compareAndSet(false, true)) {
                    future.complete(buffer.toByteArray());
                }
            }

            private void finishAndCancel() {
                if (completed.compareAndSet(false, true)) {
                    try {
                        subscription.cancel();
                    } finally {
                        future.complete(buffer.toByteArray());
                    }
                }
            }
        });

        try {
            // Bounded wait (e.g., 5 seconds) to prevent hanging if upstream stalls on error response
            byte[] bytes = future.get(5, TimeUnit.SECONDS);
            return new String(bytes, charset);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading error body from Flow.Publisher", e);
        } catch (TimeoutException e) {
            throw new IOException("Timed out waiting for error body from Flow.Publisher", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to read error body from Flow.Publisher", e.getCause());
        }
    }
}
