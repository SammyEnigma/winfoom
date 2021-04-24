package org.kpax.winfoom.proxy.auth;

import com.sun.security.auth.module.Krb5LoginModule;
import lombok.extern.slf4j.Slf4j;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.proxy.listener.ProxyListener;
import org.kpax.winfoom.util.DateUtils;
import org.kpax.winfoom.util.functional.PrivilegedActionWrapper;
import org.kpax.winfoom.util.functional.SingletonSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginException;
import java.security.PrivilegedActionException;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for Kerberos authentication, authorization.
 */
@Slf4j
@Component
public class KerberosModule implements ProxyListener {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    private final SingletonSupplier<Krb5JaasAuth> kerberosAuthenticatorSupplier =
            new SingletonSupplier<>(() -> {
                Assert.state(proxyConfig.isKerberos(), "Not configured for Kerberos");
                System.setProperty("java.security.krb5.conf", proxyConfig.getKrb5ConfFilepath());
                return new Krb5JaasAuth();
            });

    public void authenticate() throws LoginException {
        kerberosAuthenticatorSupplier.get().authenticate();
    }

    public void execute(PrivilegedActionWrapper action) throws PrivilegedActionException {
        kerberosAuthenticatorSupplier.get().execute(action);
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

    private class Krb5JaasAuth implements AutoCloseable {

        volatile Krb5LoginModule loginContext;

        volatile Subject subject;

        volatile KerberosTicket kerberosTicket;

        private synchronized void authenticate() throws LoginException {
            logger.debug("Attempting Kerberos authentication");

            if (kerberosTicket != null) {

                // Enforcing the minimum login gap policy.
                // This is necessary to avoid useless multiple logins
                // in simultaneous access cases.
                if (kerberosTicket.isCurrent() &&
                        kerberosTicket.getAuthTime() != null &&
                        DateUtils.secondsFromCurrent(kerberosTicket.getAuthTime()) < systemConfig.getKerberosLoginMinInterval()) {
                    logger.debug("Already authenticated, nothing to do");
                    return;
                }

                if (kerberosTicket.isRenewable()) {
                    try {
                        // Try to renew the ticket
                        kerberosTicket.refresh();
                        logger.debug("Ticket refresh succeeded");
                        return;
                    } catch (Exception e) {
                        logger.debug("Ticket refresh failed", e);
                    }
                }

            }

            if (loginContext != null) {
                try {
                    logger.debug("Existent LoginContext found, try logout");
                    loginContext.logout();
                } catch (Exception e) {
                    logger.warn("Cannot logout the former LoginContext: {}", e.getMessage());
                }
            }

            try {
                loginContext = new Krb5LoginModule();
                Map<String, String> map = new HashMap<>();
                map.put("storeKey", "true");
                if (logger.isDebugEnabled()) {
                    map.put("debug", "true");
                }
                subject = new Subject();

                loginContext.initialize(subject, callbacks -> {
                    for (Callback callback : callbacks) {
                        if (callback instanceof NameCallback) {
                            ((NameCallback) callback).setName(proxyConfig.getProxyKrbPrincipal());
                        } else if (callback instanceof PasswordCallback) {
                            ((PasswordCallback) callback).setPassword(proxyConfig.getProxyHttpPassword().toCharArray());
                        }
                    }
                }, null, map);

                logger.debug("About to login principal {}", proxyConfig.getProxyKrbPrincipal());
                loginContext.login();
                loginContext.commit();
            } catch (Exception e) {
                // Cleanup on exception
                logout();
                throw e;
            }

            // Retrieve the Kerberos credentials
            // Get Kerberos ticket
            for (Object o : subject.getPrivateCredentials()) {
                if (o instanceof KerberosTicket) {
                    kerberosTicket = ((javax.security.auth.kerberos.KerberosTicket) o);
                }
            }

            logger.debug("kerberosTicket {}", kerberosTicket);
        }

        void logout() {
            if (loginContext != null) {
                try {
                    loginContext.abort();
                    loginContext = null;
                } catch (Exception e) {
                    logger.debug("Error on Kerberos logout: {}", e.getMessage());
                }
            }
            subject = null;
            kerberosTicket = null;
        }

        void execute(PrivilegedActionWrapper action) throws PrivilegedActionException {
            if (subject != null) {
                Subject.doAs(subject, action);
            } else {
                throw new SecurityException("Kerberos authentication not found, you need to login first");
            }
        }


        @Override
        public void close() {
            logout();
        }
    }


}
