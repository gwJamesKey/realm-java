/*
 * Copyright 2014 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.processor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class Constants {
    public static final String REALM_PACKAGE_NAME = "io.realm";
    public static final String PROXY_SUFFIX = "RealmProxy";
    public static final String INTERFACE_SUFFIX = "RealmProxyInterface";
    public static final String INDENT = "    ";
    public static final String TABLE_PREFIX = "class_";
    public static final String DEFAULT_MODULE_CLASS_NAME = "DefaultRealmModule";
    static final String STATEMENT_EXCEPTION_ILLEGAL_NULL_VALUE =
            "throw new IllegalArgumentException(\"Trying to set non-nullable field '%s' to null.\")";
    static final String STATEMENT_EXCEPTION_NO_PRIMARY_KEY_IN_JSON =
            "throw new IllegalArgumentException(\"JSON object doesn't have the primary key field '%s'.\")";
    static final String STATEMENT_EXCEPTION_PRIMARY_KEY_CANNOT_BE_CHANGED =
            "throw new io.realm.exceptions.RealmException(\"Primary key field '%s' cannot be changed after object" +
                    " was created.\")";
    static final String STATEMENT_EXCEPTION_ILLEGAL_JSON_LOAD =
            "throw new io.realm.exceptions.RealmException(\"\\\"%s\\\" field \\\"%s\\\" cannot be loaded from json\")";


    public enum RealmFieldType {
        INTEGER("Long"),
        FLOAT("Float"),
        DOUBLE("Double"),
        BOOLEAN("Boolean"),
        STRING("String"),
        DATE("Date"),
        BINARY("BinaryByteArray"),
        OBJECT(null),
        LIST(null),
        BACKLINK(null);

        private final String javaType;
        RealmFieldType(String javaType) {
            this.javaType = javaType;
        }

        public String getJavaType() {
            return javaType;
        }

        @Override
        public String toString() {
            return "RealmFieldType." + super.toString();
        }
    }


    static final Map<String, RealmFieldType> JAVA_TO_REALM_TYPES;

    static {
        Map<String, RealmFieldType> m  = new HashMap<String, RealmFieldType>();
        m.put("byte", RealmFieldType.INTEGER);
        m.put("short", RealmFieldType.INTEGER);
        m.put("int", RealmFieldType.INTEGER);
        m.put("long", RealmFieldType.INTEGER);
        m.put("float", RealmFieldType.FLOAT);
        m.put("double", RealmFieldType.DOUBLE);
        m.put("boolean", RealmFieldType.BOOLEAN);
        m.put("java.lang.Byte", RealmFieldType.INTEGER);
        m.put("java.lang.Short", RealmFieldType.INTEGER);
        m.put("java.lang.Integer", RealmFieldType.INTEGER);
        m.put("java.lang.Long", RealmFieldType.INTEGER);
        m.put("java.lang.Float", RealmFieldType.FLOAT);
        m.put("java.lang.Double", RealmFieldType.DOUBLE);
        m.put("java.lang.Boolean", RealmFieldType.BOOLEAN);
        m.put("java.lang.String", RealmFieldType.STRING);
        m.put("java.util.Date", RealmFieldType.DATE);
        m.put("byte[]", RealmFieldType.BINARY);
        // TODO: add support for char and Char
        JAVA_TO_REALM_TYPES = Collections.unmodifiableMap(m);
    }

    static final Map<String, String> JAVA_TO_FIELD_SETTER;

    static {
        Map<String, String> m  = new HashMap<String, String>();
        m.put("byte", "setByte");
        m.put("short", "setShort");
        m.put("int", "setInt");
        m.put("long", "setLong");
        m.put("float", "setFloat");
        m.put("double", "setDouble");
        m.put("boolean", "setBoolean");
        m.put("java.lang.Byte", "set");
        m.put("java.lang.Short", "set");
        m.put("java.lang.Integer", "set");
        m.put("java.lang.Long", "set");
        m.put("java.lang.Float", "set");
        m.put("java.lang.Double", "set");
        m.put("java.lang.Boolean", "set");
        m.put("java.lang.String", "set");
        m.put("java.util.Date", "set");
        m.put("byte[]", "set");
        JAVA_TO_FIELD_SETTER = Collections.unmodifiableMap(m);
    }
}
