package tech.tetengo.api.hogares;

import java.util.Optional;

/**
 * Public API of {@code hogares}: the older adult of the household in context, as notices name them.
 * The household comes from the context (the token, or the job's bound household), never from a
 * parameter.
 */
public interface AdultoMayorDelHogar {

    /** First name of the older adult («Rosa» for «Rosa Huamán»); empty without a household in context. */
    Optional<String> nombreDePila();
}
