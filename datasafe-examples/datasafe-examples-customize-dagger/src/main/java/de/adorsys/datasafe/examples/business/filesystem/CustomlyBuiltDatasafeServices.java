package de.adorsys.datasafe.examples.business.filesystem;

import dagger.BindsInstance;
import dagger.Component;
import de.adorsys.datasafe.business.impl.cmsencryption.DefaultCMSEncryptionModule;
import de.adorsys.datasafe.business.impl.directory.DefaultCredentialsModule;
import de.adorsys.datasafe.business.impl.directory.DefaultProfileModule;
import de.adorsys.datasafe.business.impl.document.DefaultDocumentModule;
import de.adorsys.datasafe.business.impl.inbox.actions.DefaultInboxActionsModule;
import de.adorsys.datasafe.business.impl.keystore.DefaultKeyStoreModule;
import de.adorsys.datasafe.business.impl.privatestore.actions.DefaultPrivateActionsModule;
import de.adorsys.datasafe.business.impl.storage.DefaultStorageModule;
import de.adorsys.datasafe.directory.api.config.DFSConfig;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileOperations;
import de.adorsys.datasafe.inbox.api.InboxService;
import de.adorsys.datasafe.privatestore.api.PrivateSpaceService;
import de.adorsys.datasafe.storage.api.StorageService;
import de.adorsys.datasafe.types.api.context.overrides.OverridesRegistry;

import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * This is Datasafe services customized implementation.
 * Note, that despite is has {@code @Singleton} annotation, it is not real singleton, the only shared thing
 * across all services instantiated using build() is bindings with {@code Singleton} in its Module.
 */
@Singleton
@Component(modules = {
        DefaultCredentialsModule.class,
        DefaultKeyStoreModule.class,
        DefaultDocumentModule.class,
        DefaultCMSEncryptionModule.class,
        CustomPathEncryptionModule.class, // This module has disabled path encryption
        DefaultInboxActionsModule.class,
        DefaultPrivateActionsModule.class,
        DefaultProfileModule.class,
        DefaultStorageModule.class
})
public interface CustomlyBuiltDatasafeServices {

    /**
     * Services to access users' privatespace.
     */
    PrivateSpaceService privateService();

    /**
     * Services to access users' inbox.
     */
    InboxService inboxService();

    /**
     * Services to access users' profiles.
     */
    ProfileOperations userProfile();

    /**
     * Binds DFS connection (for example filesystem, minio) and system storage and access
     */
    @Component.Builder
    interface Builder {

        /**
         * Binds (configures) system root uri - where user profiles will be located and system
         * access to open (but not to read key) keystore.
         */
        @BindsInstance
        Builder config(DFSConfig config);

        /**
         * Binds (configures) all storage operations - not necessary to call {@code storageList} after.
         */
        @BindsInstance
        Builder storage(StorageService storageService);

        /**
         * Provides class overriding functionality, so that you can disable i.e. path encryption
         * @param overridesRegistry Map with class-overrides (note: you can override classes that are
         * annotated with {@code RuntimeDelegate})
         */
        @BindsInstance
        Builder overridesRegistry(@Nullable OverridesRegistry overridesRegistry);

        /**
         * @return Customized Datasafe services.
         */
        CustomlyBuiltDatasafeServices build();
    }
}
