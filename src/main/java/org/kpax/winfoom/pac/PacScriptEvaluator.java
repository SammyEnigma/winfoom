/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications copyright (c) 2020. Eugen Covaci
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.kpax.winfoom.pac;

import com.oracle.truffle.js.scriptengine.*;
import org.apache.commons.io.*;
import org.apache.commons.pool2.*;
import org.apache.commons.pool2.impl.*;
import org.graalvm.polyglot.*;
import org.kpax.winfoom.annotation.*;
import org.kpax.winfoom.config.*;
import org.kpax.winfoom.exception.MissingResourceException;
import org.kpax.winfoom.exception.*;
import org.kpax.winfoom.proxy.*;
import org.kpax.winfoom.util.*;
import org.kpax.winfoom.util.functional.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.util.*;

import javax.script.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;

@ThreadSafe
@Order(3)
@Component
public class PacScriptEvaluator implements Resetable {

    private final Logger logger = LoggerFactory.getLogger(PacScriptEvaluator.class);


    /**
     * Main entry point to JavaScript PAC script as defined by Netscape.
     * This is JavaScript function name {@code FindProxyForURL()}.
     */
    private static final String STANDARD_PAC_MAIN_FUNCTION = "FindProxyForURL";

    /**
     * Main entry point to JavaScript PAC script for IPv6 support,
     * as defined by Microsoft.
     * This is JavaScript function name {@code FindProxyForURLEx()}.
     */
    private static final String IPV6_AWARE_PAC_MAIN_FUNCTION = "FindProxyForURLEx";


    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private DefaultPacHelperMethods pacHelperMethods;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    private final DoubleExceptionSingletonSupplier<PacScriptEngine, PacFileException, IOException> scriptEngineSupplier =
            new DoubleExceptionSingletonSupplier<PacScriptEngine, PacFileException, IOException>(this::createScriptEngine);

    private final SingletonSupplier<String> helperJSScriptSupplier = new SingletonSupplier<>(() -> {
        try {
            return IOUtils.toString(getClass().getClassLoader().
                    getResourceAsStream("javascript/pacFunctions.js"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MissingResourceException("pacFunctions.js not found in classpath", e);
        }
    });

    private final SingletonSupplier<GenericObjectPool<PacScriptEngine>> enginePoolSingletonSupplier =
            new SingletonSupplier<>(() -> {
                GenericObjectPoolConfig config = new GenericObjectPoolConfig();
                config.setMaxTotal(systemConfig.getPacScriptEnginePoolMaxTotal());
                config.setMinIdle(systemConfig.getPacScriptEnginePoolMinIdle());
                config.setTestOnBorrow(false);
                config.setTestOnCreate(false);
                config.setTestOnReturn(false);
                config.setBlockWhenExhausted(true);

                return new GenericObjectPool<PacScriptEngine>(
                        new BasePooledObjectFactory<PacScriptEngine>() {
                            @Override
                            public PacScriptEngine create() throws PacFileException, IOException {
                                return createScriptEngine();
                            }

                            @Override
                            public PooledObject<PacScriptEngine> wrap(PacScriptEngine obj) {
                                return new DefaultPooledObject<PacScriptEngine>(obj);
                            }
                        }, config);
            });

    /**
     * Load and parse the PAC script file.
     *
     * @return the {@link PacScriptEvaluator} instance.
     * @throws IOException
     */
    private String loadScript() throws IOException {
        URL url = proxyConfig.getProxyPacFileLocationAsURL();
        Assert.state(url != null, "No proxy PAC file location found");
        logger.info("Get PAC file from: {}", url);
        try (InputStream inputStream = url.openStream()) {
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            logger.info("PAC content: {}", content);
            return content;
        }
    }

    private PacScriptEngine createScriptEngine() throws PacFileException, IOException {
        String pacSource = loadScript();
        try {
            ScriptEngine engine = GraalJSScriptEngine.create(null,
                    Context.newBuilder("js")
                            .allowHostAccess(HostAccess.ALL)
                            .allowHostClassLookup(s -> true)
                            .option("js.ecmascript-version", "2021"));
            Assert.notNull(engine, "GraalJS script engine not found");
            String[] allowedGlobals =
                    ("Object,Function,Array,String,Date,Number,BigInt,"
                            + "Boolean,RegExp,Math,JSON,NaN,Infinity,undefined,"
                            + "isNaN,isFinite,parseFloat,parseInt,encodeURI,"
                            + "encodeURIComponent,decodeURI,decodeURIComponent,eval,"
                            + "escape,unescape,"
                            + "Error,EvalError,RangeError,ReferenceError,SyntaxError,"
                            + "TypeError,URIError,ArrayBuffer,Int8Array,Uint8Array,"
                            + "Uint8ClampedArray,Int16Array,Uint16Array,Int32Array,"
                            + "Uint32Array,Float32Array,Float64Array,BigInt64Array,"
                            + "BigUint64Array,DataView,Map,Set,WeakMap,"
                            + "WeakSet,Symbol,Reflect,Proxy,Promise,SharedArrayBuffer,"
                            + "Atomics,console,performance,"
                            + "arguments").split(",");
            Object cleaner = engine.eval("(function(allowed) {\n"
                    + "   var names = Object.getOwnPropertyNames(this);\n"
                    + "   MAIN: for (var i = 0; i < names.length; i++) {\n"
                    + "     for (var j = 0; j < allowed.length; j++) {\n"
                    + "       if (names[i] === allowed[j]) {\n"
                    + "         continue MAIN;\n"
                    + "       }\n"
                    + "     }\n"
                    + "     delete this[names[i]];\n"
                    + "   }\n"
                    + "})");
            try {
                ((Invocable) engine).invokeMethod(cleaner, "call", null, allowedGlobals);
            } catch (NoSuchMethodException ex) {
                throw new ScriptException(ex);
            }

            // Execute the PAC javascript file
            engine.eval(pacSource);

            try {
                ((Invocable) engine).invokeMethod(engine.eval(helperJSScriptSupplier.get()), "call", null, pacHelperMethods);
            } catch (NoSuchMethodException ex) {
                throw new ScriptException(ex);
            }
            return new PacScriptEngine(engine);
        } catch (ScriptException e) {
            throw new PacFileException(e);
        }
    }

    /**
     * <p>
     * Call the JavaScript {@code FindProxyForURL(url, host)}
     * function in the PAC script (or alternatively the
     * {@code FindProxyForURLEx(url, host)} function).
     *
     * @param uri URI to get proxies for.
     * @return The non-blacklisted proxies {@link ProxyInfo} list.
     * @throws PacScriptException when something goes wrong with the JavaScript function's call.
     * @throws PacFileException   when the PAC file is invalid.
     * @throws IOException        when the PAC file cannot be loaded.
     */
    public List<ProxyInfo> findProxyForURL(URI uri) throws Exception {
        PacScriptEngine scriptEngine = enginePoolSingletonSupplier.get().borrowObject();
        try {
            Object callResult;
            try {
                callResult = scriptEngine.findProxyForURL(HttpUtils.toStrippedURLStr(uri), uri.getHost());
            } finally {
                // Make sure we return the PacScriptEngine instance back to the pool
                enginePoolSingletonSupplier.get().returnObject(scriptEngine);
            }
            String proxyLine = Objects.toString(callResult, null);
            logger.debug("Parse proxyLine [{}] for uri [{}]", proxyLine, uri);
            return HttpUtils.parsePacProxyLine(proxyLine, proxyBlacklist::isActive);
        } catch (Exception ex) {
            if (ex.getCause() instanceof ClassNotFoundException) {
                // Is someone trying to break out of the sandbox ?
                logger.warn("The downloaded PAC script is attempting to access Java class [{}] " +
                        "which may be a sign of maliciousness. " +
                        "You should investigate this with your network administrator.", ex.getCause());
            }
            // other unforeseen errors
            throw new PacScriptException("Error when executing PAC script function: " + scriptEngine.jsMainFunction, ex);
        }
    }

    private boolean isJsFunctionAvailable(ScriptEngine eng, String functionName) {
        // We want to test if the function is there, but without actually
        // invoking it.
        try {
            Object typeofCheck = eng.eval("(function(name) { return typeof this[name]; })");
            Object type = ((Invocable) eng).invokeMethod(typeofCheck, "call", null, functionName);
            return "function".equals(type);
        } catch (NoSuchMethodException | ScriptException ex) {
            logger.warn("Error on testing if the function is there", ex);
            return false;
        }
    }

    @Override
    public void close() {
        logger.debug("Reset the scriptEngineSupplier");
        enginePoolSingletonSupplier.reset();
    }

    private class PacScriptEngine {
        private final Invocable invocable;
        private final String jsMainFunction;

        PacScriptEngine(ScriptEngine scriptEngine) throws PacFileException {
            this.invocable = (Invocable) scriptEngine;
            if (isJsFunctionAvailable(scriptEngine, IPV6_AWARE_PAC_MAIN_FUNCTION)) {
                this.jsMainFunction = IPV6_AWARE_PAC_MAIN_FUNCTION;
            } else if (isJsFunctionAvailable(scriptEngine, STANDARD_PAC_MAIN_FUNCTION)) {
                this.jsMainFunction = STANDARD_PAC_MAIN_FUNCTION;
            } else {
                throw new PacFileException("Function " + STANDARD_PAC_MAIN_FUNCTION +
                        " or " + IPV6_AWARE_PAC_MAIN_FUNCTION + " not found in PAC Script.");
            }
        }

        Object findProxyForURL(String url, String host) throws ScriptException, NoSuchMethodException {
            return invocable.invokeFunction(jsMainFunction, url, host);
        }
    }

}
