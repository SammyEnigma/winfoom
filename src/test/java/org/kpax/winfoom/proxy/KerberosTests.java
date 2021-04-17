package org.kpax.winfoom.proxy;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.KerberosApplicationTest;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.kerberos.KerberosHttpProxyMock;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kpax.winfoom.TestConstants.LOCAL_PROXY_PORT;
import static org.kpax.winfoom.TestConstants.PROXY_PORT;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = KerberosApplicationTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(10)
public class KerberosTests {

    @MockBean
    private ProxyConfig proxyConfig;

    @Autowired
    private ProxyController proxyController;

    private HttpServer remoteServer;

    private KerberosHttpProxyMock kerberosHttpProxyMock;

    @BeforeEach
    void beforeEach() {
        when(proxyConfig.getProxyType()).thenReturn(ProxyConfig.Type.HTTP);
        when(proxyConfig.getProxyHost()).thenReturn("localhost");
        when(proxyConfig.getProxyPort()).thenReturn(PROXY_PORT);
        when(proxyConfig.getLocalPort()).thenReturn(LOCAL_PROXY_PORT);
        when(proxyConfig.isAuthAutoMode()).thenReturn(false);

        when(proxyConfig.getProxyHttpUsername()).thenReturn(KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_REALM +
                "\\" + KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_USERNAME);
        when(proxyConfig.getProxyHttpPassword()).thenReturn(KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_PASSWORD);
        when(proxyConfig.getProxyKrbPrincipal()).thenReturn(KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_USERNAME + "@" + KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_REALM);
        when(proxyConfig.getHttpAuthProtocol()).thenReturn(ProxyConfig.HttpAuthProtocol.KERBEROS);
        when(proxyConfig.isKerberos()).thenReturn(true);
        when(proxyConfig.getKrb5ConfFilepath()).thenReturn(new File("src/test/resources/krb5.conf").getAbsolutePath());
    }

    @BeforeAll
    void before() throws Exception {
        beforeEach();

        remoteServer = ServerBootstrap.bootstrap().registerHandler("/post", new HttpRequestHandler() {

            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                    throws IOException {
                response.setEntity(new StringEntity("12345"));
            }

        }).create();
        remoteServer.start();

        kerberosHttpProxyMock = new KerberosHttpProxyMock.KerberosHttpProxyMockBuilder().withProxyPort(PROXY_PORT)
                .withDomain(InetAddress.getLocalHost().getHostName())
                .build();
        kerberosHttpProxyMock.start();

        proxyController.start();
    }

/*    private String getDomain () throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpPost request = new HttpPost("/post");
            request.setEntity(new StringEntity("whatever"));
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                final HttpRoute route = (HttpRoute) context.getAttribute(HttpClientContext.HTTP_ROUTE);
                if (route == null) {
                    throw new AuthenticationException("Connection route is not available");
                }
                HttpHost host;
                if (isProxy()) {
                    host = route.getProxyHost();
                    if (host == null) {
                        host = route.getTargetHost();
                    }
                } else {
                    host = route.getTargetHost();
                }
            }
        }
    }*/

    @Test
    @Order(1)
    void httpProxy_NonConnect_200OK() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(localProxy)
                    .build();
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpPost request = new HttpPost("/post");
            request.setConfig(config);
            request.setEntity(new StringEntity("whatever"));
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                assertEquals("12345", responseBody);
            }
        }
    }

    @Test
    @Order(2)
    void httpProxy_Connect_200OK() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                EntityUtils.consume(response.getEntity());
            }
        }
    }


    @Test
    @Order(3)
    void httpProxy_NonConnectWrongCredentials_407() throws IOException, KrbException {
        try {
            kerberosHttpProxyMock.stop();
        } catch (KrbException e) {
            e.printStackTrace();
        }
        kerberosHttpProxyMock = new KerberosHttpProxyMock.KerberosHttpProxyMockBuilder().
                withProxyPort(PROXY_PORT).
                withCredentials(Arrays.asList(
                        new UsernamePasswordCredentials(
                                KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_USERNAME, "4321"))).build();
        kerberosHttpProxyMock.start();

        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(localProxy)
                    .build();
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpPost request = new HttpPost("/post");
            request.setConfig(config);
            request.setEntity(new StringEntity("whatever"));
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, response.getStatusLine().getStatusCode());
                EntityUtils.consume(response.getEntity());
            }
        }
    }

    @Test
    @Order(4)
    void httpProxy_ConnectWrongCredentials_407() throws IOException, KrbException {
        try {
            kerberosHttpProxyMock.stop();
        } catch (KrbException e) {
            e.printStackTrace();
        }
        kerberosHttpProxyMock = new KerberosHttpProxyMock.KerberosHttpProxyMockBuilder().
                withProxyPort(PROXY_PORT).
                withCredentials(Arrays.asList(
                        new UsernamePasswordCredentials(
                                KerberosHttpProxyMock.KerberosHttpProxyMockBuilder.DEFAULT_USERNAME, "4321"))).build();
        kerberosHttpProxyMock.start();

        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, response.getStatusLine().getStatusCode());
                EntityUtils.consume(response.getEntity());
            }
        }
    }

    @AfterAll
    void after() {
        try {
            kerberosHttpProxyMock.stop();
        } catch (KrbException e) {
            e.printStackTrace();
        }
        remoteServer.shutdown(0, TimeUnit.MILLISECONDS);
        when(proxyConfig.getProxyType()).thenReturn(ProxyConfig.Type.HTTP);
        proxyController.stop();
    }
}
