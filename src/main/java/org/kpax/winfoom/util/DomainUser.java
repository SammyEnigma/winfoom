package org.kpax.winfoom.util;

import org.kpax.winfoom.annotation.NotNull;

import java.util.Locale;

public final class DomainUser {

    private final String username;
    private final String domain;

    public DomainUser(String username, String domain) {
        this.username = username;
        this.domain = domain;
    }

    public String getUsername() {
        return username;
    }

    public String getDomain() {
        return domain;
    }

    public static DomainUser from(@NotNull String domainUsername) {
        int backslashIndex = domainUsername.indexOf('\\');
        return new DomainUser(backslashIndex > -1 ?
                domainUsername.substring(backslashIndex + 1) : domainUsername,
                backslashIndex > -1 ?
                        domainUsername.substring(0, backslashIndex).toUpperCase(Locale.ROOT) : null);
    }

    public static String extractUsername(String domainUsername) {
        if (domainUsername != null) {
            int backslashIndex = domainUsername.indexOf('\\');
            return backslashIndex > -1 ?
                    domainUsername.substring(backslashIndex + 1) : domainUsername;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "DomainUser{" +
                "username='" + username + '\'' +
                ", domain='" + domain + '\'' +
                '}';
    }
}
