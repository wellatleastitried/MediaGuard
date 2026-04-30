package wellatleastitried.mediaguardclient.service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import wellatleastitried.mediaguardclient.Configuration;

@Service
public class ServerDiscoveryService {

    private static final int MIN_NON_STANDARD_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final int PORT_SCAN_THREADS = 128;

    private final Configuration configuration;
    private final RestClient restClient;

    public ServerDiscoveryService(Configuration configuration) {
        this.configuration = configuration;
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(150))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(350));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<String> discover() {
        List<String> prefixes = localPrefixes();
        if (prefixes.isEmpty()) {
            return List.of();
        }

        Set<String> candidates = new java.util.LinkedHashSet<>();
        String preferredServerUrl = configuration.getPreferredServerUrl();
        if (preferredServerUrl != null && !preferredServerUrl.isBlank()) {
            candidates.add(preferredServerUrl.trim());
        }

        String preferredServerHost = configuration.getPreferredServerHost();
        if (preferredServerHost != null && !preferredServerHost.isBlank()) {
            resolveServerByIp(preferredServerHost).ifPresent(candidates::add);
        }

        for (String prefix : prefixes) {
            for (int host = 1; host <= 254; host++) {
                candidates.add("http://" + prefix + "." + host + ":" + configuration.getServerPort());
            }
        }

        List<String> discovered = new ArrayList<>();
        for (String candidate : candidates) {
            if (isHealthy(candidate)) {
                discovered.add(candidate);
            }
        }

        return discovered;
    }

    public Optional<String> resolveServerByIp(String input) {
        String host = normalizeHost(input);
        if (host == null) {
            return Optional.empty();
        }

        int defaultPort = configuration.getServerPort();
        String defaultCandidate = toUrl(host, defaultPort);
        if (isHealthy(defaultCandidate)) {
            return Optional.of(defaultCandidate);
        }

        ExecutorService executor = Executors.newFixedThreadPool(PORT_SCAN_THREADS);
        AtomicBoolean stop = new AtomicBoolean(false);
        try {
            CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
            int submitted = 0;
            for (int port = MIN_NON_STANDARD_PORT; port <= MAX_PORT; port++) {
                if (port == defaultPort) {
                    continue;
                }

                final int candidatePort = port;
                completionService.submit(() -> {
                    if (stop.get()) {
                        return null;
                    }
                    String candidate = toUrl(host, candidatePort);
                    if (isHealthy(candidate)) {
                        stop.set(true);
                        return candidate;
                    }
                    return null;
                });
                submitted++;
            }

            for (int i = 0; i < submitted; i++) {
                String candidate = completionService.take().get();
                if (candidate != null) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException ex) {
            return Optional.empty();
        } finally {
            executor.shutdownNow();
        }
    }

    private List<String> localPrefixes() {
        List<String> prefixes = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address ipv4 && !ipv4.isLoopbackAddress()) {
                        byte[] bytes = ipv4.getAddress();
                        prefixes.add((bytes[0] & 0xFF) + "." + (bytes[1] & 0xFF) + "." + (bytes[2] & 0xFF));
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return prefixes.stream().distinct().toList();
    }

    private boolean isHealthy(String candidate) {
        try {
            HttpStatusCode status = restClient.get()
                .uri(candidate + "/api/v1/health")
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
            return status.is2xxSuccessful();
        } catch (RestClientException ignored) {
            return false;
        }
    }

    private String normalizeHost(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        try {
            URI uri = trimmed.contains("://") ? URI.create(trimmed) : URI.create("http://" + trimmed);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return trimmed;
    }

    private String toUrl(String host, int port) {
        return "http://" + host + ":" + port;
    }
}
