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

package org.neo4j.graphdb.factory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.HashMap;

import org.junit.Test;
import org.neo4j.kernel.configuration.Config;

public class TestGraphDatabaseSetting
{
    @Test
    public void testStringSetting()
    {
        GraphDatabaseSetting.StringSetting stringSetting = new GraphDatabaseSetting.StringSetting( "foo_bar", GraphDatabaseSetting.ANY, "Must be a valid foo bar" );
        
        assertThat( stringSetting.name(), equalTo( "foo_bar" ) );

        stringSetting.validate( "test" );

        try
        {
            stringSetting.validate( null );
            fail( "null should not be allowed" );
        }
        catch( IllegalArgumentException e )
        {
            // Ok
        }

        try
        {
            stringSetting.validate( "" );
            fail( "empty string should not be allowed" );
        }
        catch( IllegalArgumentException e )
        {
            // Ok
        }
    }

    @Test
    public void testIntegerSetting()
    {
        GraphDatabaseSetting.IntegerSetting integerSetting = new GraphDatabaseSetting.IntegerSetting( "foo_bar", "Must be a valid integer", 3, 10 );

        assertThat( integerSetting.name(), equalTo( "foo_bar" ) );

        integerSetting.validate( "5" );

        integerSetting.validate( "3" );

        integerSetting.validate( "10" );

        try
        {
            integerSetting.validate( "2" );
            fail( "too low number should not be allowed" );
        }
        catch( IllegalArgumentException e )
        {
            // Ok
            assertThat( e.getMessage(), equalTo( "Minimum allowed value is:3" ) );
        }

        try
        {
            integerSetting.validate( "11" );
            fail( "too high number should not be allowed" );
        }
        catch( IllegalArgumentException e )
        {
            // Ok
        }
    }

    @Test
    public void testOptionsSetting()
    {
        GraphDatabaseSetting.OptionsSetting optionsSetting = new GraphDatabaseSetting.OptionsSetting( "foo_bar", "option1", "option2", "option3" );

        assertThat( optionsSetting.name(), equalTo( "foo_bar" ) );

        optionsSetting.validate( "option1" );

        optionsSetting.validate( "option2" );

        try
        {
            optionsSetting.validate( "option4" );
            fail( "invalid option should not be allowed" );
        }
        catch( IllegalArgumentException e )
        {
            // Ok
            assertThat( e.getMessage(), equalTo( "Value 'option4' is not valid. Valid options are:[option1, option2, option3]" ) );
        }
    }
    
    @Test
    public void testFileSetting() 
    {
        GraphDatabaseSetting.FileSetting fileSetting = new GraphDatabaseSetting.FileSetting("myfile");
        assertThat( fileSetting.name(), equalTo( "myfile" ) );
        
        fileSetting.validate("/some/path");
        
        try
        {
            fileSetting.validate( null );
            fail( "null paths should not be allowed" );
        }
        catch( IllegalArgumentException e )
        {
            // Ok
            assertThat( e.getMessage(), equalTo( "Must be a valid file path." ) );
        }
    }

    @Test
    public void testRelativeFileSetting() 
    {
        GraphDatabaseSetting.DirectorySetting baseDir = new GraphDatabaseSetting.DirectorySetting("myDirectory");
        GraphDatabaseSetting.FileSetting fileSetting = new GraphDatabaseSetting.FileSetting("myfile", baseDir, true, true);
        
        Config config = new Config(new HashMap<String,String>(){{put("myDirectory","/home/jake");}});
        
        // Relative paths
        assertThat(fileSetting.valueOf("baa", config), equalTo("/home/jake/baa"));
        
        // Absolute paths
        assertThat(fileSetting.valueOf("/baa", config), equalTo("/baa"));
        
        // Path with incorrect directory separator
        assertThat(fileSetting.valueOf("\\baa\\boo", config), equalTo("/baa/boo"));
    }

    @Test
    public void testURISetting() 
    {
        GraphDatabaseSetting.URISetting setting = new GraphDatabaseSetting.URISetting("myfile", true);
        
        Config config = mock(Config.class);
        
        assertThat(setting.valueOf("/baa/boo", config).toString(), equalTo("/baa/boo"));
        
        // Strip trailing slash
        assertThat(setting.valueOf("/baa/", config).toString(), equalTo("/baa"));
        
    }
}
