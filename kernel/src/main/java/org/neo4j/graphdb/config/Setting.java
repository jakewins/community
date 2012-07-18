/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphdb.config;

import java.util.Locale;

/**
 * This interface is available only for use, not for implementing. Implementing this interface is not expected, and
 * backwards compatibility is not guaranteed for implementors.
 */
public interface Setting<T>
{

    public interface DefaultValue
    {
        String getDefaultValue();
    }

    public String name();

    public void validate(String value)
            throws IllegalArgumentException;

    /**
     * Create a typed value from a raw string value. This is to be called
     * when a value is fetched from configuration.
     *
     * @param rawValue The raw string value stored in configuration
     * @param config The config instance, allows having config values that depend on each other.
     * @return
     */
    public T valueOf(String rawValue, Config config);

}
