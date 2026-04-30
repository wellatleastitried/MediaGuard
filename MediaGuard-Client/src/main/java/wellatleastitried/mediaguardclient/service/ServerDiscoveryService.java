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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wellatleastitried.mediaguardclient.Configuration;

@Service
public class ServerDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDiscoveryService.class);

    private static final int MIN_NON_STANDARD_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final int PORT_SCAN_THREADS = 128;
    private static final int DISCOVERY_THREADS = 64;

    private final Configuration configuration;
    private final RestClient restClient;

    public ServerDiscoveryService(Configuration configuration) {
        this.configuration = configuration;
        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(2500))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(3000));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<String> discover() {
        List<String> prefixes = localPrefixes();
        Set<String> discovered = new java.util.LinkedHashSet<>();

        LOGGER.info("Starting network discovery: localPrefixes={}", prefixes);

        String preferredServerIp = configuration.getPreferredServerIp();
        if (preferredServerIp != null && !preferredServerIp.isBlank()) {
            LOGGER.info("Probing preferred server IP: {}", preferredServerIp);
            resolveServerByIp(preferredServerIp).ifPresent(url -> {
                LOGGER.info("Preferred IP resolved: {}", url);
                discovered.add(url);
            });
        }

        if (prefixes.isEmpty()) {
            LOGGER.warn("No local network prefixes detected, skipping subnet scan");
            return discovered.stream().toList();
        }

        List<Integer> portsToProbe = discoveryPorts();
        LOGGER.info("Scanning {} subnet prefix(es) with {} port(s) each, ports={}", prefixes.size(), portsToProbe.size(), portsToProbe);
        Set<String> candidates = new java.util.LinkedHashSet<>();
        for (String prefix : prefixes) {
            for (int host = 1; host <= 254; host++) {
                String ip = prefix + "." + host;
                for (Integer port : portsToProbe) {
                    candidates.add("http://" + ip + ":" + port);
                }
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(DISCOVERY_THREADS);
        LOGGER.info("Probing {} candidate endpoints with {} threads", candidates.size(), DISCOVERY_THREADS);
        try {
            CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
            int submitted = 0;
            for (String candidate : candidates) {
                completionService.submit(() -> isHealthy(candidate) ? candidate : null);
                submitted++;
            }

            for (int i = 0; i < submitted; i++) {
                try {
                    String healthy = completionService.take().get();
                    if (healthy != null) {
                        LOGGER.info("Discovered server: {}", healthy);
                        discovered.add(healthy);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ignored) {
                }
            }
        } finally {
            executor.shutdownNow();
        }

        LOGGER.info("Network discovery complete, found {} server(s): {}", discovered.size(), discovered);
        return discovered.stream().toList();
    }

    private List<Integer> discoveryPorts() {
        Set<Integer> ports = new java.util.LinkedHashSet<>();
        ports.add(configuration.getServerPort());
        ports.add(38471);
        ports.add(8080);
        ports.add(3000);
        ports.add(5000);
        ports.add(8081);
        ports.add(80);
        ports.add(443);
        return new ArrayList<>(ports);
    }

    public Optional<String> resolveServerByIp(String input) {
        return resolveServerByIp(input, null);
    }

    public Optional<String> resolveServerByIp(String input, Integer preferredPort) {
        String host = normalizeHost(input);
        if (host == null) {
            LOGGER.warn("resolveServerByIp: invalid input '{}'", input);
            return Optional.empty();
        }

        LOGGER.info("Resolving server at host={}, preferredPort={}", host, preferredPort);

        if (preferredPort != null) {
            String preferredCandidate = toUrl(host, preferredPort);
            LOGGER.debug("Probing preferred port: {}", preferredCandidate);
            if (isHealthy(preferredCandidate)) {
                LOGGER.info("Server found at preferred port: {}", preferredCandidate);
                return Optional.of(preferredCandidate);
            }
            LOGGER.debug("Preferred port {} not responding", preferredPort);
        }

        int defaultPort = configuration.getServerPort();
        String defaultCandidate = toUrl(host, defaultPort);
        LOGGER.debug("Probing default port: {}", defaultCandidate);
        if (isHealthy(defaultCandidate)) {
            LOGGER.info("Server found at default port: {}", defaultCandidate);
            return Optional.of(defaultCandidate);
        }

        LOGGER.debug("Default port {} not responding, trying common ports", defaultPort);
        for (Integer quickPort : discoveryPorts()) {
            if (quickPort == defaultPort) {
                continue;
            }
            if (preferredPort != null && quickPort.equals(preferredPort)) {
                continue;
            }
            String quickCandidate = toUrl(host, quickPort);
            LOGGER.debug("Probing common port: {}", quickCandidate);
            if (isHealthy(quickCandidate)) {
                LOGGER.info("Server found at common port: {}", quickCandidate);
                return Optional.of(quickCandidate);
            }
        }

        LOGGER.info("Common ports exhausted for {}, starting full port scan ({}-{})", host, MIN_NON_STANDARD_PORT, MAX_PORT);
        ExecutorService executor = Executors.newFixedThreadPool(PORT_SCAN_THREADS);
        AtomicBoolean stop = new AtomicBoolean(false);
        try {
            CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
            int submitted = 0;
            for (int port = MIN_NON_STANDARD_PORT; port <= MAX_PORT; port++) {
                if (port == defaultPort) {
                    continue;
                }
                if (preferredPort != null && port == preferredPort) {
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
                    LOGGER.info("Server found via full port scan: {}", candidate);
                    return Optional.of(candidate);
                }
            }
            LOGGER.warn("Full port scan exhausted for {}, no server found", host);
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Port scan interrupted for {}", host);
            return Optional.empty();
        } catch (ExecutionException ex) {
            LOGGER.warn("Port scan execution error for {}: {}", host, ex.getMessage());
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
            boolean ok = status.is2xxSuccessful();
            if (ok) {
                LOGGER.debug("Health check OK: {}", candidate);
            }
            return ok;
        } catch (RestClientException ex) {
            LOGGER.debug("Health check failed for {}: {}", candidate, ex.getMessage());
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
