package io.github.ryanoviski.hestia;

import io.github.ryanoviski.hestia.config.ApplicationPaths;

public final class HestiaLauncher {
    private HestiaLauncher() {
    }

    public static void main(String[] args) {
        try {
            ApplicationPaths paths = ApplicationPaths.resolve();
            paths.createDirectories();
            System.setProperty("hestia.log.dir", paths.logsDirectory().toString());
        } catch (Exception exception) {
            System.err.println("Não foi possível preparar o diretório de dados do Hestia.");
        }
        HestiaApplication.launchApplication(args);
    }
}
