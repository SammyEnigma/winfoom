package org.kpax.winfoom.auth;

import org.kpax.winfoom.config.*;
import org.kpax.winfoom.proxy.*;
import org.kpax.winfoom.util.functional.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.*;
import org.springframework.stereotype.*;
import org.springframework.util.*;

import javax.security.auth.login.*;

@Component
public class AuthenticationContext implements StartListener, StopListener {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProxyConfig proxyConfig;

    private final SingletonSupplier<KerberosAuthenticator> kerberosAuthenticatorSupplier =
            new SingletonSupplier<>(() -> {
                System.setProperty("java.security.krb5.conf", proxyConfig.getKrb5ConfFilepath());
                return applicationContext.getBean(KerberosAuthenticator.class);
            });


    public KerberosAuthenticator kerberosAuthenticator() {
        Assert.state(proxyConfig.isKerberos(), "Not configured for Kerberos");
        return kerberosAuthenticatorSupplier.get();
    }

    public void authenticate() throws LoginException {
        kerberosAuthenticator().authenticate();
    }

    @Override
    public void onStart() throws Exception {
        if (proxyConfig.isKerberos()) {
            kerberosAuthenticatorSupplier.get().authenticate();
        }
    }

    @Override
    public void onStop() {
        if (proxyConfig.isKerberos()) {
            kerberosAuthenticatorSupplier.reset();
            System.clearProperty("java.security.krb5.conf");
        }
    }

}
