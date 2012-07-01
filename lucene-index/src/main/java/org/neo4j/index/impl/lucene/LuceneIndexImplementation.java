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
package org.neo4j.index.impl.lucene;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;

public class LuceneIndexImplementation extends AbstractIndexImplementation<LuceneDataSource>
{
    public interface Configuration extends AbstractIndexImplementation.Configuration
    {
    }
    
    static final String KEY_TYPE = "type";
    static final String KEY_ANALYZER = "analyzer";
    static final String KEY_TO_LOWER_CASE = "to_lower_case";
    static final String KEY_SIMILARITY = "similarity";
    public static final String SERVICE_NAME = "lucene";

    public static final Map<String, String> EXACT_CONFIG =
            Collections.unmodifiableMap( MapUtil.stringMap(
                    IndexManager.PROVIDER, SERVICE_NAME, KEY_TYPE, "exact" ) );

    public static final Map<String, String> FULLTEXT_CONFIG =
            Collections.unmodifiableMap( MapUtil.stringMap(
                    IndexManager.PROVIDER, SERVICE_NAME, KEY_TYPE, "fulltext",
                    KEY_TO_LOWER_CASE, "true" ) );

    public static final int DEFAULT_LAZY_THRESHOLD = 100;

    final int lazynessThreshold;

    public LuceneIndexImplementation( GraphDatabaseAPI db, Config config, LuceneDataSource dataSource )
    {
        super( db, config, dataSource );
        this.lazynessThreshold = DEFAULT_LAZY_THRESHOLD;
    }

    @Override
    public Map<String, String> fillInDefaults( Map<String, String> source )
    {
        Map<String, String> result = source != null ?
                new HashMap<String, String>( source ) : new HashMap<String, String>();
        String analyzer = result.get( KEY_ANALYZER );
        if ( analyzer == null )
        {
            // Type is only considered if "analyzer" isn't supplied
            String type = result.get( KEY_TYPE );
            if ( type == null )
            {
                type = "exact";
                result.put( KEY_TYPE, type );
            }
            if ( type.equals( "fulltext" ) )
            {
                if ( !result.containsKey( LuceneIndexImplementation.KEY_TO_LOWER_CASE ) )
                {
                    result.put( KEY_TO_LOWER_CASE, "true" );
                }
            }
        }
        return result;
    }

    @Override
    public boolean configMatches( Map<String, String> storedConfig, Map<String, String> config )
    {
        return  matchConfig( storedConfig, config, KEY_TYPE, null ) &&
                matchConfig( storedConfig, config, KEY_TO_LOWER_CASE, "true" ) &&
                matchConfig( storedConfig, config, KEY_ANALYZER, null ) &&
                matchConfig( storedConfig, config, KEY_SIMILARITY, null );
    }
}
