package wellatleastitried.mediaguardclient.service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import wellatleastitried.mediaguardclient.Configuration;

@Service
public class ServerDiscoveryService {

    private final Configuration configuration;

    public ServerDiscoveryService(Configuration configuration) {
        this.configuration = configuration;
    }

    public List<String> discover() {
        List<String> prefixes = localPrefixes();
        if (prefixes.isEmpty()) {
            return List.of();
        }

        Set<String> candidates = new java.util.LinkedHashSet<>();
        for (String prefix : prefixes) {
            for (int host = 1; host <= 254; host++) {
                candidates.add("http://" + prefix + "." + host + ":" + configuration.getServerPort());
            }
        }

        RestClient client = RestClient.builder().build();
        List<String> discovered = new ArrayList<>();
        for (String candidate : candidates) {
            try {
                HttpStatusCode status = client.get()
                    .uri(candidate + "/api/v1/health")
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode();
                if (status.is2xxSuccessful()) {
                    discovered.add(candidate);
                }
            } catch (RestClientException ignored) {
            }
        }

        return discovered;
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
}
