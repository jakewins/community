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

package org.neo4j.kernel.impl.nioneo.store;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.DefaultFileSystemAbstraction;
import org.neo4j.kernel.DefaultIdGeneratorFactory;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.ConfigurationDefaults;
import org.neo4j.kernel.impl.storemigration.StoreMigrator;
import org.neo4j.kernel.impl.util.FileUtils;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.test.TargetDirectory;

import static org.junit.Assert.*;
import static org.junit.internal.matchers.StringContains.*;

public class StoreVersionTest
{

    private final TargetDirectory target = TargetDirectory.forTest( StoreVersionTest.class );
    @Rule
    public final TargetDirectory.TestDirectory testDir = target.testDirectory();

    @Test
    public void allStoresShouldHaveTheCurrentVersionIdentifier() throws IOException
    {
        // Given
        String storeFileName = new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getAbsolutePath();

        Map<String,String> config = new HashMap<String, String>();
        config.put( GraphDatabaseSettings.neo_store.name(), storeFileName );
        FileSystemAbstraction fileSystem = new DefaultFileSystemAbstraction();
        StoreFactory sf = new StoreFactory(new Config(config, GraphDatabaseSettings.class), new DefaultIdGeneratorFactory(), fileSystem, null, StringLogger.SYSTEM, null);

        // When
        NeoStore neoStore = sf.createNeoStore();

        // Then
        CommonAbstractStore[] stores = {
                neoStore.getNodeStore(),
                neoStore.getRelationshipStore(),
                neoStore.getRelationshipTypeStore(),
                neoStore.getPropertyStore(),
                neoStore.getPropertyStore().getIndexStore()
        };

        for ( CommonAbstractStore store : stores )
        {
            assertThat( store.getTypeAndVersionDescriptor(), containsString( CommonAbstractStore.ALL_STORES_VERSION ) );
        }
        neoStore.close();
    }

    // TODO: Please add a comment as to why a test is ignored, and when it will be un-ignored.
    @Test
    @Ignore
    public void shouldFailToCreateAStoreContainingOldVersionNumber() throws IOException
    {
        URL legacyStoreResource = StoreMigrator.class.getResource( "legacystore/exampledb/neostore.nodestore.db" );
        File workingFile = new File( testDir.directory(), "neostore.nodestore.db" );
        FileUtils.copyFile( new File( legacyStoreResource.getFile() ), workingFile );

        FileSystemAbstraction fileSystem = new DefaultFileSystemAbstraction();
        Config config = new Config( new ConfigurationDefaults(GraphDatabaseSettings.class).apply( new HashMap<String,String>() ));
        
        try {
            new NodeStore( workingFile.getPath(), config, new DefaultIdGeneratorFactory(), fileSystem, StringLogger.SYSTEM );
            fail( "Should have thrown exception" );
        } catch ( NotCurrentStoreVersionException e ) {
            //expected
        }
    }

    @Test
    public void neoStoreHasCorrectStoreVersionField() throws IOException
    {
        String storeFileName = new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getPath();

        Map<String,String> config = new HashMap<String, String>();
        config.put( "neo_store", storeFileName );
        FileSystemAbstraction fileSystem = new DefaultFileSystemAbstraction();
        StoreFactory sf = new StoreFactory(new Config(new ConfigurationDefaults(GraphDatabaseSettings.class ).apply( config )), new DefaultIdGeneratorFactory(), fileSystem, null, StringLogger.SYSTEM, null);
        NeoStore neoStore = sf.createNeoStore();
        // The first checks the instance method, the other the public one
        assertEquals( CommonAbstractStore.ALL_STORES_VERSION,
                NeoStore.versionLongToString( neoStore.getStoreVersion() ) );
        assertEquals( CommonAbstractStore.ALL_STORES_VERSION,
                NeoStore.versionLongToString( NeoStore.getStoreVersion( fileSystem, storeFileName ) ) );
    }

    @Test
    public void testProperEncodingDecodingOfVersionString()
    {
        String[] toTest = new String[] { "123", "foo", "0.9.9", "v0.A.0",
                "bar", "chris", "1234567" };
        for ( String string : toTest )
        {
            assertEquals(
                    string,
                    NeoStore.versionLongToString( NeoStore.versionStringToLong( string ) ) );
        }
    }
}
