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

package org.kpax.winfoom.proxy;

import org.apache.commons.lang3.Validate;
import org.apache.http.*;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.InputOutputs;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Eugen Covaci
 */
class RequestReadyClientConnection implements ClientConnection {

    private final Logger logger = LoggerFactory.getLogger(RequestReadyClientConnection.class);

    private final Socket socket;

    private final InputStream inputStream;

    private final OutputStream outputStream;

    private final SessionInputBufferImpl sessionInputBuffer;

    private final HttpRequest httpRequest;

    private boolean requestPrepared;

    private boolean lastResort;

    RequestReadyClientConnection(Socket socket) throws IOException, HttpException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.sessionInputBuffer = new SessionInputBufferImpl(
                new HttpTransportMetricsImpl(),
                InputOutputs.DEFAULT_BUFFER_SIZE,
                InputOutputs.DEFAULT_BUFFER_SIZE,
                MessageConstraints.DEFAULT,
                StandardCharsets.UTF_8.newDecoder());
        this.sessionInputBuffer.bind(this.inputStream);
        this.httpRequest = new DefaultHttpRequestParser(this.sessionInputBuffer).parse();
        this.outputStream = socket.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public SessionInputBufferImpl getSessionInputBuffer() {
        return sessionInputBuffer;
    }

    @Override
    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public void write(Object obj) throws IOException {
        outputStream.write(ObjectFormat.toCrlf(obj, StandardCharsets.UTF_8));
    }

    @Override
    public void writeln() throws IOException {
        outputStream.write(ObjectFormat.CRLF.getBytes());
    }

    @Override
    public void writeErrorResponse(int statusCode, Exception e) {
        writeErrorResponse(HttpVersion.HTTP_1_1, statusCode, e);
    }

    @Override
    public void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, String reasonPhrase) {
        try {
            write(HttpUtils.toStatusLine(protocolVersion, statusCode, reasonPhrase));
            writeln();
        } catch (Exception ex) {
            logger.debug("Error on writing error response", ex);
        }
    }

    @Override
    public void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, Exception e) {
        Validate.notNull(e, "Exception cannot be null");
        writeErrorResponse(protocolVersion, statusCode, e.getMessage());
    }

    @Override
    public void writeHttpResponse(final HttpResponse httpResponse) throws Exception {
        StatusLine statusLine = httpResponse.getStatusLine();
        logger.debug("Write statusLine {}", statusLine);
        write(statusLine);

        logger.debug("Write headers");
        for (Header header : httpResponse.getAllHeaders()) {
            write(header);
        }

        // Empty line between headers and the body
        writeln();

        HttpEntity entity = httpResponse.getEntity();
        if (entity != null) {
            logger.debug("Write entity content");
            entity.writeTo(outputStream);
        }
        EntityUtils.consume(entity);
    }

    @Override
    public boolean isRequestPrepared() {
        return this.requestPrepared;
    }

    @Override
    public void requestPrepared() {
        this.requestPrepared = true;
    }

    @Override
    public boolean isLastResort() {
        return this.lastResort;
    }

    @Override
    public void lastResort() {
        this.lastResort = true;
    }


    @Override
    public void close() {
        InputOutputs.close(socket);
    }
}
