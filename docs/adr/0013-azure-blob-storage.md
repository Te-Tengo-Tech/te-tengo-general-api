# 0013. Object storage on Azure Blob Storage, chosen by configuration

**Status:** accepted (2026-10-31).

## Context
- Clips (and their landmarks), movement files (ADR 0010) and profile photos live in object storage, reached by the household agent and the app only through short-lived signed URLs; the API never handles the bytes. Until now production used Cloudflare R2 through its S3 API, with R2 API tokens kept in the Ansible vault, while the VM, its network and everything else already run on Microsoft Azure (te-tengo-infra).
- Two clouds mean two consoles, two sets of credentials to rotate and a second processor of personal data to name in the legal texts. The team decided to keep the whole cloud side on Azure. Push stays on Firebase Cloud Messaging, which Android requires.
- The agent contract already returns the headers an upload must carry (`cabeceras`), so a store with different upload headers needs no contract change, only documentation.

## Decision
- **Provider by configuration:** `tetengo.almacenamiento.proveedor` (`TT_ALMACENAMIENTO_PROVEEDOR`) is `s3` (default: Amazon S3 or an S3-compatible store such as R2, or the in-memory fakes without a bucket) or `azure`. An unknown value stops the application. Production sets `azure`; the S3 adapters stay unchanged, so going back is a configuration change.
- **One shared account client, one adapter per port:** `shared.infrastructure.almacenamiento.azure.CuentaDeAzureBlob` (Azure SDK for Java, `azure-storage-blob`, with the JDK HTTP client instead of Netty) hands out a `ContenedorDeBlobs` per container. The adapters `AlmacenamientoDeClipsEnAzure` (`alertas`), `AlmacenamientoDeMovimientosEnAzure` (`movimientos`) and `AlmacenamientoDeFotosEnAzure` (`shared`) are thin wrappers, so the SAS rules live in one place (`shared` is an open module).
- **Containers:** `clips` (clips, their landmarks and, by default, photos), `movements` (movement files, same keys as before). Backups and the Terraform state are infrastructure (te-tengo-infra).
- **Signed URLs are SAS:** an upload URL has create and write permissions on one blob, a read URL read only, HTTPS only (HTTP only against a local emulator), with the same lifetimes as the S3 pre-signed URLs and never more than 7 days. A download asks for `Content-Disposition: attachment` with the SAS `rscd` parameter, like S3's `response-content-disposition`.
- **No storage secret in production:** without an account key the API authenticates with Microsoft Entra ID (the VM's managed identity, through the SDK's default credential chain) and signs **user delegation SAS** with a user delegation key, valid one day and renewed when a URL would outlive it. An account key (`TT_AZURE_STORAGE_CLAVE`) signs service SAS instead; it is meant for Azurite and tests.
- **Upload headers:** Azure's Put Blob needs `x-ms-blob-type: BlockBlob`, so the upload answers return it next to `Content-Type`, and clients send exactly the returned headers. The desktop agent also adds it to an Azure Blob URL when an older API does not return it.
- **Deletions** of many objects run 16 at a time on virtual threads; missing blobs are ignored, as with S3's `DeleteObjects`, and any failure is raised after all were tried, so the index rows stay for the next run. Blob batches were tried and left out: against Azurite a batch answered `202` and deleted nothing, and a deletion that silently does not happen (consent revocation, retention) is worse than a slower one.
- **Tests:** the adapters run against Azurite (Microsoft's storage emulator, Testcontainers), which verifies SAS signatures; Floci stays the local AWS emulator (ADR 0005) and the `local` profile keeps its S3.

## Consequences
- One cloud, one identity: the VM's managed identity with the Storage Blob Data Contributor role replaces the R2 tokens, so there is no storage key to leak or rotate.
- A SAS cannot pin the content type or the size the way an S3 signature pins `Content-Type` and `Content-Length`. The API already checks the uploaded objects (photos at confirmation, movement files' size at confirmation); a client could upload a larger object than asked for until the confirmation refuses it and the cleanup jobs delete it.
- Every client must send the returned headers: the agent does; the app's photo upload must too (te-tengo-mobile-flutter), and the PWA needs the blob service's CORS rule to allow `x-ms-blob-type`.
- Unlike R2, Azure bills egress: each clip or replay the app reads is billed traffic (small at the project's volume, te-tengo-infra docs/terraform.md).
- The legal texts that name Cloudflare R2 as the store (consent text version 2) must be updated to name Azure; that is copy and a legal decision (docs/BLOCKERS.md).
- The existing objects are copied once from R2 to Azure by the operator (te-tengo-infra docs/deploy.md); keys do not change, so the index in PostgreSQL stays valid.

## References
- Microsoft. (n.d.). *Create a user delegation SAS*. Azure Storage documentation. https://learn.microsoft.com/rest/api/storageservices/create-user-delegation-sas
- Microsoft. (n.d.). *Put Blob*. Azure Storage REST API. https://learn.microsoft.com/rest/api/storageservices/put-blob
- Microsoft. (n.d.). *Use the Azurite emulator for local Azure Storage development*. https://learn.microsoft.com/azure/storage/common/storage-use-azurite
