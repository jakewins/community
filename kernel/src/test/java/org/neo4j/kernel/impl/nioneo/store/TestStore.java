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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.DefaultFileSystemAbstraction;
import org.neo4j.kernel.DefaultIdGeneratorFactory;
import org.neo4j.kernel.IdGeneratorFactory;
import org.neo4j.kernel.IdType;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.ConfigurationDefaults;
import org.neo4j.kernel.impl.AbstractNeo4jTestCase;
import org.neo4j.kernel.logging.StringLogger;

public class TestStore
{
    public static IdGeneratorFactory ID_GENERATOR_FACTORY =
            new DefaultIdGeneratorFactory();
    public static FileSystemAbstraction FILE_SYSTEM =
            new DefaultFileSystemAbstraction();
    
    private String path()
    {
        String path = AbstractNeo4jTestCase.getStorePath( "teststore" );
        new File( path ).mkdirs();
        return path;
    }
    
    private String file( String name )
    {
        return path() + File.separator + name;
    }
    
    private String storeFile()
    {
        return file( "testStore.db" );
    }
    
    private String storeIdFile()
    {
        return file( "testStore.db.id" );
    }
    
    @Test
    public void testCreateStore() throws Throwable
    {
        try
        {
            try
            {
                Store store = Store.createStore( null );
                store.start();
                fail( "Null fileName should throw exception" );
            }
            catch ( IllegalArgumentException e )
            { // good
            }
            Store store = Store.createStore( storeFile() );
            store.start();
            store.stop();
            try
            {
                store.createStorage();
                fail( "Creating existing store should throw exception" );
            }
            catch ( IllegalStateException e )
            { // good
            }
            store.shutdown();
        }
        finally
        {
            deleteBothFiles();
        }
    }

    private void deleteBothFiles()
    {
        File file = new File( storeFile() );
        if ( file.exists() )
        {
            assertTrue( file.delete() );
        }
        file = new File( storeIdFile() );
        if ( file.exists() )
        {
            assertTrue( file.delete() );
        }
    }

    @Test
    public void testStickyStore() throws Throwable
    {
        try
        {
            Store createStore = Store.createStore( storeFile() );
            createStore.start();
            createStore.stop();
            
            java.nio.channels.FileChannel fileChannel = new java.io.RandomAccessFile(
                storeFile(), "rw" ).getChannel();
            fileChannel.truncate( fileChannel.size() - 2 );
            fileChannel.close();
            Store store = new Store( storeFile() );
            store.start();
            store.makeStoreOk();
            store.shutdown();
        }
        finally
        {
            deleteBothFiles();
        }
    }

    @Test
    public void testStop() throws Throwable
    {
        try
        {
            Store store = Store.createStore( storeFile() );
            store.start();
            store.stop();
        }
        finally
        {
            deleteBothFiles();
        }
    }

    private static class Store extends AbstractStore
    {
        public static final String TYPE_DESCRIPTOR = "TestVersion";
        private static final int RECORD_SIZE = 1;

        public Store( String fileName ) throws IOException
        {
            super( StringLogger.DEV_NULL, new Config( new ConfigurationDefaults(GraphDatabaseSettings.class ).apply( MapUtil.stringMap(
                                "store_dir", "target/var/teststore" ) )), IdType.NODE, ID_GENERATOR_FACTORY, FILE_SYSTEM);
            this.setStorageFileName(fileName);
        }

        public int getRecordSize()
        {
            return RECORD_SIZE;
        }

        public String getTypeDescriptor()
        {
            return TYPE_DESCRIPTOR;
        }

        public static Store createStore( String fileName) throws IOException
        {
            return new Store( fileName );
        }

        protected void rebuildIdGenerator()
        {
        }

        @Override
        public List<WindowPoolStats> getAllWindowPoolStats()
        {
            // TODO Auto-generated method stub
            return null;
        }
    }
}