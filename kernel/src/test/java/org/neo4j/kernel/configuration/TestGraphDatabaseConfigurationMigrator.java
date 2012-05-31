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

package org.neo4j.kernel.configuration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.neo4j.helpers.collection.MapUtil.stringMap;

import org.junit.Test;
import org.neo4j.kernel.logging.StringLogger;

/**
 * Test configuration migration rules
 */
public class TestGraphDatabaseConfigurationMigrator
{
    @Test
    public void testNoMigration()
    {
        GraphDatabaseConfigurationMigrator migrator = new GraphDatabaseConfigurationMigrator();
        assertThat( migrator.apply( stringMap( "foo", "bar" ), StringLogger.SYSTEM), equalTo( stringMap( "foo", "bar" ) ) );
    }

    @Test
    public void testEnableOnlineBackup()
    {
        GraphDatabaseConfigurationMigrator migrator = new GraphDatabaseConfigurationMigrator();
        assertThat( migrator.apply( stringMap( "enable_online_backup", "true" ), StringLogger.SYSTEM ), equalTo( stringMap( "online_backup_enabled", "true", "online_backup_port", "6362" ) ) );
    }

    @Test
    public void testUdcEnabled()
    {
        GraphDatabaseConfigurationMigrator migrator = new GraphDatabaseConfigurationMigrator();
        assertThat( migrator.apply( stringMap( "neo4j.ext.udc.disable", "true" ), StringLogger.SYSTEM ), equalTo( stringMap( "neo4j.ext.udc.enabled", "false" ) ) );
        assertThat( migrator.apply( stringMap( "neo4j.ext.udc.disable", "false" ), StringLogger.SYSTEM ), equalTo( stringMap( "neo4j.ext.udc.enabled", "true" ) ) );
    }

    @Test
    public void testEnableRemoteShell()
    {
        GraphDatabaseConfigurationMigrator migrator = new GraphDatabaseConfigurationMigrator();
        assertThat( migrator.apply( stringMap( "enable_remote_shell", "true" ), StringLogger.SYSTEM ), equalTo( stringMap( "remote_shell_enabled", "true" ) ) );
        assertThat( migrator.apply( stringMap( "enable_remote_shell", "false" ), StringLogger.SYSTEM ), equalTo( stringMap( "remote_shell_enabled", "false" ) ) );
        assertThat( migrator.apply( stringMap( "enable_remote_shell", "port=1234" ), StringLogger.SYSTEM ), equalTo( stringMap( "remote_shell_enabled", "true","remote_shell_port","1234","remote_shell_read_only","false","remote_shell_name","shell" ) ) );
    }
}
