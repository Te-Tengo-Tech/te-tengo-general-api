/**
 * The activity log (US-33, EP07): who changed what and when in the system's configuration, from the app
 * or from a PC, append-only. It listens to the other modules' events (PCs linked and unlinked, cameras
 * and rooms, pauses, consents, the cloud retention of movements) and takes the changes a PC makes on
 * its own (advanced mode, local storage). Nobody depends on it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Actividad")
package tech.tetengo.api.actividad;
