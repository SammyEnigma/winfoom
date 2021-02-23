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

package org.kpax.winfoom.util;

import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Map;

public class BeanUtils {
    private static final Logger logger = LoggerFactory.getLogger(BeanUtils.class);

    public static void copyNonNullProperties(Iterator<String> fieldNamesItr, Object src, Object dest)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Map<String, Object> objectMap = PropertyUtils.describe(src);
        for (; fieldNamesItr.hasNext();) {
            String fieldName = fieldNamesItr.next();
            if (objectMap.containsKey(fieldName)) {
                Object fieldValue = objectMap.get(fieldName);
                logger.debug("Set property: {}={}", fieldName, fieldValue);
                PropertyUtils.setProperty(dest, fieldName, fieldValue);
            } else {
                throw new IllegalArgumentException("The source object does not contain the field [" + fieldName + "] ");
            }
        }
    }

}
