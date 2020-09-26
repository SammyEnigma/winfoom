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

import org.apache.http.HttpException;

import java.io.IOException;
import java.net.ConnectException;

/**
 * Process a {@link ClientConnection} with a certain {@link ProxyInfo}.
 *
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/13/2020
 */
interface ClientConnectionProcessor {

    /**
     * Process the client's connection. That is:<br>
     * <ul>
     * <li>Prepare the client's request to make a remote HTTP request through the proxy or direct.</li>
     * <li>Make the remote HTTP request.</li>
     * <li>Give back to the client the resulted response or an error response when no response is available.</li>
     * </ul>
     *
     * @param clientConnection the {@link ClientConnection} instance.
     * @param proxyInfo        The {@link ProxyInfo} used to make the remote HTTP request.
     * @throws ConnectException when proxy connection fails
     * @throws HttpException    if a HTTP exception has occurred
     * @throws IOException      if an input/output error occurs
     */
    void process(ClientConnection clientConnection, ProxyInfo proxyInfo)
            throws IOException, HttpException;

}
