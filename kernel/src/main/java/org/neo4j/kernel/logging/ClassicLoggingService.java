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

package org.neo4j.kernel.logging;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

/**
 * Implements the old-style logging with just one logger regardless of name.
 */
public class ClassicLoggingService
    extends LifecycleAdapter
    implements Logging
{
    public static final class Configuration
    {
        public static final GraphDatabaseSetting.DirectorySetting store_dir = GraphDatabaseSettings.store_dir;

        public static final GraphDatabaseSetting.IntegerSetting threshold_for_logging_rotation = GraphDatabaseSettings.threshold_for_logging_rotation;
    }

    public static final String DEFAULT_NAME = "messages.log";

    protected StringLogger stringLogger;

    public ClassicLoggingService()
    {
        stringLogger = StringLogger.DEV_NULL;
    }

    public ClassicLoggingService(OutputStream out)
    {
        stringLogger = new FileStringLogger( new PrintWriter( out));
    }

    public ClassicLoggingService(Config config)
    {
        stringLogger = new FileStringLogger( new File( config.get( Configuration.store_dir ), DEFAULT_NAME ).getAbsolutePath(),
                                              config.get( Configuration.threshold_for_logging_rotation ) );
    }

    @Override
    public void init()
        throws Throwable
    {
    }

    @Override
    public void shutdown()
        throws Throwable
    {
        stringLogger.close();
    }

    @Override
    public StringLogger getLogger( String name )
    {
        return stringLogger;
    }
}
