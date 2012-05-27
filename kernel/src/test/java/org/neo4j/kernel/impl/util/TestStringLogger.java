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
package org.neo4j.kernel.impl.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.ConfigurationDefaults;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.logging.ClassicLoggingService;
import org.neo4j.kernel.logging.Loggers;
import org.neo4j.kernel.logging.StringLogger;

public class TestStringLogger
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void makeSureLogsAreRotated() throws Exception
    {
        File logFile = new File( temp.getRoot(), ClassicLoggingService.DEFAULT_NAME );
        File oldFile = new File( temp.getRoot(), ClassicLoggingService.DEFAULT_NAME + ".1" );
        File oldestFile = new File( temp.getRoot(), ClassicLoggingService.DEFAULT_NAME + ".2" );

        LifeSupport life = new LifeSupport();
        Map<String,String> config = MapUtil.stringMap( ClassicLoggingService.Configuration.store_dir.name(), temp.getRoot().getAbsolutePath(), ClassicLoggingService.Configuration.threshold_for_logging_rotation.name(), ""+(200*1024) );
        ClassicLoggingService logging = life.add( new ClassicLoggingService(new Config( new ConfigurationDefaults(ClassicLoggingService.Configuration.class).apply( config ))));
        life.start();

        StringLogger logger = logging.getLogger( Loggers.NEO4J );
        assertFalse( oldFile.exists() );
        int counter = 0;
        String prefix = "Bogus message ";
        
        // First rotation
        while ( !oldFile.exists() )
        {
            logger.info( prefix + counter++, true );
        }
        int mark1 = counter-1;
        logger.info( prefix + counter++, true );
        assertTrue( firstLineOfFile( oldFile ).contains( prefix + "0" ) );
        assertTrue( lastLineOfFile( oldFile ).contains( prefix + mark1 ) );
        assertTrue( firstLineOfFile( logFile ).contains( prefix + (counter-1) ) );
        
        // Second rotation
        while ( !oldestFile.exists() )
        {
            logger.info( prefix + counter++, true );
        }
        int mark2 = counter-1;
        logger.info( prefix + counter++, true );
        assertTrue( firstLineOfFile( oldestFile ).contains( prefix + "0" ) );
        assertTrue( lastLineOfFile( oldestFile ).contains( prefix + mark1 ) );
        assertTrue( firstLineOfFile( oldFile ).contains( prefix + (mark1+1) ) );
        assertTrue( lastLineOfFile( oldFile ).contains( prefix + mark2 ) );
        assertTrue( firstLineOfFile( logFile ).contains( prefix + (counter-1) ) );
        
        // Third rotation, assert .2 file is now what used to be .1 used to be and
        // .3 doesn't exist
        long previousSize = 0;
        while ( true )
        {
            logger.info( prefix + counter++, true );
            if ( logFile.length() < previousSize ) break;
            previousSize = logFile.length();
        }
        assertFalse( new File( temp.getRoot(), ClassicLoggingService.DEFAULT_NAME + ".3" ).exists() );
        assertTrue( firstLineOfFile( oldestFile ).contains( prefix + (mark1+1) ) );
        assertTrue( lastLineOfFile( oldestFile ).contains( prefix + mark2 ) );

        life.shutdown();
    }

    private String firstLineOfFile( File file ) throws Exception
    {
        BufferedReader reader = new BufferedReader( new FileReader( file ) );
        String result = reader.readLine();
        reader.close();
        return result;
    }
    
    private String lastLineOfFile( File file ) throws Exception
    {
        BufferedReader reader = new BufferedReader( new FileReader( file ) );
        String line = null;
        String result = null;
        while ( (line = reader.readLine()) != null )
        {
            result = line;
        }
        reader.close();
        return result;
    }
}
