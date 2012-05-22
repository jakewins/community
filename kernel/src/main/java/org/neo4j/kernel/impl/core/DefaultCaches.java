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
package org.neo4j.kernel.impl.core;

import static org.neo4j.helpers.collection.Iterables.cast;
import static org.neo4j.helpers.collection.Iterables.iterable;

import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.cache.Cache;
import org.neo4j.kernel.impl.cache.CacheProvider;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

public class DefaultCaches extends LifecycleAdapter implements Caches
{
    private Cache<NodeImpl> node;
    private Cache<RelationshipImpl> relationship;
    protected CacheProvider cacheProvider;

    public static class Configuration
    {
        public static final GraphDatabaseSettings.CacheTypeSetting cache_type = GraphDatabaseSettings.cache_type;
    }

    private Config config;
    private Iterable<CacheProvider> cacheProviders;
    private final StringLogger logger;
    public DefaultCaches( Iterable<CacheProvider> cacheProviders, StringLogger logger, Config config )
    {
        if(!cacheProviders.iterator().hasNext()) {
            throw new IllegalArgumentException("No cache providers specified, cannot start without caches.");
        }
        this.cacheProviders = cacheProviders;
        this.logger = logger;
        this.config = config;
    }

    @Override
    public void start()
        throws Throwable
    {
        cacheProvider = findCacheProvider( config.get( Configuration.cache_type ) );
        node = cacheProvider.newNodeCache( logger, config );
        relationship = cacheProvider.newRelationshipCache( logger, config );
    }

    @Override
    public CacheProvider getCurrentCacheProvider()
    {
        return cacheProvider;
    }

    @Override
    public Iterable<Cache<?>> caches()
    {
        return cast( iterable( node, relationship ) );
    }


    @Override
    public Cache<NodeImpl> node()
    {
        return node;
    }

    @Override
    public Cache<RelationshipImpl> relationship()
    {
        return relationship;
    }

    private CacheProvider findCacheProvider(String name)
        throws IllegalArgumentException
    {
        for( CacheProvider cacheProvider : cacheProviders )
        {
            if (cacheProvider.getName().equals( name ))
                return cacheProvider;
        }
        throw new IllegalArgumentException( "No cache type '" + name + "'" );
    }
}
