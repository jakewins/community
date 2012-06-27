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

import static java.lang.Long.parseLong;
import static org.neo4j.index.base.EntityId.entityId;
import static org.neo4j.index.impl.lucene.LuceneIndex.KEY_DOC_ID;

import java.util.Collection;

import org.apache.lucene.document.Document;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.base.AbstractIndexHits;
import org.neo4j.index.base.EntityId;

class DocToIdIterator extends AbstractIndexHits<EntityId>
{
    private final Collection<EntityId> exclude;
    private IndexReference searcherOrNull;
    private final IndexHits<Document> source;
    
    DocToIdIterator( IndexHits<Document> source, Collection<EntityId> exclude, IndexReference searcherOrNull )
    {
        this.source = source;
        this.exclude = exclude;
        this.searcherOrNull = searcherOrNull;
        if ( source.size() == 0 )
        {
            close();
        }
    }

    @Override
    protected EntityId fetchNextOrNull()
    {
        EntityId result = null;
        while ( result == null )
        {
            if ( !source.hasNext() )
            {
                endReached();
                break;
            }
            Document doc = source.next();
            EntityId id = entityId( parseLong( doc.get( KEY_DOC_ID ) ) );
            if ( !exclude.contains( id ) ) result = id;
        }
        return result;
    }

    protected void endReached()
    {
        close();
    }
    
    @Override
    public void close()
    {
        if ( !isClosed() )
        {
            this.searcherOrNull.close();
            this.searcherOrNull = null;
        }
    }

    public int size()
    {
        return source.size()-exclude.size();
    }

    private boolean isClosed()
    {
        return searcherOrNull==null;
    }

    public float currentScore()
    {
        return source.currentScore();
    }
    
    @Override
    protected void finalize() throws Throwable
    {
        close();
        super.finalize();
    }
}
