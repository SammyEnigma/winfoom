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

package org.kpax.winfoom.proxy;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.FoomApplicationTest;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.ScopeConfiguration;
import org.kpax.winfoom.exception.PacFileException;
import org.kpax.winfoom.pac.DefaultPacScriptEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.net.URL;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = FoomApplicationTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DefaultPacScriptEvaluatorTests {
    @MockBean
    private ProxyConfig proxyConfig;

    @Lazy
    @Autowired
    private DefaultPacScriptEvaluator pacScriptEvaluator;

    @Autowired
    private ScopeConfiguration scopeConfiguration;

    private HttpServer remoteServer;

    @BeforeAll
    void beforeAll() throws IOException {
        remoteServer = ServerBootstrap.bootstrap().registerHandler("/pacFile", new HttpRequestHandler() {

            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
                response.setEntity(new InputStreamEntity(getClass().getClassLoader().getResourceAsStream("proxy-simple.pac")));
            }

        }).create();

        remoteServer.start();
    }

    @Test
    void loadPacFileContent_validLocalFile_NoError() throws IOException, PacFileException {
        when(proxyConfig.getProxyPacFileLocationAsURL()).thenReturn(getClass().getClassLoader().getResource("proxy-simple.pac"));
        scopeConfiguration.getProxySessionScope().clear();
    }

    @Test
    void loadPacFileContent_validRemoteFile_NoError() throws IOException, PacFileException {
        when(proxyConfig.getProxyPacFileLocationAsURL()).thenReturn(new URL("http://localhost:" + remoteServer.getLocalPort() + "/pacFile"));
        scopeConfiguration.getProxySessionScope().clear();
    }


    @Test
    void loadPacFileContent_invalidLocalFile_InvalidPacFileException() throws IOException {
        when(proxyConfig.getProxyPacFileLocationAsURL()).thenReturn(getClass().getClassLoader().getResource("proxy-invalid.pac"));
        scopeConfiguration.getProxySessionScope().clear();
    }

    @AfterAll
    void after() {
        remoteServer.stop();
    }
}
