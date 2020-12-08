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

    /**
     * The supplier for the sharable {@link Engine} instance.
     */
    private SingletonSupplier<Engine> engineSingletonSupplier = new SingletonSupplier<>(() ->
            Engine.newBuilder().allowExperimentalOptions(true).build()
    );

    private final SingletonSupplier<String> helperJSScriptSupplier = new SingletonSupplier<>(() -> {
        try {
            return IOUtils.toString(getClass().getClassLoader().
                    getResourceAsStream("javascript/pacFunctions.js"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MissingResourceException("pacFunctions.js not found in classpath", e);
        }
    });


    /**
     * The {@link GenericObjectPool} supplier.
     * <p>Since the Graaljs {@link Context} is not thread safe, we maintain a pool of {@link GraalJSScriptEngine} instances.
     */
    private final SingletonSupplier<GenericObjectPool<GraalJSScriptEngine>> enginePoolSingletonSupplier =
            new SingletonSupplier<>(() -> {
                GenericObjectPoolConfig config = new GenericObjectPoolConfig();
                config.setMaxTotal(systemConfig.getPacScriptEnginePoolMaxTotal());
                config.setMinIdle(systemConfig.getPacScriptEnginePoolMinIdle());
                config.setTestOnBorrow(false);
                config.setTestOnCreate(false);
                config.setTestOnReturn(false);
                config.setBlockWhenExhausted(true);
                return new GenericObjectPool<GraalJSScriptEngine>(
                        new BasePooledObjectFactory<GraalJSScriptEngine>() {
                            @Override
                            public GraalJSScriptEngine create() throws PacFileException, IOException {
                                return createScriptEngine();
                            }

                            @Override
                            public PooledObject<GraalJSScriptEngine> wrap(GraalJSScriptEngine obj) {
                                return new DefaultPooledObject<GraalJSScriptEngine>(obj);
                            }
                        }, config);
            });

    private volatile String jsMainFunction;

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

    private synchronized void initJsMainFunction(GraalJSScriptEngine scriptEngine) throws PacFileException {
        if (jsMainFunction == null) {
            if (isJsFunctionAvailable(scriptEngine, IPV6_AWARE_PAC_MAIN_FUNCTION)) {
                jsMainFunction = IPV6_AWARE_PAC_MAIN_FUNCTION;
            } else if (isJsFunctionAvailable(scriptEngine, STANDARD_PAC_MAIN_FUNCTION)) {
                jsMainFunction = STANDARD_PAC_MAIN_FUNCTION;
            } else {
                throw new PacFileException("Function " + STANDARD_PAC_MAIN_FUNCTION +
                        " or " + IPV6_AWARE_PAC_MAIN_FUNCTION + " not found in PAC Script.");
            }
        }
    }

    private GraalJSScriptEngine createScriptEngine() throws PacFileException, IOException {
        String pacSource = loadScript();
        try {
            GraalJSScriptEngine scriptEngine = GraalJSScriptEngine.create(engineSingletonSupplier.get(),
                    Context.newBuilder("js")
                            .allowHostAccess(HostAccess.ALL)
                            .allowHostClassLookup(s -> true)
                            .option("js.ecmascript-version", "2021"));
            Assert.notNull(scriptEngine, "GraalJS script engine not found");
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
            Object cleaner = scriptEngine.eval("(function(allowed) {\n"
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
                scriptEngine.invokeMethod(cleaner, "call", null, allowedGlobals);
            } catch (NoSuchMethodException ex) {
                throw new ScriptException(ex);
            }

            // Execute the PAC javascript file
            scriptEngine.eval(pacSource);

            // Load the Javascript file helper
            try {
                scriptEngine.invokeMethod(scriptEngine.eval(helperJSScriptSupplier.get()),
                        "call", null, pacHelperMethods);
            } catch (NoSuchMethodException ex) {
                throw new ScriptException(ex);
            }
            return scriptEngine;
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
        GraalJSScriptEngine scriptEngine = enginePoolSingletonSupplier.get().borrowObject();
        if (jsMainFunction == null) {
            initJsMainFunction(scriptEngine);
        }
        try {
            Object callResult;
            try {
                callResult = scriptEngine.invokeFunction(jsMainFunction,
                        HttpUtils.toStrippedURLStr(uri), uri.getHost());
            } finally {
                // Make sure we return the PacScriptEngine instance back to the pool
                enginePoolSingletonSupplier.get().returnObject(scriptEngine);
            }
            String proxyLine = Objects.toString(callResult, null);
            logger.debug("Parse proxyLine [{}] for uri [{}]", proxyLine, uri);
            return HttpUtils.parsePacProxyLine(proxyLine, proxyBlacklist::isActive);
        } catch (Exception ex) {
            throw new PacScriptException("Error when executing PAC script function: " + jsMainFunction, ex);
        }
    }

    private boolean isJsFunctionAvailable(GraalJSScriptEngine eng, String functionName) {
        // We want to test if the function is there, but without actually
        // invoking it.
        try {
            Object typeofCheck = eng.eval("(function(name) { return typeof this[name]; })");
            Object type = eng.invokeMethod(typeofCheck, "call", null, functionName);
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
        jsMainFunction = null;
    }

}
