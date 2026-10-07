# Cómo agregar un caso de uso

Ejemplo: **US-06 / CA-06.2**, renombrar la habitación de una cámara. Es el slice `camaras`.

1. **Lee la historia y sus criterios** en `docs/referencias/PRODUCT_BACKLOG.md`. Cada criterio en formato Dado/Cuando/Entonces se convierte en al menos una prueba.
2. **Dominio:** la regla va en el agregado (`Camara.renombrar`), que lanza `ErrorDeNegocio` con un código del `enum` del módulo (`CamaraError.NOMBRE_VACIO`).
   - Prueba: `CamaraTest`, sin Spring.
3. **Migración**, si hay tablas nuevas: `V<n>__descripcion.sql`, con `hogar_id` si los datos son del hogar.
4. **Puerto y adaptador:**
   - el puerto va en `application/port` (`CamaraRepository`);
   - el adaptador, en `infrastructure/persistence`, con un `JpaRepository` *package-private*.
5. **Caso de uso:** una clase por acción, en `application` (`RenombrarCamara`), con `@Transactional`. El Javadoc cita la historia y el criterio.
6. **REST:**
   - el controlador va en `interfaces/rest`, con `version = ApiVersioning.V1`;
   - los DTO, como `record`;
   - el mapper, estático.
7. **Prueba de integración:** extiende `AbstractIntegrationTest` y usa `JwtDePrueba.tokenDeFamiliar(hogar)`. Debe probar:
   - el camino feliz;
   - cada error como `ProblemDetail` con su `codigo`;
   - **el aislamiento entre dos hogares**.
8. **Cierre:**
   ```bash
   ./gradlew spotlessApply test
   ```
   Todo debe pasar, incluidas las pruebas de arquitectura. Después, un commit en Conventional Commits.
