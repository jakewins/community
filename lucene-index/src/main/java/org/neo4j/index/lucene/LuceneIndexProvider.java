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
package org.neo4j.index.lucene;

import static org.neo4j.helpers.collection.MapUtil.stringMap;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.AbstractIndexProvider;
import org.neo4j.index.base.IndexDataSource;
import org.neo4j.index.impl.lucene.LuceneDataSource;
import org.neo4j.index.impl.lucene.LuceneIndexImplementation;
import org.neo4j.kernel.ConfigProxy;
import org.neo4j.kernel.GraphDatabaseSPI;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;

public class LuceneIndexProvider extends AbstractIndexProvider
{
    public interface Configuration
    {
        boolean read_only(boolean def);
    }
    
    public LuceneIndexProvider()
    {
        super( LuceneIndexImplementation.SERVICE_NAME );
    }

    @Override
    protected IndexDataSource newDataSource( Map<String, String> params, IndexStore indexStore,
            FileSystemAbstraction fileSystemAbstraction, XaFactory xaFactory )
    {
        params = overrideLogAndProviderStore( params );
        return new LuceneDataSource( ConfigProxy.config( params,
                LuceneDataSource.Configuration.class ), indexStore, fileSystemAbstraction, xaFactory );
    }

    private Map<String, String> overrideLogAndProviderStore( Map<String, String> params )
    {
        String storeDir = params.get( "store_dir" );
        return stringMap( new HashMap<String, String>( params ),
                // Overridden for legacy purposes. If left out then good defaults are used instead
                "index_logical_log", new File( new File( storeDir, "index" ), "lucene.log" ).getAbsolutePath(),
                "index_provider_db", new File( new File( storeDir, "index" ), "lucene-store.db" ).getAbsolutePath(),
                "index_dir_name", "lucene" );
    }

    @Override
    protected AbstractIndexImplementation newIndexImplementation( GraphDatabaseSPI db,
            IndexDataSource dataSource, Map<String, String> params )
    {
        return new LuceneIndexImplementation( db, ConfigProxy.config( params, LuceneIndexImplementation.Configuration.class ), (LuceneDataSource)dataSource );
    }
}
