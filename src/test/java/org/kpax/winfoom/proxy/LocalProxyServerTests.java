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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.FoomApplicationTest;
import org.kpax.winfoom.config.ProxyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.kpax.winfoom.TestConstants.LOCAL_PROXY_PORT;
import static org.mockito.Mockito.when;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/4/2020
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = FoomApplicationTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalProxyServerTests {

    @MockBean
    private ProxyConfig proxyConfig;

    @SpyBean
    private LocalProxyServer localProxyServer;

    @Autowired
    private ProxyController proxyController;

    @BeforeEach
    void before() {
        when(proxyConfig.getLocalPort()).thenReturn(LOCAL_PROXY_PORT);
        when(proxyConfig.getProxyHost()).thenReturn("localhost");
        when(proxyConfig.getProxyPort()).thenReturn(80);
    }

    @Test
    void server_DoStart_True() throws Exception {
        localProxyServer.start();
    }

    @AfterAll
    void after() throws Exception {
        if (localProxyServer != null) {
            localProxyServer.close();
        }
    }
}
