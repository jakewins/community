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
package org.neo4j.kernel.impl.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.impl.AbstractNeo4jTestCase;
import org.neo4j.kernel.impl.core.Caches;

import static org.junit.Assert.*;

public class TestCacheTypes extends AbstractNeo4jTestCase
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private GraphDatabaseAPI newDb( String cacheType )
    {
        return (GraphDatabaseAPI) new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( temp.getRoot().getAbsolutePath() ).setConfig( GraphDatabaseSettings.cache_type.name(), cacheType ).newGraphDatabase();
    }

    @Test
    public void testDefaultCache()
    {
        GraphDatabaseAPI db = newDb( null );
        assertEquals( SoftCacheProvider.NAME, getCacheName( db ) );
        db.shutdown();
    }

    @Test
    public void testWeakRefCache()
    {
        GraphDatabaseAPI db = newDb( WeakCacheProvider.NAME );
        assertEquals( WeakCacheProvider.NAME, getCacheName( db ) );
        db.shutdown();
    }

    @Test
    public void testSoftRefCache()
    {
        GraphDatabaseAPI db = newDb( SoftCacheProvider.NAME );
        assertEquals( SoftCacheProvider.NAME, getCacheName( db ) );
        db.shutdown();
    }

    @Test
    public void testNoCache()
    {
        GraphDatabaseAPI db = newDb( NoCacheProvider.NAME );
        assertEquals( NoCacheProvider.NAME, getCacheName( db ) );
        db.shutdown();
    }

    @Test
    public void testStrongCache()
    {
        GraphDatabaseAPI db = newDb( StrongCacheProvider.NAME );
        assertEquals( StrongCacheProvider.NAME, getCacheName( db ) );
        db.shutdown();
    }
    
    @Test
    public void testInvalidCache()
    {
        // invalid cache type should fail
        GraphDatabaseAPI db = null;
        try
        {
            db = newDb( "whatever" );
            fail( "Wrong cache type should not be allowed" );
        }
        catch( Exception e )
        {
            // Ok
        }
    }

    private String getCacheName( GraphDatabaseAPI db )
    {
        return db.getDependencyResolver().resolveDependency( Caches.class ).getCurrentCacheProvider().getName();
    }
}
