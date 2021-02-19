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

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.*;
import org.apache.commons.configuration2.ex.*;
import org.apache.kerby.kerberos.kerb.*;
import org.kpax.winfoom.config.*;
import org.kpax.winfoom.kerberos.*;
import org.kpax.winfoom.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.config.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

import javax.swing.*;
import java.beans.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * The entry point for Winfoom application.
 */
@EnableScheduling
@SpringBootApplication
public class KerberosApplication {

    private static final Logger logger = LoggerFactory.getLogger(KerberosApplication.class);

    @Bean
    KerberosHttpProxyMock kerberosHttpProxyMock () throws KrbException, UnknownHostException {
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

        ReflectUtils.setFinalStatic(SystemContext.class, "IS_OS_WINDOWS", false);

        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.jgss.debug", "true");

        if (SystemContext.IS_GUI_MODE && !SystemContext.IS_OS_WINDOWS) {
            logger.error("Graphical mode is not supported on " + SystemContext.OS_NAME + ", exit the application");
            System.exit(1);
        }

        logger.info("Application started at: {}", new Date());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Application shutdown at: {}", new Date());
        }));

        if (SystemContext.IS_GUI_MODE) {
            try {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            } catch (Exception e) {
                logger.warn("Failed to set Windows L&F, use the default look and feel", e);
            }
        }

        // Check version
        try {
            checkAppVersion();
        } catch (Exception e) {
            logger.error("Failed to verify app version", e);
            if (SystemContext.IS_GUI_MODE) {
                SwingUtils.showErrorMessage(String.format("Failed to verify application version.<br>" +
                                "Remove the %s directory then try again.",
                        Paths.get(System.getProperty(SystemConfig.WINFOOM_CONFIG_ENV), SystemConfig.APP_HOME_DIR_NAME)));
            }
            System.exit(1);
        }

        logger.info("Bootstrap Spring's application context");
        try {
            ConfigurableApplicationContext applicationContext = SpringApplication.run(KerberosApplication.class, args);
            applicationContext.getBean(KerberosHttpProxyMock.class).start();
        } catch (Exception e) {
            logger.error("Error on bootstrapping Spring's application context", e);
            if (SystemContext.IS_GUI_MODE) {
                SwingUtils.showErrorMessage("Failed to launch the application." +
                        "<br>Please check the application's log file.");
            }
            System.exit(1);
        }

    }

    /**
     * Verify whether the existent system.properties file's releaseVersion property and
     * the application version (extracted from the MANIFEST file) are the same or backward compatible.
     * If not, the existent {@code *.properties} file are moved into a backup location.
     *
     * @throws IOException
     * @throws ConfigurationException
     */
    private static void checkAppVersion() throws IOException, ConfigurationException {
        logger.info("Check the application's version");
        Path appHomePath = Paths.get(System.getProperty(SystemConfig.WINFOOM_CONFIG_ENV), SystemConfig.APP_HOME_DIR_NAME);
        if (Files.exists(appHomePath)) {
            Path proxyConfigPath = appHomePath.resolve(ProxyConfig.FILENAME);
            if (Files.exists(proxyConfigPath)) {
                Configuration configuration = new Configurations()
                        .propertiesBuilder(proxyConfigPath.toFile()).getConfiguration();
                String existingVersion = configuration.getString("app.version");
                logger.info("existingVersion [{}]", existingVersion);
                if (existingVersion != null) {
                    String actualVersion = KerberosApplication.class.getPackage().getImplementationVersion();
                    logger.info("actualVersion [{}]", actualVersion);
                    if (actualVersion != null && !actualVersion.equals(existingVersion)) {
                        boolean isCompatibleProxyConfig = !Files.exists(proxyConfigPath)
                                || ProxyConfig.isCompatible(configuration);
                        logger.info("The existent proxy config is compatible with the new one: {}",
                                isCompatibleProxyConfig);

                        if (!isCompatibleProxyConfig) {
                            logger.info("Backup the existent proxy.properties file since is invalid" +
                                    " (from a previous incompatible version)");
                            InputOutputs.backupFile(proxyConfigPath,
                                    SystemContext.IS_GUI_MODE,
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else {
                    logger.info("Version not found within proxy.properties, " +
                            "backup both config files since they are invalid (from a previous incompatible version)");
                    InputOutputs.backupFile(proxyConfigPath,
                            SystemContext.IS_GUI_MODE,
                            StandardCopyOption.REPLACE_EXISTING);
                    InputOutputs.backupFile(appHomePath.resolve(SystemConfig.FILENAME),
                            SystemContext.IS_GUI_MODE,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                logger.info("No proxy.properties found, backup the system.properties file " +
                        "since is invalid (from a previous incompatible version)");
                InputOutputs.backupFile(appHomePath.resolve(SystemConfig.FILENAME),
                        SystemContext.IS_GUI_MODE,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

}
