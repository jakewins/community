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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.test.TargetDirectory;

public class SeparatedStoreFilesIT
{

    TargetDirectory target = TargetDirectory.forTest( SeparatedStoreFilesIT.class );


    @Before
    public void clean() throws IOException
    {
        target.cleanup();
    }

    @Test
    public void shouldBePossibleToConfigureIndividualStoreFileLocations()
    {

        // Given
        File nodeStoreFile = target.file( "nodestore" );
        File relStoreFile = target.file( "relstore" );
        File propStoreFile = target.file( "properties" );
        File arrayStoreFile = target.file( "arrays" );
        File stringStoreFile = target.file( "strings" );

        GraphDatabaseBuilder builder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( target.directory( "db" ).getAbsolutePath() )
                .setConfig(GraphDatabaseSettings.nodestore_location, nodeStoreFile.getAbsolutePath() )
                .setConfig( GraphDatabaseSettings.relationshipstore_location, relStoreFile.getAbsolutePath() )
                .setConfig( GraphDatabaseSettings.propertystore_location, propStoreFile.getAbsolutePath() )
                .setConfig( GraphDatabaseSettings.propertystore_arrays_location, arrayStoreFile.getAbsolutePath() )
                .setConfig( GraphDatabaseSettings.propertystore_strings_location, stringStoreFile.getAbsolutePath() );

        // When I create a database
        GraphDatabaseService db = builder.newGraphDatabase();
        db.shutdown();

        // And I restart it
        builder.newGraphDatabase().shutdown();

        // Then
        assertThat("Node store should have been created in separate location.", nodeStoreFile.exists(), is(true));
        assertThat("Relationship store should have been created in separate location.", relStoreFile.exists(), is(true));
        assertThat("Property store should have been created in separate location.", propStoreFile.exists(), is(true));
        assertThat("Array store should have been created in separate location.", arrayStoreFile.exists(), is(true));
        assertThat("String store should have been created in separate location.", stringStoreFile.exists(), is(true));

    }

    @Test
    public void shouldCreateStoresAtCorrectDefaultLocations()
    {

        // Given
        File nodeStoreFile = target.file( "db/neostore.nodestore.db" );
        File relStoreFile = target.file( "db/neostore.relationshipstore.db" );
        File propStoreFile = target.file( "db/neostore.propertystore.db" );
        File arrayStoreFile = target.file( "db/neostore.propertystore.db.arrays" );
        File stringStoreFile = target.file( "db/neostore.propertystore.db.strings" );

        GraphDatabaseBuilder builder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( target.directory( "db" ).getAbsolutePath() );

        // When
        GraphDatabaseService db = builder.newGraphDatabase();
        db.shutdown();

        // Then
        assertThat("Node store should have been created in separate location.", nodeStoreFile.exists(), is(true));
        assertThat("Relationship store should have been created in separate location.", relStoreFile.exists(), is(true));
        assertThat("Property store should have been created in separate location.", propStoreFile.exists(), is(true));
        assertThat("Array store should have been created in separate location.", arrayStoreFile.exists(), is(true));
        assertThat("String store should have been created in separate location.", stringStoreFile.exists(), is(true));

    }


}
