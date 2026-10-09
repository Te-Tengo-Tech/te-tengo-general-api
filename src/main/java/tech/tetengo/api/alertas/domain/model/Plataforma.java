package tech.tetengo.api.alertas.domain.model;

/**
 * Platform of a push device (API contract §7): the native app on Android or iOS, or the web build
 * (PWA) with an FCM web push token.
 */
public enum Plataforma {
    ANDROID,
    IOS,
    WEB
}
