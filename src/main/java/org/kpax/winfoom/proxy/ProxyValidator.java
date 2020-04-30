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
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.exception.InvalidPacFileException;
import org.kpax.winfoom.proxy.connection.Socks4ConnectionSocketFactory;
import org.kpax.winfoom.proxy.connection.SocksConnectionSocketFactory;
import org.kpax.winfoom.util.HttpUtils;
import org.netbeans.core.network.proxy.pac.PacValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.security.auth.login.CredentialException;
import java.io.IOException;
import java.net.*;
import java.util.Iterator;
import java.util.List;

@Component
public class ProxyValidator {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private UserConfig userConfig;


    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyAutoConfig proxyAutoconfig;

    public void testProxyConfig()
            throws IOException, CredentialException, InvalidPacFileException, PacValidationException, URISyntaxException {
        logger.info("Test proxy config {}", userConfig);

        ProxyType proxyType = userConfig.getProxyType();
        if (proxyType.isPac()) {
            proxyAutoconfig.loadScript();
            HttpHost httpHost = HttpHost.create(userConfig.getProxyTestUrl());
            List<ProxyInfo> proxyInfos = proxyAutoconfig.findProxyForURL(new URI(httpHost.toURI()));
            for (Iterator<ProxyInfo> itr = proxyInfos.iterator(); itr.hasNext(); ) {
                ProxyInfo proxyInfo = itr.next();
                logger.info("Validate {}", proxyInfo);
                ProxyInfo.Type type = proxyInfo.getType();
                try {
                    HttpHost host = proxyInfo.getHost();
                    testProxyConfig(true,
                            type.isSocks5(),
                            type.isSocks4(),
                            type.isHttp(),
                            host != null ? host.getHostName() : null,
                            host != null ? host.getPort() : -1,
                            null, null);
                    break;
                } catch (HttpHostConnectException e) {
                    if (itr.hasNext()) {
                        logger.warn("Error on validate this proxy, will try the next one");
                    } else {
                        throw e;
                    }
                }
            }
        } else {
            testProxyConfig(false,
                    proxyType.isSocks5(),
                    proxyType.isSocks4(),
                    proxyType.isHttp(),
                    userConfig.getProxyHost(),
                    userConfig.getProxyPort(),
                    userConfig.getProxyUsername(),
                    userConfig.getProxyPassword());
        }

    }

    private void testProxyConfig(boolean isPac,
                                 boolean isSocks5,
                                 boolean isSocks4,
                                 boolean isHttp,
                                 String proxyHost,
                                 int proxyPort,
                                 String proxyUsername,
                                 String proxyPassword)
            throws IOException, CredentialException {

        logger.info("Test proxy with isPac={}, isSocks5={}, isSocks4={}, isHttp={}, proxyHost={}, proxyPort={}, proxyUsername={}, proxyPassword={}",
                isPac,
                isSocks5,
                isSocks4,
                isHttp,
                proxyHost,
                proxyPort,
                proxyUsername,
                proxyPassword);

        HttpClientBuilder httpClientBuilder;
        if (isSocks4 || isSocks5) {
            if (!isPac
                    && isSocks5
                    && StringUtils.isNotEmpty(proxyUsername)) {
                Authenticator.setDefault(new Authenticator() {
                    public PasswordAuthentication getPasswordAuthentication() {
                        return (new PasswordAuthentication(proxyUsername,
                                proxyPassword != null ? proxyPassword.toCharArray() : new char[0]));
                    }
                });
            }
            ConnectionSocketFactory connectionSocketFactory = isSocks4
                    ? new Socks4ConnectionSocketFactory() : new SocksConnectionSocketFactory();
            Registry<ConnectionSocketFactory> factoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", connectionSocketFactory)
                    .register("https", connectionSocketFactory)
                    .build();

            httpClientBuilder = HttpClients.custom().setConnectionManager(new PoolingHttpClientConnectionManager(factoryRegistry));
        } else {
            httpClientBuilder = WinHttpClients.custom();
        }

        try (CloseableHttpClient httpClient = httpClientBuilder.build()) {
            HttpHost target = HttpHost.create(userConfig.getProxyTestUrl());
            HttpGet request = new HttpGet("/");
            if (isHttp) {
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                RequestConfig config = systemConfig.applyConfig(RequestConfig.custom())
                        .setProxy(proxy)
                        .build();
                request.setConfig(config);
            }
            HttpClientContext context = HttpClientContext.create();
            if (isSocks4 || isSocks5) {
                context.setAttribute(HttpUtils.SOCKS_ADDRESS, new InetSocketAddress(proxyHost, proxyPort));
            }
            logger.info("Executing request {} to {}", request.getRequestLine(), target);
            try (CloseableHttpResponse response = httpClient.execute(target, request, context)) {
                StatusLine statusLine = response.getStatusLine();
                logger.info("Test response status {}", statusLine);
                if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                    logger.info("Test OK");
                } else if (statusLine.getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                    throw new CredentialException(statusLine.getReasonPhrase());
                }
            }
        } catch (Exception e) {
            logger.error("Error on testing http proxy config", e);
            throw e;
        }
    }
}