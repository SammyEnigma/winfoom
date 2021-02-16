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

import org.apache.commons.lang3.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.apache.http.protocol.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.*;
import org.kpax.winfoom.config.*;
import org.kpax.winfoom.util.*;
import org.mockito.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.kpax.winfoom.TestConstants.*;
import static org.kpax.winfoom.config.SystemContext.WINFOOM_CONFIG_ENV;
import static org.mockito.Mockito.*;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 2/29/2020
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = FoomApplicationTest.class)
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(10)
class RepeatableHttpEntityTests {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String echoContentHeader = "Echo-content";
    private final String streamingHeader = "Is-streaming";
    private final String tempFilenameHeader = "Temp-filename";
    private final String tempFileContentHeader = "Temp-file-content";
    private final String bufferedBytesHeader = "Buffered-bytes";

    private ServerSocket serverSocket;

    private int bufferSize = 1024;

    private Path tempDirectory;

    @Mock
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    @BeforeAll
    void before() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(proxyConfig.getProxyType()).thenReturn(ProxyConfig.Type.HTTP);
        when(proxyConfig.isAutoConfig()).thenReturn(false);
        when(proxyConfig.getProxyHost()).thenReturn("localhost");
        when(proxyConfig.getProxyPort()).thenReturn(PROXY_PORT);

        tempDirectory = Paths.get(System.getProperty(WINFOOM_CONFIG_ENV), "temp");
        Files.createDirectories(tempDirectory);
        logger.info("Using temp directory {}", tempDirectory);

        serverSocket = new ServerSocket(TestConstants.PROXY_PORT);

        final ServerSocket server = serverSocket;

        new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> {

                        // Handle this connection.
                        try {
                            ClientConnection clientConnection = new ClientConnection(socket, proxyConfig, systemConfig, null);
                            RepeatableHttpEntity requestEntity;
                            HttpRequest request = clientConnection.getRequest();
                            try {
                                requestEntity = new RepeatableHttpEntity(request, clientConnection.getSessionInputBuffer(), tempDirectory,
                                        bufferSize);
                                Header transferEncoding = request.getFirstHeader(HTTP.TRANSFER_ENCODING);
                                if (transferEncoding != null && HTTP.CHUNK_CODING.equalsIgnoreCase(transferEncoding.getValue())) {
                                    requestEntity.setChunked(true);
                                }
                                ((HttpEntityEnclosingRequest) request).setEntity(requestEntity);
                                clientConnection.write("HTTP/1.1 200 OK");
                            } catch (Exception e) {
                                clientConnection.write("HTTP/1.1 500 " + e.getMessage());
                                clientConnection.writeln();
                                throw e;
                            }

                            if (request.containsHeader(echoContentHeader)) {
                                clientConnection.write(request.getFirstHeader(HTTP.CONTENT_LEN));
                                clientConnection.write(request.getFirstHeader(HTTP.CONTENT_TYPE));
                                clientConnection.writeln();
                                requestEntity.getContent().transferTo(clientConnection.getOutputStream());
                            } else {

                                // Read the entity
                                HttpUtils.consumeEntity(requestEntity);

                                boolean streaming = (Boolean) ReflectionTestUtils.getField(requestEntity, "streaming");
                                clientConnection.write(HttpUtils.createHttpHeader(streamingHeader, String.valueOf(streaming)));

                                Path tempFilepath = (Path) ReflectionTestUtils.getField(requestEntity, "tempFilepath");
                                if (tempFilepath != null) {
                                    clientConnection.write(HttpUtils.createHttpHeader(tempFilenameHeader, tempFilepath.getFileName().toString()));
                                    clientConnection.write(HttpUtils.createHttpHeader(tempFileContentHeader, Files.readString(tempFilepath)));
                                }

                                byte[] bufferedBytes = (byte[]) ReflectionTestUtils.getField(requestEntity, "bufferedBytes");
                                if (bufferedBytes != null) {
                                    clientConnection.write(HttpUtils.createHttpHeader(bufferedBytesHeader, String.valueOf(bufferedBytes.length)));
                                }

                                clientConnection.write(HttpUtils.createHttpHeader(HTTP.CONTENT_LEN, "0"));
                                clientConnection.writeln();
                            }

                        } catch (Exception e) {
                            logger.error("Error on handling connection", e);
                        } finally {
                            InputOutputs.close(socket);
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

    @Test
    void repeatable_BufferLessThanContentLength_UseTempFile() throws IOException {//OK
        this.bufferSize = 1;
        final String content = "12345";
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + TestConstants.PROXY_PORT);
            HttpPost request = new HttpPost("/");
            request.setEntity(new StringEntity(content));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                assertTrue(response.containsHeader(streamingHeader));
                assertEquals("false", response.getFirstHeader(streamingHeader).getValue());
                assertTrue(response.containsHeader(tempFilenameHeader));
                assertEquals(content, response.getFirstHeader(tempFileContentHeader).getValue());
            }
        }
    }

    @Test
    void repeatable_BufferEqualsContentLength_Buffering() throws IOException {//OK
        this.bufferSize = 5;
        final String content = "12345";
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + TestConstants.PROXY_PORT);
            HttpPost request = new HttpPost("/");
            request.setEntity(new StringEntity(content));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                assertEquals("false", response.getFirstHeader(streamingHeader).getValue());
                assertFalse(response.containsHeader(tempFilenameHeader));
                assertEquals(String.valueOf(content.getBytes().length),
                        response.getFirstHeader(bufferedBytesHeader).getValue());
            }
        }
    }

    @Test
    void repeatable_BufferBiggerThanContentLength_Buffering() throws IOException {//OK
        this.bufferSize = 7;
        final String content = "12345";
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + TestConstants.PROXY_PORT);
            HttpPost request = new HttpPost("/");
            request.setEntity(new StringEntity(content));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                assertEquals("false", response.getFirstHeader(streamingHeader).getValue());
                assertFalse(response.containsHeader(tempFilenameHeader));
                assertEquals(String.valueOf(content.getBytes().length),
                        response.getFirstHeader(bufferedBytesHeader).getValue());
            }
        }
    }

    @Test
    void repeatable_NegativeContentLengthBufferBiggerThanRealContentLength_UseTempFile() throws IOException {//OK
        this.bufferSize = 10000000;
        final String content = "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque" +
                " laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto " +
                "beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit " +
                "aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro " +
                "quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam " +
                "eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima " +
                "veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi" +
                " consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae" +
                " consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?";

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + TestConstants.PROXY_PORT);
            HttpPost request = new HttpPost("/");
            InputStreamEntity streamEntity = new InputStreamEntity(new ByteArrayInputStream(content.getBytes()));
            streamEntity.setChunked(true);
            request.setEntity(streamEntity);

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                assertEquals("false", response.getFirstHeader(streamingHeader).getValue());
                assertTrue(response.containsHeader(tempFilenameHeader));
                assertEquals(content, response.getFirstHeader(tempFileContentHeader).getValue());
            }
        }
    }

    @Test
    void repeatable_NegativeContentLengthBufferLessThanContentLength_UseTempFile() throws IOException {//OK
        this.bufferSize = 2;
        String content = "12345";
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + TestConstants.PROXY_PORT);
            HttpPost request = new HttpPost("/");
            request.setEntity(new InputStreamEntity(new ByteArrayInputStream(content.getBytes())));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                assertEquals("false", response.getFirstHeader(streamingHeader).getValue());
                assertTrue(response.containsHeader(tempFilenameHeader));
                assertEquals(content, response.getFirstHeader(tempFileContentHeader).getValue());
            }
        }
    }


    @Test
    void repeatable_NoAvailableData_DoNotUseTempFile() throws IOException {//OK
        this.bufferSize = 1024;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + TestConstants.PROXY_PORT);
            HttpPost request = new HttpPost("/");

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                assertEquals("false", response.getFirstHeader(streamingHeader).getValue());
                assertFalse(response.containsHeader(tempFilenameHeader));
            }
        }
    }


    @AfterAll
    void after() throws IOException {
        serverSocket.close();
        if (tempDirectory != null) {
            InputOutputs.emptyDirectory(tempDirectory.toFile());
        }
    }

}
