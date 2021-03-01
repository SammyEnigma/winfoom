/*
 * Copyright (c) 2020. Eugen Covaci
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.kpax.winfoom;

import org.apache.kerby.kerberos.kerb.KrbException;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.kerberos.KerberosHttpProxyMock;
import org.kpax.winfoom.proxy.ProxyController;
import org.kpax.winfoom.util.Base64DecoderPropertyEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.beans.PropertyEditor;
import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.kpax.winfoom.config.SystemConfig.WINFOOM_CONFIG_ENV;

/**
 * Helper class for testing Kerberos protocol.
 */
@EnableScheduling
@SpringBootApplication
public class KerberosApplication {

    private static final Logger logger = LoggerFactory.getLogger(KerberosApplication.class);

    static {
        String relPath = FoomApplicationTest.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        File targetDir = new File(relPath + "../../target");
        File homeDir = Paths.get(targetDir.getAbsolutePath(), "config", SystemConfig.APP_HOME_DIR_NAME).toFile();
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        System.setProperty(WINFOOM_CONFIG_ENV, homeDir.getParentFile().getAbsolutePath());
    }

    @Bean
    KerberosHttpProxyMock kerberosHttpProxyMock() throws KrbException, UnknownHostException {
        return new KerberosHttpProxyMock.KerberosHttpProxyMockBuilder().build();
    }

    @Bean
    static CustomEditorConfigurer propertyEditorRegistrar() {
        CustomEditorConfigurer customEditorConfigurer = new CustomEditorConfigurer();
        Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>();
        customEditors.put(String.class, Base64DecoderPropertyEditor.class);
        customEditorConfigurer.setCustomEditors(customEditors);
        return customEditorConfigurer;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.jgss.debug", "true");

        logger.info("Application started at: {}", new Date());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Application shutdown at: {}", new Date());
        }));

        logger.info("Current path: " + new File(".").getAbsolutePath());

        // Set KRB5_CONFIG
        System.setProperty("KRB5_CONFIG", "./src/test/resources/krb5.conf");

        logger.info("Bootstrap Spring's application context");
        try {
            ConfigurableApplicationContext applicationContext = SpringApplication.run(KerberosApplication.class, args);
            applicationContext.getBean(KerberosHttpProxyMock.class).start();

            // Configure for Kerberos
            ProxyConfig proxyConfig = applicationContext.getBean(ProxyConfig.class);
            proxyConfig.setProxyType(ProxyConfig.Type.HTTP);
            proxyConfig.setProxyHost("localhost");
            proxyConfig.setProxyPort(KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_PROXY_PORT);
            proxyConfig.setUseCurrentCredentials(false);
            proxyConfig.setHttpAuthProtocol(ProxyConfig.HttpAuthProtocol.KERBEROS);
            proxyConfig.setProxyUsername(KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_REALM +
                    "\\" +
                    KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_USERNAME);
            proxyConfig.setProxyPassword(KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_PASSWORD);

            // Start the local proxy server
            applicationContext.getBean(ProxyController.class).start();
        } catch (Exception e) {
            logger.error("Error on bootstrapping Spring's application context", e);
            System.exit(1);
        }
    }

}
