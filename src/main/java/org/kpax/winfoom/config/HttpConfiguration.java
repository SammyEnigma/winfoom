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

package org.kpax.winfoom.config;

import org.apache.http.*;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.win.WindowsNTLMSchemeFactory;
import org.apache.http.impl.auth.win.WindowsNegotiateSchemeFactory;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.kpax.winfoom.event.AfterServerStopEvent;
import org.kpax.winfoom.event.BeforeServerStartEvent;
import org.kpax.winfoom.proxy.ProxyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 11/20/2019
 */
@EnableScheduling
@Configuration
public class HttpConfiguration {

    private final Logger logger = LoggerFactory.getLogger(HttpConfiguration.class);

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Autowired
    private ProxyContext proxyContext;

    /**
     * @return The HTTP connection manager.
     */
    @Bean
    PoolingHttpClientConnectionManager connectionManager() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

        logger.info("Configure connection manager");
        if (systemConfig.getMaxConnections() != null) {
            connectionManager.setMaxTotal(systemConfig.getMaxConnections());
        }
        if (systemConfig.getMaxConnectionsPerRoute() != null) {
            connectionManager.setDefaultMaxPerRoute(systemConfig.getMaxConnectionsPerRoute());
        }

        return connectionManager;
    }

    @Bean
    HttpClientBuilder httpClientBuilder() {
        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
                .register(AuthSchemes.NTLM, new WindowsNTLMSchemeFactory(null))
                .register(AuthSchemes.SPNEGO, new WindowsNegotiateSchemeFactory(null))
                .build();
        HttpClientBuilder builder = WinHttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .setDefaultSocketConfig(socketConfig())
                .setConnectionManager(connectionManager())
                .setConnectionManagerShared(true)
                .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
                .setServiceUnavailableRetryStrategy(new ProxyAuthenticationRequiredRetryStrategy())
                .setRetryHandler(new RepeatableHttpRequestRetryHandler())
                .disableRedirectHandling()
                .disableCookieManagement();

        if (systemConfig.isUseSystemProperties()) {
            builder.useSystemProperties();
        }
        return builder;
    }

    /**
     * Configure the <code>HttpClientBuilder</code> instance
     * with the user's values.
     *
     * @return The <code>ApplicationListener<BeforeServerStartEvent></code> instance.
     */
    @Bean
    ApplicationListener<BeforeServerStartEvent> onServerStartEventApplicationListener() {
        return event -> {
            logger.info("Set http builder's request config");
            RequestConfig requestConfig = createRequestConfig();
            httpClientBuilder()
                    .setDefaultRequestConfig(requestConfig)
                    .setRoutePlanner(new DefaultProxyRoutePlanner(requestConfig.getProxy()));
        };
    }

    /**
     * Purges the pooled HTTP connection manager after stopping the local proxy.
     *
     * @return The <code>ApplicationListener<AfterServerStopEvent></code> instance.
     */
    @Bean
    ApplicationListener<AfterServerStopEvent> onServerStopEventApplicationListener() {
        return event -> {
            logger.info("Close expired/idle connections");
            PoolingHttpClientConnectionManager connectionManager = connectionManager();
            connectionManager.closeExpiredConnections();
            connectionManager.closeIdleConnections(0, TimeUnit.SECONDS);
        };
    }

    /**
     * A job that closes the idle/expired HTTP connections.
     */
    @Scheduled(fixedRateString = "#{systemConfig.connectionManagerCleanInterval * 1000}")
    void cleanUpConnectionManager() {
        if (proxyContext.isStarted()) {
            logger.debug("Execute connection manager pool clean up task");
            PoolingHttpClientConnectionManager connectionManager = connectionManager();
            connectionManager.closeExpiredConnections();
            connectionManager.closeIdleConnections(systemConfig.getConnectionManagerIdleTimeout(), TimeUnit.SECONDS);
        }
    }

    private SocketConfig socketConfig() {
        return SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSndBufSize(systemConfig.getSocketBufferSize())
                .setRcvBufSize(systemConfig.getSocketBufferSize())
                .build();
    }

    private RequestConfig createRequestConfig() {
        HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
        return RequestConfig.custom()
                .setProxy(proxy)
                .setCircularRedirectsAllowed(true)
                .build();
    }

    private class ProxyAuthenticationRequiredRetryStrategy implements ServiceUnavailableRetryStrategy {

        @Override
        public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpRequest request = HttpClientContext.adapt(context).getRequest();

            /*
            Repeat the request on 407 Proxy Authentication Required error code
            but only if the request has no body or a repeatable one.
             */
            return statusCode == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED
                    && executionCount < systemConfig.getRepeatsOnFailure()
                    && (!(request instanceof HttpEntityEnclosingRequest)
                    || ((HttpEntityEnclosingRequest) request).getEntity().isRepeatable());
        }

        @Override
        public long getRetryInterval() {
            return 0;
        }
    }

    private class RepeatableHttpRequestRetryHandler extends DefaultHttpRequestRetryHandler {

        @Override
        protected boolean handleAsIdempotent(HttpRequest request) {

            /*
            Allow repeating also when
            the request has a repeatable body
             */
            return !(request instanceof HttpEntityEnclosingRequest)
                    || ((HttpEntityEnclosingRequest) request).getEntity().isRepeatable();
        }

    }

}
