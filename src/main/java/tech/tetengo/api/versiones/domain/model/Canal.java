package tech.tetengo.api.versiones.domain.model;

import java.util.Arrays;
import java.util.Optional;

/** Where an app is installed from (US-43): each channel has its own latest version. */
public enum Canal {
    /** Te Tengo Captura for Windows: the installer (CA-43.2, CA-43.7). */
    WINDOWS("windows", true),
    /** Te Tengo Captura for macOS: the disk image, only linked (CA-43.3). */
    MAC("mac", true),
    /** The Android app installed from the APK (CA-43.5). */
    APK("apk", true),
    /** The Android app from Google Play (CA-43.5). */
    PLAY("play", false),
    /** The iPhone app, TestFlight or App Store (CA-43.6). */
    IOS("ios", false),
    /** The installable web app (CA-43.4). */
    PWA("pwa", false);

    private final String clave;
    private final boolean archivo;

    Canal(String clave, boolean archivo) {
        this.clave = clave;
        this.archivo = archivo;
    }

    /** The key of the channel in the manifests and in the response ({@code windows}, {@code apk}…). */
    public String clave() {
        return clave;
    }

    /** Whether its {@code url} is a file the client downloads and checks (so it has a SHA-256). */
    public boolean esArchivo() {
        return archivo;
    }

    public static Optional<Canal> deClave(String clave) {
        return Arrays.stream(values()).filter(c -> c.clave.equals(clave)).findFirst();
    }
}
