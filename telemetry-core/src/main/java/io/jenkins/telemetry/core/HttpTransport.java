package io.jenkins.telemetry.core;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class HttpTransport {
    private final HttpClient client;
    private final Duration requestTimeout;
    private final boolean gzip;
    private final Map<String, String> headers;

    public HttpTransport(Duration connectTimeout, Duration requestTimeout,
                         boolean gzip, Map<String, String> headers) {
        this(connectTimeout, requestTimeout, gzip, headers, null, null);
    }

    private HttpTransport(Duration connectTimeout, Duration requestTimeout,
                          boolean gzip, Map<String, String> headers,
                          ProxySelector proxy, SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy != null) builder.proxy(proxy);
        if (sslContext != null) builder.sslContext(sslContext);
        this.client = builder.build();
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        this.gzip = gzip;
        this.headers = Map.copyOf(ConfigurationResolver.resolve(headers));
    }

    public static HttpTransport fromConfiguration(Map<String, String> rawConfiguration) {
        Map<String, String> configuration = rawConfiguration == null
            ? Map.of() : ConfigurationResolver.resolve(rawConfiguration);
        Duration connect = Duration.ofMillis(positiveLong(configuration,
            "connectTimeoutMillis", 10_000));
        Duration request = Duration.ofMillis(positiveLong(configuration,
            "requestTimeoutMillis", 30_000));
        boolean gzip = Boolean.parseBoolean(configuration.getOrDefault("gzip", "true"));

        ProxySelector proxy = null;
        String proxyHost = trimToNull(configuration.get("proxyHost"));
        if (proxyHost != null) {
            int proxyPort = Math.toIntExact(positiveLong(configuration, "proxyPort", 8080));
            proxy = ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));
        }

        SSLContext sslContext = sslContext(configuration);
        return new HttpTransport(connect, request, gzip,
            headerConfiguration(configuration), proxy, sslContext);
    }

    public Response post(URI endpoint, String contentType, byte[] body)
        throws IOException, InterruptedException {
        byte[] transmitted = gzip ? gzip(body) : body;
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
            .timeout(requestTimeout)
            .header("Content-Type", contentType)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(transmitted));
        if (gzip) request.header("Content-Encoding", "gzip");
        headers.forEach(request::header);
        HttpResponse<byte[]> response = client.send(request.build(),
            HttpResponse.BodyHandlers.ofByteArray());
        return new Response(response.statusCode(), response.body(),
            response.headers().map());
    }

    public static Map<String, String> headerConfiguration(Map<String, String> configuration) {
        Map<String, String> result = new LinkedHashMap<>();
        if (configuration == null) return result;
        configuration.forEach((key, value) -> {
            if (key.startsWith("header.")) {
                result.put(key.substring("header.".length()), value);
            }
        });
        String combined = configuration.get("headers");
        if (combined != null && !combined.isBlank()) {
            for (String item : combined.split(";")) {
                int equals = item.indexOf('=');
                if (equals > 0) {
                    result.put(item.substring(0, equals).trim(), item.substring(equals + 1).trim());
                }
            }
        }
        return result;
    }

    private static SSLContext sslContext(Map<String, String> configuration) {
        String trustStorePath = trimToNull(configuration.get("trustStorePath"));
        String keyStorePath = trimToNull(configuration.get("keyStorePath"));
        if (trustStorePath == null && keyStorePath == null) return null;

        char[] trustPassword = chars(configuration.get("trustStorePassword"));
        char[] keyStorePassword = chars(configuration.get("keyStorePassword"));
        char[] keyPassword = chars(configuration.getOrDefault("keyPassword",
            configuration.get("keyStorePassword")));
        try {
            TrustManager[] trustManagers = null;
            if (trustStorePath != null) {
                KeyStore trustStore = loadStore(trustStorePath,
                    configuration.getOrDefault("trustStoreType", KeyStore.getDefaultType()),
                    trustPassword);
                TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
                factory.init(trustStore);
                trustManagers = factory.getTrustManagers();
            }

            KeyManager[] keyManagers = null;
            if (keyStorePath != null) {
                KeyStore keyStore = loadStore(keyStorePath,
                    configuration.getOrDefault("keyStoreType", KeyStore.getDefaultType()),
                    keyStorePassword);
                KeyManagerFactory factory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
                factory.init(keyStore, keyPassword);
                keyManagers = factory.getKeyManagers();
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, trustManagers, null);
            return context;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Unable to initialize collector TLS configuration", e);
        } finally {
            wipe(trustPassword);
            wipe(keyStorePassword);
            wipe(keyPassword);
        }
    }

    private static KeyStore loadStore(String path, String type, char[] password)
        throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            store.load(input, password);
        }
        return store;
    }

    private static long positiveLong(Map<String, String> configuration,
                                     String key, long fallback) {
        String raw = configuration.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        long parsed;
        try {
            parsed = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a positive integer", e);
        }
        if (parsed <= 0) throw new IllegalArgumentException(key + " must be positive");
        return parsed;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static char[] chars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private static void wipe(char[] value) {
        if (value != null) Arrays.fill(value, '\0');
    }

    private static byte[] gzip(byte[] value) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(buffer)) {
            output.write(value);
        }
        return buffer.toByteArray();
    }

    public record Response(int statusCode, byte[] body,
                           Map<String, java.util.List<String>> headers) {
        public String bodyUtf8() {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }

        public String firstHeader(String name) {
            if (name == null || headers == null) return null;
            for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }
    }
}
