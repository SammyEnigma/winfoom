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

import org.kpax.winfoom.proxy.ProxyInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Select the appropriate {@link ClientConnectionProcessor} to process a {@link org.kpax.winfoom.proxy.ClientConnection}.
 */
@Component
public class ConnectionProcessorSelector {

    @Autowired
    private HttpConnectClientConnectionProcessor httpConnectClientConnectionProcessor;

    @Autowired
    private SocksConnectClientConnectionProcessor socksConnectClientConnectionProcessor;

    @Autowired
    private NonConnectClientConnectionProcessor nonConnectClientConnectionProcessor;

    @Autowired
    private SocksNonConnectClientConnectionProcessor socksNonConnectClientConnectionProcessor;

    public ClientConnectionProcessor selectConnectionProcessor(boolean isConnect, ProxyInfo proxyInfo) {
        if (isConnect) {
            if (proxyInfo.getType().isSocks() || proxyInfo.getType().isDirect()) {
                return socksConnectClientConnectionProcessor;
            } else {
                return httpConnectClientConnectionProcessor;
            }
        } else {
            if (proxyInfo.getType().isSocks() || proxyInfo.getType().isDirect()) {
                return socksNonConnectClientConnectionProcessor;
            } else {
                return nonConnectClientConnectionProcessor;
            }
        }
    }
}
