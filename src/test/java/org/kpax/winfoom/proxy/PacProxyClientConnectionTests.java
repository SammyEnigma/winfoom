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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.FoomApplicationTest;
import org.kpax.winfoom.TestConstants;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.exception.PacFileException;
import org.kpax.winfoom.pac.DefaultPacScriptEvaluator;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kpax.winfoom.TestConstants.LOCAL_PROXY_PORT;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = FoomApplicationTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PacProxyClientConnectionTests {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int socketTimeout = 3; // seconds
    private final int connectTimeout = 2; // seconds

    @MockBean
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ClientConnectionHandler clientConnectionHandler;

    @Autowired
    private ConnectionPoolingManager connectionPoolingManager;

    @Autowired
    private ProxyAutoConfig proxyAutoConfig;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    private ServerSocket serverSocket;

    private HttpServer remoteServer;

    @BeforeEach
    void beforeEach() {
        when(proxyConfig.getProxyType()).thenReturn(ProxyConfig.Type.PAC);
        when(proxyConfig.isAutoConfig()).thenReturn(true);
        when(proxyConfig.getBlacklistTimeout()).thenReturn(1);
    }

    @BeforeAll
    void before() throws IOException {

        ReflectionTestUtils.setField(systemConfig, "socketConnectTimeout", connectTimeout);

        remoteServer = ServerBootstrap.bootstrap().registerHandler("/get", new HttpRequestHandler() {

            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                    throws IOException {
                response.setEntity(new StringEntity("12345"));
            }

        }).create();
        remoteServer.start();

        serverSocket = new ServerSocket(TestConstants.LOCAL_PROXY_PORT);
        connectionPoolingManager.start();
        new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(socketTimeout * 1000);
                    new Thread(() -> {

                        // Handle this connection.
                        try {
                            clientConnectionHandler.handleConnection(socket);
                        } catch (Exception e) {
                            logger.error("Error on handling connection", e);
                        }
                    }).start();
                } catch (SocketException e) {
                    if (!StringUtils.startsWithIgnoreCase(e.getMessage(), "Interrupted function call")) {
                        logger.error("Socket error on getting connection", e);
                    }
                } catch (Exception e) {
                    logger.error("Error on getting connection", e);
                }
            }
        }).start();

    }

    @Order(1)
    @Test
    void singleProxy_socks4ConnectAndNonConnect_CorrectResponse() throws IOException, PacFileException {
        ClientAndServer proxyServer = ClientAndServer.startClientAndServer();
        try {
            String content = String.format("function FindProxyForURL(url, host) {return \"SOCKS4 localhost:%s\";}", proxyServer.getLocalPort());
            logger.debug("content {}", content);
            DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
            ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

            HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
                RequestConfig config = RequestConfig.custom()
                        .setProxy(localProxy)
                        .build();
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpGet request = new HttpGet("/get");
                request.setConfig(config);

                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    String responseBody = EntityUtils.toString(response.getEntity());
                    assertEquals("12345", responseBody);
                }
            }

            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setProxy(localProxy).build()) {
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }
            }

        } finally {
            proxyServer.stop();
        }
    }

    @Order(2)
    @Test
    void singleProxy_Socks5ConnectAndNonConnect_CorrectResponse() throws IOException, PacFileException {
        ClientAndServer proxyServer = ClientAndServer.startClientAndServer();
        try {
            String content = String.format("function FindProxyForURL(url, host) {return \"SOCKS5 localhost:%s\";}", proxyServer.getLocalPort());
            logger.debug("content {}", content);
            DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
            ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

            HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
                RequestConfig config = RequestConfig.custom()
                        .setProxy(localProxy)
                        .build();
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpGet request = new HttpGet("/get");
                request.setConfig(config);

                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    String responseBody = EntityUtils.toString(response.getEntity());
                    assertEquals("12345", responseBody);
                }
            }

            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setProxy(localProxy).build()) {
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }
            }
        } finally {
            proxyServer.stop();
        }
    }

    @Order(3)
    @Test
    void singleProxy_HttpConnectAndNonConnect_CorrectResponse() throws IOException, PacFileException {
        ClientAndServer proxyServer = ClientAndServer.startClientAndServer();
        try {
            String content = String.format("function FindProxyForURL(url, host) {return \"HTTP localhost:%s\";}", proxyServer.getLocalPort());
            logger.debug("content {}", content);
            DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
            ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

            HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
                RequestConfig config = RequestConfig.custom()
                        .setProxy(localProxy)
                        .build();
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpGet request = new HttpGet("/get");
                request.setConfig(config);

                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    String responseBody = EntityUtils.toString(response.getEntity());
                    assertEquals("12345", responseBody);
                }
            }

            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setProxy(localProxy).build()) {
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }
            }
        } finally {
            proxyServer.stop();
        }
    }

    @Order(4)
    @Test
    void singleProxy_ProxyConnectAndNonConnect_CorrectResponse() throws IOException, PacFileException {
        ClientAndServer proxyServer = ClientAndServer.startClientAndServer();
        try {
            String content = String.format("function FindProxyForURL(url, host) {return \"PROXY localhost:%s\";}", proxyServer.getLocalPort());
            logger.debug("content {}", content);
            DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
            ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

            HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
                RequestConfig config = RequestConfig.custom()
                        .setProxy(localProxy)
                        .build();
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpGet request = new HttpGet("/get");
                request.setConfig(config);

                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    String responseBody = EntityUtils.toString(response.getEntity());
                    assertEquals("12345", responseBody);
                }
            }

            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setProxy(localProxy).build()) {
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }
            }
        } finally {
            proxyServer.stop();
        }
    }

    @Order(5)
    @Test
    void singleProxy_SocksConnectAndNonConnect_CorrectResponse() throws IOException, PacFileException {
        ClientAndServer proxyServer = ClientAndServer.startClientAndServer();
        try {
            String content = String.format("function FindProxyForURL(url, host) {return \"SOCKS localhost:%s\";}", proxyServer.getLocalPort());
            logger.debug("content {}", content);
            DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
            ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

            HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
                RequestConfig config = RequestConfig.custom()
                        .setProxy(localProxy)
                        .build();
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpGet request = new HttpGet("/get");
                request.setConfig(config);

                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    String responseBody = EntityUtils.toString(response.getEntity());
                    assertEquals("12345", responseBody);
                }
            }

            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setProxy(localProxy).build()) {
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }
            }
        } finally {
            proxyServer.stop();
        }
    }

    @Order(6)
    @Test
    void singleProxy_DirectConnectAndNonConnect_CorrectResponse() throws IOException, PacFileException {
        String content = String.format("function FindProxyForURL(url, host) {return \"DIRECT\";}");
        logger.debug("content {}", content);
        DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
        ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(localProxy)
                    .build();
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpGet request = new HttpGet("/get");
            request.setConfig(config);

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                assertEquals("12345", responseBody);
            }
        }

        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Order(7)
    @Test
    void multipleProxiesFirstDown_socks4AndHttpConnectAndNonConnect_CorrectResponse()
            throws IOException, PacFileException {
        ClientAndServer proxyServer = ClientAndServer.startClientAndServer();
        proxyBlacklist.clear();
        try {
            String content = String.format(
                    "function FindProxyForURL(url, host) {return \"SOCKS4 192.168.111.000:1234;HTTP localhost:%s\";}",
                    proxyServer.getLocalPort());
            logger.debug("content {}", content);
            DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
            ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

            HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
                RequestConfig config = RequestConfig.custom()
                        .setProxy(localProxy)
                        .build();
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpGet request = new HttpGet("/get");
                request.setConfig(config);

                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    String responseBody = EntityUtils.toString(response.getEntity());
                    assertEquals("12345", responseBody);
                }
            }

            Map<ProxyInfo, Instant> blacklistMap = proxyBlacklist.getActiveBlacklistMap();

            assertEquals(1, blacklistMap.size());
            assertEquals("192.168.111.000:1234", blacklistMap.keySet().iterator().next().getProxyHost().toHostString());

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries()
                    .setProxy(localProxy).build()) {
                HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
                HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
                try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }
            }

            assertEquals(1, blacklistMap.size());
            assertEquals("192.168.111.000:1234", blacklistMap.keySet().iterator().next().getProxyHost().toHostString());

        } finally {
            proxyServer.stop();
        }
    }


    @Order(8)
    @Test
    void nullProxyLine_DirectConnectAndNonConnect_NoProxyCorrectResponse() throws IOException, PacFileException {
        String content = String.format("function FindProxyForURL(url, host) {return null;}");
        logger.debug("content {}", content);
        DefaultPacScriptEvaluator defaultPacScriptEvaluator = new DefaultPacScriptEvaluator(content, false);
        ReflectionTestUtils.setField(proxyAutoConfig, "nbPacScriptEvaluator", defaultPacScriptEvaluator);

        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().disableAutomaticRetries().build()) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(localProxy)
                    .build();
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpGet request = new HttpGet("/get");
            request.setConfig(config);

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                assertEquals("12345", responseBody);
            }
        }

        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            }
        }
    }

    @AfterAll
    void after() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Ignore
        }
        remoteServer.stop();
    }
}
