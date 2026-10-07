/**
 * Shared kernel: base entities, per-household multi-tenancy, security, errors and versioning.
 *
 * <p>It is the only module the others may depend on directly.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package tech.tetengo.api.shared;
