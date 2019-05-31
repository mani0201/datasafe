package de.adorsys.datasafe.privatestore.api.actions;

import de.adorsys.datasafe.encrypiton.api.types.UserIDAuth;
import de.adorsys.datasafe.types.api.actions.ReadRequest;
import de.adorsys.datasafe.types.api.resource.PrivateResource;

import java.io.InputStream;

/**
 * Reads users' file in privatespace.
 */
public interface ReadFromPrivate {

    /**
     * Opens resource identified by the location from {@code request} inside privatespace
     * and supplies its decrypted content.
     * @param request Where to read resource (location can be relative/absolute)
     * @return Decrypted resource content stream
     * @apiNote Returned stream should be closed properly
     */
    InputStream read(ReadRequest<UserIDAuth, PrivateResource> request);
}
