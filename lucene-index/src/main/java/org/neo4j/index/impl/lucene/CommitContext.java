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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.IndexIdentifier;

/**
 * This presents a context for each {@link LuceneCommand} when they are
 * committing its data.
 */
class CommitContext
{
    final LuceneDataSource dataSource;
    final IndexIdentifier identifier;
    final IndexType indexType;
    final Map<Long, DocumentContext> documents = new HashMap<Long, DocumentContext>();
    final boolean recovery;

    IndexReference searcher;
    IndexWriter writer;

    CommitContext( LuceneDataSource dataSource, IndexIdentifier identifier, IndexType indexType, boolean recovery )
    {
        this.dataSource = dataSource;
        this.identifier = identifier;
        this.indexType = indexType;
        this.recovery = recovery;
    }

    void ensureWriterInstantiated()
    {
        if ( searcher == null )
        {
            searcher = dataSource.getIndexSearcher( identifier );
            writer = searcher.getWriter();
        }
    }
    
    DocumentContext getDocument( EntityId entityId, boolean allowCreate )
    {
        long id = entityId.getId();
        DocumentContext context = documents.get( id );
        if ( context != null )
        {
            return context;
        }

        Document document = LuceneDataSource.findDocument( indexType, searcher.getSearcher(), id );
        if ( document != null )
        {
            context = new DocumentContext( document, true, id );
            documents.put( id, context );
        }
        else if ( allowCreate )
        {
            context = new DocumentContext( IndexType.newDocument( entityId ), false, id );
            documents.put( id, context );
        }
        return context;
    }

    private void applyDocuments( IndexWriter writer, IndexType type,
            Map<Long, DocumentContext> documents ) throws IOException
    {
        for ( Map.Entry<Long, DocumentContext> entry : documents.entrySet() )
        {
            DocumentContext context = entry.getValue();
            if ( context.exists )
            {
                if ( LuceneDataSource.documentIsEmpty( context.document ) )
                {
                    writer.deleteDocuments( type.idTerm( context.entityId ) );
                }
                else
                {
                    writer.updateDocument( type.idTerm( context.entityId ), context.document );
                }
            }
            else
            {
                writer.addDocument( context.document );
            }
        }
    }
    
    public void close() throws IOException
    {
        if ( searcher != null )
        {
            applyDocuments( writer, indexType, documents );
            dataSource.invalidateIndexSearcher( identifier );
            searcher.close();
        }
    }

    static class DocumentContext
    {
        final Document document;
        final boolean exists;
        final long entityId;

        DocumentContext( Document document, boolean exists, long entityId )
        {
            this.document = document;
            this.exists = exists;
            this.entityId = entityId;
        }
    }
}
