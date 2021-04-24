/*
 *  Copyright (c) 2020. Eugen Covaci
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

package org.kpax.winfoom.proxy.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.kpax.winfoom.annotation.NotNull;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.exception.ProxyAuthorizationException;
import org.kpax.winfoom.exception.ProxyConnectException;
import org.kpax.winfoom.proxy.ClientConnection;
import org.kpax.winfoom.proxy.ProxyBlacklist;
import org.kpax.winfoom.proxy.ProxyInfo;
import org.kpax.winfoom.proxy.auth.KerberosModule;
import org.kpax.winfoom.util.StreamSource;
import org.springframework.beans.factory.annotation.Autowired;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.PrivilegedActionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Process a {@link ClientConnection} with a certain {@link ProxyInfo}.
 *
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/13/2020
 */
@Slf4j
public abstract class ClientConnectionProcessor {

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    @Autowired
    private KerberosModule kerberosModule;

    /**
     * Process the client's connection. That is:<br>
     * <ul>
     * <li>Prepare the client's request to make a remote HTTP request through the proxy or direct.</li>
     * <li>Make the remote HTTP request.</li>
     * <li>Give back to the client the resulted response (commit the response).</li>
     * </ul>
     * <p><b>Note: This method must not commit the response if doesn't return normally.</b></p>
     *
     * @param clientConnection the {@link ClientConnection} instance.
     * @param proxyInfo        The {@link ProxyInfo} used to make the remote HTTP request.
     * @throws HttpException if a HTTP exception has occurred
     * @throws IOException   if an input/output error occurs
     */
    abstract void handleRequest(@NotNull final ClientConnection clientConnection,
                                @NotNull final ProxyInfo proxyInfo)
            throws IOException, HttpException, ProxyAuthorizationException;

    /**
     * Handle the exception thrown by {@link #handleRequest(ClientConnection, ProxyInfo)} method.
     * <p><b>Note: This method must either commit the response or throw a {@link ProxyConnectException}</b></p>
     *
     * @param clientConnection the {@link ClientConnection} instance.
     * @param proxyInfo        the {@link ProxyInfo} used to make the remote HTTP request.
     * @param e                the exception thrown by {@link #handleRequest(ClientConnection, ProxyInfo)} method
     * @throws ProxyConnectException
     */
    abstract void handleError(@NotNull final ClientConnection clientConnection,
                              @NotNull final ProxyInfo proxyInfo,
                              @NotNull final Exception e)
            throws ProxyConnectException;

    /**
     * Call {@link #handleRequest(ClientConnection, ProxyInfo)} within Kerberos authenticated context.
     * This method rethrows all the exceptions thrown by {@link #handleRequest(ClientConnection, ProxyInfo)} method.
     *
     * @param clientConnection the {@link ClientConnection} instance.
     * @param proxyInfo        the {@link ProxyInfo} used to make the remote HTTP request.
     * @throws IOException
     * @throws HttpException
     * @throws ProxyAuthorizationException
     * @throws PrivilegedActionException
     */
    private void handleKerberosRequest(@NotNull final ClientConnection clientConnection,
                                       @NotNull final ProxyInfo proxyInfo)
            throws IOException, HttpException, ProxyAuthorizationException, PrivilegedActionException {
        try {
            kerberosModule.execute(() -> handleRequest(clientConnection, proxyInfo));
        } catch (PrivilegedActionException e) {
            logger.debug("Error on executing request within Kerberos context", e);
            Exception actualException = e.getException();
            if (actualException instanceof IOException) {
                throw (IOException) actualException;
            }
            if (actualException instanceof HttpException) {
                throw (HttpException) actualException;
            }
            if (actualException instanceof ProxyAuthorizationException) {
                throw (ProxyAuthorizationException) actualException;
            }
            if (actualException instanceof RuntimeException) {
                throw (RuntimeException) actualException;
            }
            throw e;
        }
    }

    /**
     * Simultaneously transfer bytes between two sources in a mutually independent manner.
     *
     * @param firstSource  The first source.
     * @param secondSource The second source.
     */
    void duplex(@NotNull final StreamSource firstSource,
                @NotNull final StreamSource secondSource) {
        logger.debug("Start full duplex communication");
        Future<?> secondToFirst = executorService.submit(
                () -> {
                    try {
                        secondSource.getInputStream().transferTo(firstSource.getOutputStream());
                    } catch (SocketTimeoutException e) {
                        logger.debug("Timeout exception on executing second to first transfer: {}", e.getMessage());
                    } catch (SocketException e) {
                        logger.debug("Socket exception on executing second to first transfer: {}", e.getMessage());
                    } catch (Exception e) {
                        logger.debug("Error on executing second to first transfer", e);
                    }
                });
        try {
            firstSource.getInputStream().transferTo(secondSource.getOutputStream());
        } catch (SocketTimeoutException e) {
            logger.debug("Timeout exception on executing first to second transfer: {}", e.getMessage());
        } catch (SocketException e) {
            logger.debug("Socket exception on executing first to second transfer: {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("Error on executing first to second transfer", e);
        }
        if (!secondToFirst.isDone()) {
            // Wait for the async transfer to finish
            try {
                secondToFirst.get();
            } catch (ExecutionException e) {// Normally, we shouldn't get here
                logger.debug("Error on executing second to first transfer", e.getCause());
            } catch (InterruptedException e) {
                logger.debug("Transfer from second to first interrupted: {}", e.getMessage());
            } catch (CancellationException e) {
                logger.debug("Transfer from second to first cancelled: {}", e.getMessage());
            }
        }
        logger.debug("End full duplex communication");
    }

    /**
     * Call the {@link #handleKerberosRequest(ClientConnection, ProxyInfo)} method.
     * On {@link ProxyAuthorizationException} re-authenticate and call the same method again.
     * <p>If it returns normally, the response will be considered committed.</p>
     *
     * @param clientConnection the {@link ClientConnection} instance.
     * @param proxyInfo        the {@link ProxyInfo} used to make the remote HTTP request.
     * @throws PrivilegedActionException
     * @throws IOException
     * @throws HttpException
     */
    private void processKerberos(@NotNull final ClientConnection clientConnection,
                                 @NotNull final ProxyInfo proxyInfo)
            throws PrivilegedActionException,
            IOException,
            HttpException {
        try {
            logger.debug("First attempt to handle request within Kerberos auth context");

            // Handle the request within Kerberos authenticated context
            handleKerberosRequest(clientConnection, proxyInfo);
        } catch (ProxyAuthorizationException e) {
            logger.debug("Authorization error on first attempt", e);
            try {
                // The Kerberos proxy rejected the request.
                // Normally this happens when the ticket is expired
                // so we re-login to get a new valid ticket
                kerberosModule.authenticate();

                logger.debug("Second attempt to handle request within Kerberos auth context");

                // Handle the request within Kerberos
                // authenticated context for the second time
                handleKerberosRequest(clientConnection, proxyInfo);
            } catch (LoginException le) {
                logger.debug("Failed to login to Kerberos proxy", le);
                clientConnection.writeProxyAuthRequiredErrorResponse();
            } catch (ProxyAuthorizationException pae) {
                logger.debug("Authorization error on second attempt", pae);
                clientConnection.writeHttpResponse(pae.getResponse());
            }
        }
    }

    /**
     * Call the {@link #handleRequest(ClientConnection, ProxyInfo)} method
     * then {@link #handleError(ClientConnection, ProxyInfo, Exception)} method
     * if an exception occurs.
     * Also, blacklist the autoconfig proxy on {@link ProxyConnectException}.
     * <p>If it returns normally, the response will be considered committed.</p>
     *
     * @param clientConnection the {@link ClientConnection} instance.
     * @param proxyInfo        the {@link ProxyInfo} used to make the remote HTTP request.
     * @throws ProxyConnectException
     */
    public final void process(@NotNull final ClientConnection clientConnection,
                              @NotNull final ProxyInfo proxyInfo)
            throws ProxyConnectException {
        logger.debug("Process {} for {}", clientConnection, proxyInfo);
        try {
            if (proxyConfig.isKerberos()) {
                try {
                    processKerberos(clientConnection, proxyInfo);
                } catch (PrivilegedActionException e) {
                    logger.debug("Failed to execute action within Kerberos authenticated context", e);
                    clientConnection.writeErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            "Cannot perform Kerberos authentication");
                }
            } else {
                handleRequest(clientConnection, proxyInfo);
            }
        } catch (Exception e) {
            logger.debug("Error on handling request", e);
            try {
                handleError(clientConnection, proxyInfo, e);
            } catch (ProxyConnectException pce) {
                if (proxyConfig.isAutoConfig()) {
                    proxyBlacklist.blacklist(proxyInfo);
                }
                throw pce;
            }
        }
    }

}
