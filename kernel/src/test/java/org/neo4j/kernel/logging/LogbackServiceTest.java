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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.ConfigurationDefaults;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.slf4j.Logger;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SimpleSocketServer;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Test of LogbackService
 */
public class LogbackServiceTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testLogging()
        throws IOException
    {
        String loggingPath = folder.getRoot().getAbsolutePath();
        Config config = new Config(new ConfigurationDefaults( GraphDatabaseSettings.class).apply( MapUtil.stringMap( AbstractGraphDatabase.Configuration.store_dir.name(), loggingPath ) ));

        LifeSupport life = new LifeSupport();
        try
        {
            Logging logging = life.add( new LogbackService(config));
            life.start();

            logging.getLogger( "foo" ).logMessage( "TEST" );
            logging.getLogger( "neo4j.test" ).logMessage( "TEST" );
        }
        finally
        {
            life.shutdown();
        }

        File logFile = new File( folder.getRoot(), "messages.log" );
        assertTrue( logFile.exists() );
        BufferedReader reader = new BufferedReader( new FileReader( logFile ) );
        try
        {
            String logMessage = reader.readLine();
            assertTrue( logMessage.endsWith( "TEST" ) );
        }
        finally
        {
            reader.close();
        }
    }

    @Test
    public void testRemoteLogging() throws Exception
    {
        final List<String> logMessages = new ArrayList<String>(  );
        LoggerContext context = new LoggerContext();
        AppenderBase<ILoggingEvent> newAppender = new AppenderBase<ILoggingEvent>()
        {
            @Override
            protected void append( ILoggingEvent eventObject )
            {
                logMessages.add( eventObject.getFormattedMessage() );
            }
        };
        newAppender.setContext( context );
        newAppender.start();
        context.getLogger( Logger.ROOT_LOGGER_NAME ).addAppender( newAppender );

        SimpleSocketServer server = new SimpleSocketServer( context, 4560 );
        server.start();

        String loggingPath = folder.getRoot().getAbsolutePath();
        Map<String,String> parameters = MapUtil.stringMap( AbstractGraphDatabase.Configuration.store_dir.name(), loggingPath,
                                                           GraphDatabaseSettings.remote_logging_enabled.name(), GraphDatabaseSetting.TRUE);
        Config config = new Config(new ConfigurationDefaults( GraphDatabaseSettings.class).apply( parameters ));

        LifeSupport life = new LifeSupport();
        try
        {
            Logging logging = life.add( new LogbackService(config));
            life.start();

            logging.getLogger( "foo" ).logMessage( "TEST" );
            logging.getLogger( "neo4j" ).logMessage( "TEST" );
        }
        finally
        {
            // Wait for a reply for max 10 seconds
            long startTime = System.currentTimeMillis();
            while(logMessages.size() <= 0 && startTime + 1000 * 10 > System.currentTimeMillis()) {
                Thread.sleep(100);
            }
            
            life.shutdown();
            server.close();
        }
        
        assertThat( logMessages.get( 0 ), equalTo( "TEST" ) );
    }
}
