package org.kpax.winfoom.util;

import org.kpax.winfoom.annotation.NotNull;

import java.util.Locale;

public final class DomainUser {

    private final String username;
    private final String domain;

    public DomainUser(@NotNull String domainUsername) {
        int backslashIndex = domainUsername.indexOf('\\');
        this.username = backslashIndex > -1 ?
                domainUsername.substring(backslashIndex + 1) : domainUsername;
        this.domain = backslashIndex > -1 ?
                domainUsername.substring(0, backslashIndex).toUpperCase(Locale.ROOT) : null;
    }

    public String getUsername() {
        return username;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public String toString() {
        return "DomainUser{" +
                "username='" + username + '\'' +
                ", domain='" + domain + '\'' +
                '}';
    }
}
