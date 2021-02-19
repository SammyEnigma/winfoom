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

import org.apache.http.client.config.RequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.Socket;
import java.net.SocketException;

/**
 * The proxy facade system configuration.
 * <p>This settings cannot change once the application is started.
 *
 * @author Eugen Covaci
 */
@Component
@PropertySource(value = "file:${" + SystemConfig.WINFOOM_CONFIG_ENV + "}/" + SystemConfig.APP_HOME_DIR_NAME + "/" + SystemConfig.FILENAME,
        ignoreResourceNotFound = true)
public class SystemConfig {

    public static final String FILENAME = "system.properties";

    public static final String APP_HOME_DIR_NAME = ".winfoom";

    public static final String BACKUP_DIR_NAME = "backup";
    public static final String WINFOOM_CONFIG_ENV = "WINFOOM_CONFIG";

    private final Logger logger = LoggerFactory.getLogger(SystemConfig.class);

    /**
     * Connection pool property:  max polled connections per route.
     */
    @Value("${maxConnections.perRoute:20}")
    private Integer maxConnectionsPerRoute;

    /**
     * Connection pool property: max polled connections.
     */
    @Value("${maxConnections:600}")
    private Integer maxConnections;

    /**
     * The max size of the entity buffer (bytes).
     */
    @Value("${internalBuffer.length:102400}")
    private Integer internalBufferLength;

    /**
     * The frequency of running purge idle
     * on the connection manager pool (seconds).
     */
    @Value("${connectionManager.clean.interval:60}")
    private Integer connectionManagerCleanInterval;

    /**
     * The connections idle timeout,
     * to be purged by a scheduled task (seconds).
     */
    @Value("${connectionManager.idleTimeout:100}")
    private Integer connectionManagerIdleTimeout;

    /**
     * The maximum number of pending connections.
     */
    @Value("${serverSocket.backlog:1000}")
    private Integer serverSocketBacklog;

    /**
     * The timeout for read/write through socket channel (seconds).
     */
    @Value("${socket.soTimeout:65}")
    private Integer socketSoTimeout;

    /**
     * The timeout for socket connect (seconds).
     */
    @Value("${socket.connectTimeout:20}")
    private Integer socketConnectTimeout;

    /**
     * The timeout for request connection (seconds).
     */
    @Value("${connection.request.timeout:30}")
    private Integer connectionRequestTimeout;

    /**
     * This is a Java system property.<br>
     * When dealing with a host which has both IPv4 and IPv6 addresses,and if IPv6 is available on the operating system,
     * the default behavior is to prefer using IPv4 addresses over IPv6 ones.<br>
     * This is to ensure backward compatibility, for example applications that depend on the representation of an IPv4 address (e.g. 192.168.1.1).<br>
     * This property can be set to true to change that preference and use IPv6 addresses over IPv4 ones where possible.<br>
     */
    @Value("${java.net.preferIPv6Addresses:false}")
    private boolean preferIPv6Addresses;

    /**
     * The cache2k default capacity.
     */
    @Value("${cache.globPattern.capacity:100}")
    private Integer cacheGlobPatternCapacity;

    /**
     * The pacScriptEngine pool maximum total instances.
     */
    @Value("${pacScriptEngine.pool.maxTotal:100}")
    private Integer pacScriptEnginePoolMaxTotal;

    /**
     * The pacScriptEngine pool min idle instances.
     */
    @Value("${pacScriptEngine.pool.minIdle:20}")
    private Integer pacScriptEnginePoolMinIdle;

    /**
     * The request timeout for API server (seconds).
     */
    @Value("${apiServer.request.timeout:10}")
    private Integer apiServerRequestTimeout;

    /**
     * The minimum interval successful Kerberos login is allowed (seconds)
     */
    @Value("${kerberos.login.minInterval:30}")
    private Integer kerberosLoginMinInterval;

    @PostConstruct
    private void init() {
        logger.info("System settings: {}", this);
    }

    public Integer getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public Integer getInternalBufferLength() {
        return internalBufferLength;
    }

    public Integer getConnectionManagerCleanInterval() {
        return connectionManagerCleanInterval;
    }

    public Integer getConnectionManagerIdleTimeout() {
        return connectionManagerIdleTimeout;
    }

    public Integer getServerSocketBacklog() {
        return serverSocketBacklog;
    }

    public Integer getSocketSoTimeout() {
        return socketSoTimeout;
    }

    public Integer getSocketConnectTimeout() {
        return socketConnectTimeout;
    }

    public boolean isPreferIPv6Addresses() {
        return preferIPv6Addresses;
    }

    public Integer getCacheGlobPatternCapacity() {
        return cacheGlobPatternCapacity;
    }

    public Integer getPacScriptEnginePoolMaxTotal() {
        return pacScriptEnginePoolMaxTotal;
    }

    public Integer getPacScriptEnginePoolMinIdle() {
        return pacScriptEnginePoolMinIdle;
    }

    public Integer getApiServerRequestTimeout() {
        return apiServerRequestTimeout;
    }

    public Integer getKerberosLoginMinInterval() {
        return kerberosLoginMinInterval;
    }

    public RequestConfig.Builder applyConfig(final RequestConfig.Builder configBuilder) {
        return configBuilder.setConnectTimeout(socketConnectTimeout * 1000)
                .setConnectionRequestTimeout(connectionRequestTimeout * 1000)
                .setSocketTimeout(socketSoTimeout * 1000);
    }

    public Socket configureSocket(final Socket socket) throws SocketException {
        socket.setSoTimeout(socketSoTimeout * 1000);
        return socket;
    }

    @Override
    public String toString() {
        return "SystemConfig{" +
                "maxConnectionsPerRoute=" + maxConnectionsPerRoute +
                ", maxConnections=" + maxConnections +
                ", internalBufferLength=" + internalBufferLength +
                ", connectionManagerCleanInterval=" + connectionManagerCleanInterval +
                ", connectionManagerIdleTimeout=" + connectionManagerIdleTimeout +
                ", serverSocketBacklog=" + serverSocketBacklog +
                ", socketSoTimeout=" + socketSoTimeout +
                ", socketConnectTimeout=" + socketConnectTimeout +
                ", connectionRequestTimeout=" + connectionRequestTimeout +
                ", preferIPv6Addresses=" + preferIPv6Addresses +
                ", cacheGlobPatternCapacity=" + cacheGlobPatternCapacity +
                ", pacScriptEnginePoolMaxTotal=" + pacScriptEnginePoolMaxTotal +
                ", pacScriptEnginePoolMinIdle=" + pacScriptEnginePoolMinIdle +
                ", apiServerRequestTimeout=" + apiServerRequestTimeout +
                ", kerberosLoginMinInterval=" + kerberosLoginMinInterval +
                '}';
    }
}
