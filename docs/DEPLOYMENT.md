# Deployment: container image

The API ships as one container image, built by the [`Dockerfile`](../Dockerfile) and published to the GitHub Container Registry as `ghcr.io/te-tengo-tech/te-tengo-general-api`. The server setup (EC2 t4g, RDS, S3, SNS, reverse proxy with TLS) lives in `te-tengo-infra`; this document only covers the image.

## The image
- **Two stages.** A Temurin 25 JDK stage runs `./gradlew bootJar` and splits the jar into Spring Boot layers. The runtime stage is Temurin 25 **JRE** with one image layer per Spring Boot layer (dependencies, loader, snapshots, application), so a new release usually only pulls the small application layer.
- **Platforms.** `linux/arm64` (the EC2 instance is a Graviton t4g) and `linux/amd64`. The build stage runs on the build machine's platform, since the jar is platform independent, so a multi-platform build compiles once.
- **Non-root.** It runs as user `tetengo` (uid/gid `10001`) from `/app`.
- **Health.** Port `8080`. The image `HEALTHCHECK` probes `/actuator/health/liveness`; readiness is `/actuator/health/readiness`. The JRE image has neither curl nor wget, so the probe uses bash's `/dev/tcp`.
- **Memory.** `JAVA_TOOL_OPTIONS` sizes the heap at 75 % of the container memory limit and exits on `OutOfMemoryError`, so the restart policy brings it back. Override the variable to tune it.

## Running it
The configuration is the same set of environment variables listed in the [README](../README.md#configuration), plus Spring Boot's standard datasource variables. The JWT keys are files: mount them read-only and point to them.

```bash
docker build -t te-tengo-general-api .
docker run --rm -p 8080:8080 \
  -v "$PWD/.claves:/run/secrets/tetengo:ro" \
  -e TT_JWT_CLAVE_PUBLICA=file:/run/secrets/tetengo/publica.pem \
  -e TT_JWT_CLAVE_PRIVADA=file:/run/secrets/tetengo/privada.pem \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/tetengo \
  -e SPRING_DATASOURCE_USERNAME=tetengo -e SPRING_DATASOURCE_PASSWORD=<password> \
  te-tengo-general-api
curl localhost:8080/actuator/health
```

- **Key permissions.** The container user (uid 10001) must be able to read the mounted keys: `chmod 644`, or `chown 10001` with `chmod 600`.
- **Without AWS variables,** clips use the in-memory fake and e-mail and push only log. Set `TT_CLIPS_*`, `TT_CORREO_PROVEEDOR=ses`, `TT_SES_*`, `TT_PUSH_PROVEEDOR` and `TT_SNS_*` for the real services. On EC2, leave the access keys blank: the AWS SDK picks up the instance role.
- **Against a local stack** (PostgreSQL and Floci on a Docker network), point the endpoints at the Floci container: `TT_CLIPS_ENDPOINT=http://floci:4566`, `TT_CLIPS_PATH_STYLE=true`, `TT_CLIPS_ACCESS_KEY=test`, `TT_CLIPS_SECRET_KEY=test`, `TETENGO_CLIPS_S3_CREARBUCKET=true`. Pre-signed URLs then use the `floci` host name, which only containers on that network resolve.

## Publishing (workflow [`image.yml`](../.github/workflows/image.yml))
| Trigger | What happens |
|---|---|
| Pull request touching the image inputs | Builds both platforms without pushing, then smoke-tests the amd64 image: it must migrate an empty PostgreSQL 18, report healthy and not run as root |
| Manual run (*Actions → Container image → Run workflow*) | Same build; with **push** checked, pushes the tags `sha-<commit>` and `<branch>` |
| Tag `api-v<version>`, e.g. `api-v0.2.0` | Pushes `0.2.0`, `0.2`, `latest` and `sha-<commit>` |

- **Authentication.** Pushing uses the workflow's own `GITHUB_TOKEN` (`packages: write`), so no secret is needed.
- **Labels.** `docker/metadata-action` adds the OCI labels: source, revision, version and creation date.

### One-time steps after the first push
1. **Link the package to this repository.** The `org.opencontainers.image.source` label links it automatically. Check the package page under the organization's *Packages* tab.
2. **Choose its visibility.** The package starts **private**. For the EC2 instance to pull it, either:
   - make it public (*Package settings → Change visibility*), or
   - keep it private and log the server in with a token that only has `read:packages`: `docker login ghcr.io -u <user> --password-stdin`.
3. **Pin the version on the server.** Reference a version tag (`:0.2.0`) or a digest in the server's compose file, never `:latest`, so a deployment is reproducible and can be rolled back.
