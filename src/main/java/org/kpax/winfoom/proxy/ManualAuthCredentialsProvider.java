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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.proxy.listener.StopListener;
import org.kpax.winfoom.util.DomainUser;
import org.kpax.winfoom.util.functional.SingletonSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * The {@link CredentialsProvider} for manual authentication.
 */
public class ManualAuthCredentialsProvider implements CredentialsProvider, StopListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ProxyConfig proxyConfig;

    private final SingletonSupplier<Credentials> credentialsSupplier = new SingletonSupplier<>(() -> {
        if (proxyConfig.isKerberos()) {
            logger.debug("No credentials for Kerberos");
            return new NoCredentials();
        } else if (proxyConfig.isNtlm()) {
            DomainUser domainUser = DomainUser.from(proxyConfig.getProxyUsername());
            logger.debug("Create NTLM credentials using domainUser {}", domainUser);
            return new NTCredentials(domainUser.getUsername(), proxyConfig.getProxyPassword(), null, domainUser.getDomain());
        } else {
            logger.debug("Create basic credentials for username: {}", proxyConfig.getProxyUsername());
            return new UsernamePasswordCredentials(proxyConfig.getProxyUsername(),
                    proxyConfig.getProxyPassword());
        }
    });

    public ManualAuthCredentialsProvider(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public void setCredentials(AuthScope authscope, Credentials credentials) {
        throw new UnsupportedOperationException("Cannot supply credentials this way");
    }

    @Override
    public Credentials getCredentials(AuthScope authscope) {
        return credentialsSupplier.get();
    }

    @Override
    public void clear() {
        credentialsSupplier.reset();
    }

    @Override
    public void onStop() {
        clear();
    }

    private static class NoCredentials implements Credentials {

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

    }
}
