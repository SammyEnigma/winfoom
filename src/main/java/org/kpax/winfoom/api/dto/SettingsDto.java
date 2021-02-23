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

package org.kpax.winfoom.api.dto;

import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemContext;
import org.kpax.winfoom.exception.InvalidProxySettingsException;
import org.kpax.winfoom.util.HttpUtils;
import org.springframework.util.Assert;

/**
 * The settings DTO for API server.
 */
public class SettingsDto {

    private Integer apiPort;
    private Boolean autodetect;
    private Boolean autostart;

    public Integer getApiPort() {
        return apiPort;
    }

    public void setApiPort(Integer apiPort) {
        this.apiPort = apiPort;
    }

    public Boolean getAutodetect() {
        return autodetect;
    }

    public void setAutodetect(Boolean autodetect) {
        this.autodetect = autodetect;
    }

    public Boolean getAutostart() {
        return autostart;
    }

    public void setAutostart(Boolean autostart) {
        this.autostart = autostart;
    }

    public void validate () throws InvalidProxySettingsException {
        if (apiPort != null) {
            if (!HttpUtils.isValidPort(apiPort)) {
                throw new InvalidProxySettingsException("Invalid apiPort, allowed range: 1 - 65535");
            }
        }
    }

    @Override
    public String toString() {
        return "SettingsDto{" +
                "apiPort=" + apiPort +
                ", autodetect=" + autodetect +
                ", autostart=" + autostart +
                '}';
    }
}
