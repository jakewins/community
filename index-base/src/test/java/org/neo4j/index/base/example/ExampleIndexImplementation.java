/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.index.base.example;

import java.util.Map;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.IndexDataSource;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;

public class ExampleIndexImplementation extends AbstractIndexImplementation<IndexDataSource>
{

    protected ExampleIndexImplementation( GraphDatabaseAPI db, Config config, IndexDataSource dataSource )
    {
        super( db, config, dataSource );
        // TODO Auto-generated constructor stub
    }

    @Override
    public Index<Node> nodeIndex( String indexName, Map<String, String> config )
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RelationshipIndex relationshipIndex( String indexName, Map<String, String> config )
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> fillInDefaults( Map<String, String> config )
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean configMatches( Map<String, String> storedConfig, Map<String, String> config )
    {
        // TODO Auto-generated method stub
        return false;
    }

}
