package tech.tetengo.api.shared.infrastructure.almacenamiento.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;

/** Signs the SAS token of one blob: with the account key, or with a user delegation key. */
interface FirmanteSas {

    /** The SAS query string, without {@code ?}. */
    String firmar(BlobClient blob, BlobServiceSasSignatureValues valores);

    /** Service SAS signed with the account key the client was built with (Azurite, tests). */
    static FirmanteSas conClaveDeCuenta() {
        return BlobClient::generateSas;
    }
}
