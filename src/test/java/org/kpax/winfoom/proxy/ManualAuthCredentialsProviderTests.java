/*
 * Copyright (c) 2020. Eugen Covaci
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *
 */

package org.kpax.winfoom.proxy;


import org.apache.http.auth.*;
import org.junit.jupiter.api.*;
import org.kpax.winfoom.config.*;
import org.mockito.*;

import java.io.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ManualAuthCredentialsProviderTests {

    @Mock
    private ProxyConfig proxyConfig;

    @BeforeAll
    void before() throws IOException {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getCredentials_withoutDomain_NoError() {
        when(proxyConfig.getProxyUsername()).thenReturn("randomUser");
        when(proxyConfig.getProxyPassword()).thenReturn("randomPassword");

        ManualAuthCredentialsProvider manualAuthCredentialsProvider = new ManualAuthCredentialsProvider(proxyConfig);
        Credentials credentials = manualAuthCredentialsProvider.getCredentials(null);

        assertEquals("Incorrect principal", "randomUser", credentials.getUserPrincipal().getName());
        assertEquals("Incorrect password", "randomPassword", credentials.getPassword());
    }

    @Test
    public void getCredentials_withDomain_NoError() {
        when(proxyConfig.getProxyUsername()).thenReturn("MyDomain\\randomUser");
        when(proxyConfig.getProxyPassword()).thenReturn("randomPassword");
        when(proxyConfig.isNtlm()).thenReturn(true);

        ManualAuthCredentialsProvider manualAuthCredentialsProvider = new ManualAuthCredentialsProvider(proxyConfig);
        Credentials credentials = manualAuthCredentialsProvider.getCredentials(null);

        assertEquals("Incorrect principal", "MYDOMAIN\\randomUser", credentials.getUserPrincipal().getName());
        assertEquals("Incorrect password", "randomPassword", credentials.getPassword());
    }

}
