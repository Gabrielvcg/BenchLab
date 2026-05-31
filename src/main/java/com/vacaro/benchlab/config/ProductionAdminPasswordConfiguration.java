package com.vacaro.benchlab.config;

import com.vacaro.benchlab.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Configuration
@Profile("prod")
public class ProductionAdminPasswordConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ProductionAdminPasswordConfiguration.class);

    @Bean
    ApplicationRunner productionAdminPasswordInitializer(
        Environment environment,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        CacheManager cacheManager,
        TransactionTemplate transactionTemplate
    ) {
        return args ->
            transactionTemplate.executeWithoutResult(status ->
                enforceProductionUsers(environment, userRepository, passwordEncoder, cacheManager)
            );
    }

    void enforceProductionUsers(
        Environment environment,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        CacheManager cacheManager
    ) {
        String adminPassword = environment.getProperty("benchlab.security.admin-password");
        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException("BENCHLAB_SECURITY_ADMIN_PASSWORD must be set in production");
        }
        if ("admin".equals(adminPassword)) {
            throw new IllegalStateException("BENCHLAB_SECURITY_ADMIN_PASSWORD cannot use the default admin password");
        }

        userRepository
            .findOneByLogin("admin")
            .ifPresentOrElse(
                admin -> {
                    admin.setPassword(passwordEncoder.encode(adminPassword));
                    admin.setActivated(true);
                    userRepository.save(admin);
                    LOG.info("Contrasena del usuario administrador actualizada desde configuracion segura de produccion");
                },
                () -> LOG.warn("No se encontro el usuario administrador para actualizar su contrasena en produccion")
            );

        userRepository
            .findOneByLogin("user")
            .ifPresent(user -> {
                user.getAuthorities().clear();
                userRepository.save(user);
                userRepository.delete(user);
                LOG.warn("Usuario por defecto 'user' eliminado en arranque de produccion");
            });

        evictUserCaches(cacheManager);
    }

    private static void evictUserCaches(CacheManager cacheManager) {
        evict(cacheManager, UserRepository.USERS_BY_LOGIN_CACHE);
        evict(cacheManager, UserRepository.USERS_BY_EMAIL_CACHE);
    }

    private static void evict(CacheManager cacheManager, String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
