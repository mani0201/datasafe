package de.adorsys.datasafe.privatestore.api.actions;

import de.adorsys.datasafe.encrypiton.api.types.UserIDAuth;
import de.adorsys.datasafe.types.api.actions.ListRequest;
import de.adorsys.datasafe.types.api.resource.AbsoluteLocation;
import de.adorsys.datasafe.types.api.resource.PrivateResource;
import de.adorsys.datasafe.types.api.resource.ResolvedResource;

import java.util.stream.Stream;

/**
 * Lists user files in privatespace.
 */
public interface ListPrivate {

    /**
     * List files/entries at the location (possibly absolute) within privatespace, specified by {@code request}
     * @param request Where to list entries
     * @return Stream of absolute resource locations (location is decrypted)
     */
    Stream<AbsoluteLocation<ResolvedResource>> list(ListRequest<UserIDAuth, PrivateResource> request);
}
