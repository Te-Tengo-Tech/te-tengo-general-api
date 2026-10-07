/**
 * Registro, inicio de sesión con bloqueo tras 5 intentos y recuperación de contraseña; emite los JWT con el claim hogar_id. Historias US-01 a US-03.
 *
 * <p>Pendiente de implementar: seguir el slice de referencia {@code tech.tetengo.api.camaras} y
 * {@code docs/GUIA_CASOS_DE_USO.md}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cuentas y acceso")
package tech.tetengo.api.cuentas;
