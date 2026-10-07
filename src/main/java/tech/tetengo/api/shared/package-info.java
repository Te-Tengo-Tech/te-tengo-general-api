/**
 * Núcleo compartido: entidades base, multi-tenancy por hogar, seguridad, errores y versionado.
 *
 * <p>Es el único módulo del que los demás pueden depender directamente.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package tech.tetengo.api.shared;
