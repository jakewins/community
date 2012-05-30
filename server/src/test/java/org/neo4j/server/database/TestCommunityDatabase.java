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
package org.neo4j.server.database;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.neo4j.server.ServerTestUtils.createTempDir;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.lifecycle.LifecycleException;
import org.neo4j.kernel.logging.BufferingLogger;
import org.neo4j.server.configuration.ServerConfig;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.shell.ShellException;
import org.neo4j.shell.ShellLobby;
import org.neo4j.shell.ShellSettings;

public class TestCommunityDatabase
{
    private File databaseDirectory;
    private Database theDatabase;
    private boolean deletionFailureOk;
    private BufferingLogger log;

    @Before
    public void setup() throws Exception
    {
        databaseDirectory = createTempDir();
        Config conf = ServerConfig.fromMap(new HashMap<String,String>());
        conf.set(ServerSettings.database_location, databaseDirectory.getAbsolutePath());
        
        this.log = new BufferingLogger();
        theDatabase = new CommunityDatabase(conf, log, null);
    }

    @After
    public void shutdownDatabase() throws Throwable
    {
        this.theDatabase.shutdown();

        try
        {
            FileUtils.forceDelete( databaseDirectory );
        }
        catch ( IOException e )
        {
            // TODO Removed this when EmbeddedGraphDatabase startup failures
            // closes its
            // files properly.
            if ( !deletionFailureOk )
            {
                throw e;
            }
        }
    }

    @Test
    public void shouldLogOnSuccessfulStartup() throws Throwable
    {
        theDatabase.start();
        assertThat( log.toString(), containsString( "Successfully started database" ) );
    }

    @Test
    public void shouldShutdownCleanly() throws Throwable
    {
        theDatabase.start();
        theDatabase.stop();

        assertThat( log.toString(), containsString( "Successfully shutdown database" ) );
    }

    @Test( expected = LifecycleException.class )
    public void shouldComplainIfDatabaseLocationIsAlreadyInUse() throws Throwable
    {
        deletionFailureOk = true;
        Config conf = ServerConfig.fromMap(new HashMap<String,String>());
        conf.set(ServerSettings.database_location, databaseDirectory.getAbsolutePath());
        
        this.log = new BufferingLogger();
        Database other = new CommunityDatabase(conf, log, null);
        other.start();
    }

    @Test
    public void connectWithShellOnDefaultPortWhenNoShellConfigSupplied() throws Exception
    {
        ShellLobby.newClient()
                .shutdown();
    }

    @Test
    public void shouldBeAbleToOverrideShellConfig() throws Throwable
    {
        int customPort = findFreeShellPortToUse( 8881 );
        File tempDir = createTempDir();
        
        Config conf = ServerConfig.fromMap(new HashMap<String,String>());
        conf.set(ServerSettings.database_location, databaseDirectory.getAbsolutePath());
        conf.set(ShellSettings.remote_shell_enabled, GraphDatabaseSetting.TRUE );
        conf.set(ShellSettings.remote_shell_port, ""+customPort );
        
        this.log = new BufferingLogger();
        
        Database otherDb = new CommunityDatabase(conf, log, null );
        otherDb.start();

        // Try to connect with a shell client to that custom port.
        // Throws exception if unable to connect
        ShellLobby.newClient( customPort )
                .shutdown();

        otherDb.shutdown();
        FileUtils.forceDelete( tempDir );
    }

    private int findFreeShellPortToUse( int startingPort )
    {
        // Make sure there's no other random stuff on that port
        while ( true )
        {
            try
            {
                ShellLobby.newClient( startingPort )
                        .shutdown();
                startingPort++;
            }
            catch ( ShellException e )
            { // Good
                return startingPort;
            }
        }
    }
}
