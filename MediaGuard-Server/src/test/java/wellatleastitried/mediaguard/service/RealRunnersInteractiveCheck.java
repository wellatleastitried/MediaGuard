package wellatleastitried.mediaguard.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import wellatleastitried.mediaguard.services.config.ServiceConfig;
import wellatleastitried.mediaguard.services.runner.Runner;

public final class RealRunnersInteractiveCheck {

    private RealRunnersInteractiveCheck() {
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("=== Real Runner Integration Check ===");
        System.out.println("This check prompts for local directory/file paths on the server host.");
        System.out.println("Any service you skip will not be run.");

        Map<String, ServiceConfig> configs = new LinkedHashMap<>();
        configs.put("jellyfin", promptJellyfin(reader));
        configs.put("radarr", promptPathService(reader, "Radarr"));
        configs.put("sonarr", promptPathService(reader, "Sonarr"));
        configs.put("prowlarr", promptProwlarr(reader));
        configs.put("tdarr", promptTdarr(reader));
        configs.put("qbittorrent", promptQbittorrent(reader));

        RunnerFactory factory = new RunnerFactory();
        List<Runner> runners = factory.build(configs);
        if (runners.isEmpty()) {
            throw new IllegalStateException("No runners enabled. Re-run and enable at least one service.");
        }

        Path output = Files.createTempDirectory("real-runner-output-");

        System.out.println("Running " + runners.size() + " runners...");
        for (Runner runner : runners) {
            System.out.println("- Running: " + runner.getServiceName());
            runner.run(output);
        }

        long fileCount;
        try (var walk = Files.walk(output)) {
            fileCount = walk.filter(Files::isRegularFile).count();
        }

        if (fileCount <= 0) {
            throw new IllegalStateException("No files were discovered/written by enabled runners.");
        }

        System.out.println("Runner output directory: " + output);
        System.out.println("Total files discovered: " + fileCount);
    }

    private static ServiceConfig promptJellyfin(BufferedReader reader) throws Exception {
        ServiceConfig config = new ServiceConfig();
        boolean enabled = askYesNo(reader, "Enable Jellyfin runner? [y/N]: ", false);
        config.setEnabled(enabled);
        if (!enabled) {
            return config;
        }

        config.setConfigPath(ask(reader, "Jellyfin config directory", "/mnt/appdata/jellyfin/config"));
        return config;
    }

    private static ServiceConfig promptPathService(BufferedReader reader, String label) throws Exception {
        ServiceConfig config = new ServiceConfig();
        boolean enabled = askYesNo(reader, "Enable " + label + " runner? [y/N]: ", false);
        config.setEnabled(enabled);
        if (!enabled) {
            return config;
        }

        String lower = label.toLowerCase(Locale.ROOT);
        config.setPath(ask(reader, label + " base directory", "/mnt/appdata/" + lower));
        return config;
    }

    private static ServiceConfig promptProwlarr(BufferedReader reader) throws Exception {
        ServiceConfig config = new ServiceConfig();
        boolean enabled = askYesNo(reader, "Enable Prowlarr runner? [y/N]: ", false);
        config.setEnabled(enabled);
        if (!enabled) {
            return config;
        }

        config.setPath(ask(reader, "Prowlarr base directory", "/mnt/appdata/prowlarr"));
        return config;
    }

    private static ServiceConfig promptTdarr(BufferedReader reader) throws Exception {
        ServiceConfig config = new ServiceConfig();
        boolean enabled = askYesNo(reader, "Enable Tdarr runner? [y/N]: ", false);
        config.setEnabled(enabled);
        if (!enabled) {
            return config;
        }

        config.setPath(ask(reader, "Tdarr base directory", "/mnt/appdata/tdarr"));
        return config;
    }

    private static ServiceConfig promptQbittorrent(BufferedReader reader) throws Exception {
        ServiceConfig config = new ServiceConfig();
        boolean enabled = askYesNo(reader, "Enable qBittorrent runner? [y/N]: ", false);
        config.setEnabled(enabled);
        if (!enabled) {
            return config;
        }

        config.setPath(ask(reader, "qBittorrent base directory", "/mnt/appdata/qbittorrent"));
        config.setGraveyardPath(ask(reader, "qBittorrent graveyard directory", "/mnt/media/graveyard"));
        return config;
    }

    private static String ask(BufferedReader reader, String prompt, String defaultValue) throws Exception {
        System.out.print(prompt + (defaultValue == null || defaultValue.isBlank() ? ": " : " [" + defaultValue + "]: "));
        String line = reader.readLine();
        if (line == null) {
            return defaultValue == null ? "" : defaultValue;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return defaultValue == null ? "" : defaultValue;
        }
        return trimmed;
    }

    private static boolean askYesNo(BufferedReader reader, String prompt, boolean defaultValue) throws Exception {
        String fallback = defaultValue ? "y" : "n";
        String answer = ask(reader, prompt, fallback).toLowerCase(Locale.ROOT);
        return answer.equals("y") || answer.equals("yes") || answer.equals("true");
    }
}
