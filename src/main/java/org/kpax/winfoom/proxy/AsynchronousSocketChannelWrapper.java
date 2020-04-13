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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.Validate;
import org.apache.http.*;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A helper class that wraps an {@link AsynchronousSocketChannel}.
 *
 * @author Eugen Covaci
 */
class AsynchronousSocketChannelWrapper implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(AsynchronousSocketChannelWrapper.class);

    private final AsynchronousSocketChannel socketChannel;

    private final int socketChannelTimeout;

    private final InputStream inputStream;

    private final OutputStream outputStream;


    AsynchronousSocketChannelWrapper(AsynchronousSocketChannel socketChannel, int socketChannelTimeout) {
        Validate.notNull(socketChannel, "socketChannel cannot be null");
        this.socketChannel = socketChannel;
        this.socketChannelTimeout = socketChannelTimeout;
        inputStream = new SocketChannelInputStream();
        outputStream = new SocketChannelOutputStream();
    }

    AsynchronousSocketChannel getSocketChannel() {
        return socketChannel;
    }

    InputStream getInputStream() {
        return inputStream;
    }

    OutputStream getOutputStream() {
        return outputStream;
    }

    void write(Object obj) throws IOException {
        outputStream.write(ObjectFormat.toCrlf(obj, ObjectFormat.UTF_8));
    }

    void writeln(Object obj) throws IOException {
        write(obj);
        writeln();
    }

    void writeln() throws IOException {
        outputStream.write(ObjectFormat.CRLF.getBytes());
    }

    void writelnError(int statusCode, Exception e) {
        writelnError(HttpVersion.HTTP_1_1, statusCode, e);
    }

    void writelnError(ProtocolVersion protocolVersion, int statusCode, Exception e) {
        Validate.notNull(e, "Exception cannot be null");
        try {
            writeln(HttpUtils.toStatusLine(protocolVersion, statusCode, e.getMessage()));
        } catch (Exception ex) {
            logger.debug("Error on writing response error", ex);
        }
    }

    /**
     * Writes the HTTP response as it is.
     *
     * @param httpResponse The HTTP response.
     */
    void writeHttpResponse(final HttpResponse httpResponse) throws Exception {
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
    public void close() throws IOException {
        socketChannel.close();
    }

    boolean isOpen() {
        return socketChannel.isOpen();
    }

    private class SocketChannelInputStream extends InputStream {

        @Override
        public int read() {
            throw new NotImplementedException("Do not use it");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            try {
                return socketChannel.read(buffer).get(socketChannelTimeout, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());
            } catch (Exception e) {
                throw new IOException(e);
            }

        }

    }

    private class SocketChannelOutputStream extends OutputStream {

        @Override
        public void write(int b) {
            throw new NotImplementedException("Do not use it");
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            try {
                socketChannel.write(buffer).get(socketChannelTimeout, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

    }

}
