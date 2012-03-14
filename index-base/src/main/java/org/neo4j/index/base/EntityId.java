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
package org.neo4j.index.base;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

public class EntityId
{
    private final long id;

    private EntityId( long id )
    {
        this.id = id;
    }
    
    public long getId()
    {
        return this.id;
    }
    
    @Override
    public boolean equals( Object obj )
    {
        return obj instanceof EntityId ? ((EntityId)obj).id == id : false;
    }
    
    @Override
    public int hashCode()
    {
        // Copied from Long
        return (int)(id ^ (id >>> 32));
    }
    
    @Override
    public String toString()
    {
        return "node[" + id + "]";
    }
    
    public static EntityId entityId( Node node )
    {
        return new EntityId( node.getId() );
    }
    
    public static EntityId entityId( long id )
    {
        return new EntityId( id );
    }
    
    public static RelationshipId entityId( Relationship relationship )
    {
        return new RelationshipId( relationship.getId(), relationship.getStartNode().getId(),
                relationship.getEndNode().getId() );
    }
    
    public static RelationshipId entityId( long id, long startNode, long endNode )
    {
        return new RelationshipId( id, startNode, endNode );
    }
    
    public static class RelationshipId extends EntityId
    {
        private final long startNode;
        private final long endNode;

        RelationshipId( long id, long startNode, long endNode )
        {
            super( id );
            this.startNode = startNode;
            this.endNode = endNode;
        }
        
        public long getStartNode()
        {
            return startNode;
        }
        
        public long getEndNode()
        {
            return endNode;
        }
        
        @Override
        public String toString()
        {
            return "rel[" + getId() + "," + startNode + "," + endNode + "]";
        }
    }
}
