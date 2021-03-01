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


import org.apache.http.HttpHost;
import org.apache.http.RequestLine;
import org.kpax.winfoom.annotation.NotNull;
import org.kpax.winfoom.annotation.ThreadSafe;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.pac.PacScriptEvaluator;
import org.kpax.winfoom.proxy.listener.StopListener;
import org.kpax.winfoom.proxy.processor.ConnectionProcessorSelector;
import org.kpax.winfoom.util.InputOutputs;
import org.kpax.winfoom.util.functional.SingletonSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Socket;

/**
 * Responsible for handling client's connection.
 */
@ThreadSafe
@Component
public class ClientConnectionHandler implements StopListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private PacScriptEvaluator pacScriptEvaluator;

    @Autowired
    private ConnectionProcessorSelector connectionProcessorSelector;

    /**
     * Supplier for ProxyInfo, manual case.
     */
    private final SingletonSupplier<ProxyInfo> proxyInfoSupplier = new SingletonSupplier<>(() -> {
        HttpHost proxyHost = proxyConfig.getProxyType().isDirect() ? null :
                new HttpHost(proxyConfig.getProxyHost(), proxyConfig.getProxyPort());
        return new ProxyInfo(proxyConfig.getProxyType(), proxyHost);
    });

    /**
     * Create a {@link ClientConnection} instance then process it.
     *
     * @param socket the client's socket
     * @throws Exception
     */
    public void handleConnection(@NotNull final Socket socket) throws Exception {
        final ClientConnection clientConnection;
        if (proxyConfig.isAutoConfig()) {
            clientConnection = new ClientConnection(socket, proxyConfig, systemConfig,
                    connectionProcessorSelector, pacScriptEvaluator);
        } else {
            clientConnection = new ClientConnection(socket, proxyConfig, systemConfig,
                    connectionProcessorSelector, proxyInfoSupplier.get());
        }
        try {
            RequestLine requestLine = clientConnection.getRequestLine();
            logger.debug("Handle request: {}", requestLine);
            clientConnection.process();
            logger.debug("Done handling request: {}", requestLine);
        } finally {
            InputOutputs.close(clientConnection);
        }
    }

    @Override
    public void onStop() {
        proxyInfoSupplier.reset();
    }
}
