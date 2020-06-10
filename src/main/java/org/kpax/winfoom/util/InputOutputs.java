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

package org.kpax.winfoom.util;

import org.apache.commons.configuration2.Configuration;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author Eugen Covaci
 */
public final class InputOutputs {

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private static final Logger logger = LoggerFactory.getLogger(InputOutputs.class);

    private InputOutputs() {
    }

    /**
     * Check for available data.
     *
     * @param inputBuffer The input buffer.
     * @return <code>false</code> iff EOF has been reached.
     */
    public static boolean isAvailable(SessionInputBufferImpl inputBuffer) {
        try {
            return inputBuffer.hasBufferedData() || inputBuffer.fillBuffer() > -1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Close an <code>AutoCloseable</code>, debug the possible error.
     *
     * @param closeable The {@link AutoCloseable} instance.
     */
    public static void close(AutoCloseable closeable) {
        if (closeable != null) {
            logger.debug("Close {}", closeable.getClass());
            try {
                closeable.close();
            } catch (Exception e) {
                logger.debug("Fail to close: " + closeable.getClass().getName(), e);
            }
        }

    }

    public static String generateCacheFilename() {
        return new StringBuffer()
                .append(System.nanoTime())
                .append("-")
                .append((int) (Math.random() * 100)).toString();
    }


    /**
     * Transfer bytes between two sources.
     *
     * @param executorService    The executor service for async support.
     * @param firstInputSource   The input of the first source.
     * @param firstOutputSource  The output of the first source.
     * @param secondInputSource  The input of the second source.
     * @param secondOutputSource The output of the second source.
     */
    public static void duplex(ExecutorService executorService,
                              InputStream firstInputSource, OutputStream firstOutputSource,
                              InputStream secondInputSource, OutputStream secondOutputSource) {

        logger.debug("Start full duplex communication");
        Future<?> secondToFirst = executorService.submit(
                () -> secondInputSource.transferTo(firstOutputSource));
        try {
            firstInputSource.transferTo(secondOutputSource);
            if (!secondToFirst.isDone()) {

                // Wait for the async transfer to finish
                try {
                    secondToFirst.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof SocketTimeoutException) {
                        logger.debug("Second to first transfer cancelled due to timeout");
                    } else {
                        logger.debug("Error on executing second to first transfer", e.getCause());
                    }
                } catch (InterruptedException e) {
                    logger.debug("Transfer from second to first interrupted", e);
                } catch (CancellationException e) {
                    logger.debug("Transfer from second to first cancelled", e);
                }
            }
        } catch (Exception e) {
            secondToFirst.cancel(true);
            if (e instanceof SocketTimeoutException) {
                logger.debug("Second to first transfer cancelled due to timeout");
            } else {
                logger.debug("Error on executing second to first transfer", e);
            }
        }
        logger.debug("End full duplex communication");
    }

    public static boolean isIncluded(Properties who, Properties where) {
        Assert.notNull(who, "who cannot be null");
        Assert.notNull(where, "where cannot be null");
        for (String key : who.stringPropertyNames()) {
            if (where.getProperty(key) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * If it is a regular file, delete it. If it is a directory, delete recursively.
     *
     * @param file the regular file or directory to be deleted.
     * @return {@code true} if the deletion takes place.
     */
    public static boolean deleteFile(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteFile(f);
            }
        }
        return file.delete();
    }

    /**
     * Delete the directory's content.
     *
     * @param directory the {@link File} to be emptied
     * @return {@code true} iff all the contained files were deleted.
     */
    public static boolean emptyDirectory(File directory) {
        Assert.isTrue(directory.isDirectory(), "Not a directory");
        File[] files = directory.listFiles();
        for (File file : Objects.requireNonNull(files)) {
            deleteFile(file);
        }
        return files.length == 0;
    }

    /**
     * Check whether a {@link Configuration} instance is compatible with the current {@link ProxyConfig} structure.
     *
     * @param proxyConfig the {@link Configuration} instance
     * @return {@code true} if each {@link Configuration} key is among
     * the {@link ProxyConfig}'s {@link Value} annotated fields.
     */
    public static boolean isProxyConfigCompatible(Configuration proxyConfig) {
        List<String> keys = new ArrayList<>();
        for (Field field : ProxyConfig.class.getDeclaredFields()) {
            Value valueAnnotation = field.getAnnotation(Value.class);
            if (valueAnnotation != null) {
                keys.add(valueAnnotation.value().replaceAll("[${}]", "").split(":")[0]);
            }
        }

        for (Iterator<String> itr = proxyConfig.getKeys(); itr.hasNext(); ) {
            String key = itr.next();
            if (!keys.contains(key)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Move the file designated by a {@link Path} to a backup location.
     *
     * @param path        the file's {@link Path}
     * @param withWarning if {@code true} an warning will popup
     * @param options     the moving {@link CopyOption}s
     * @return the new {@link Path} or {@code null} if the original file does not exist
     * @throws IOException
     */
    public static Path backupFile(Path path, boolean withWarning, CopyOption... options) throws IOException {
        Assert.notNull(path, "path cannot be null");
        if (Files.exists(path)) {
            Path appHomePath = Paths.get(System.getProperty("user.home"), SystemConfig.APP_HOME_DIR_NAME);
            Path backupDirPath = appHomePath.resolve(SystemConfig.BACKUP_DIR_NAME);
            if (!Files.exists(backupDirPath)) {
                Files.createDirectories(backupDirPath);
            }
            if (withWarning) {
                SwingUtils.showWarningMessage(null,
                        String.format("The %s file found belongs to a different application version<br>" +
                                        "and is not compatible with the current version!<br>" +
                                        "The existent one will be moved to:<br>" +
                                        "%s directory.",
                                path.getFileName(),
                                backupDirPath.toString()));
            }
            logger.info("Move the file {} to: {} directory", path, backupDirPath);
            return Files.move(path, backupDirPath.resolve(path.getFileName()), options);
        }
        logger.info("Cannot move file {} because it does not exist", path);
        return null;
    }

}
