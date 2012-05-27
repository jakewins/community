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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.helpers.UTF8;
import org.neo4j.kernel.IdGeneratorFactory;
import org.neo4j.kernel.IdType;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.core.ReadOnlyDbException;
import org.neo4j.kernel.logging.StringLogger;

/**
 * An abstract representation of a store. A store is a file that contains
 * records. Each record has a fixed size (<CODE>getRecordSize()</CODE>) so
 * the position for a record can be calculated by
 * <CODE>id * getRecordSize()</CODE>.
 * <p>
 * A store has an {@link IdGenerator} managing the records that are free or in
 * use.
 */
public abstract class AbstractStore extends CommonAbstractStore
{
    public static abstract class Configuration
        extends CommonAbstractStore.Configuration
    {
        public static final GraphDatabaseSetting.BooleanSetting rebuild_idgenerators_fast = GraphDatabaseSettings.rebuild_idgenerators_fast;
    }

    public AbstractStore( StringLogger logger, Config conf, IdType idType, IdGeneratorFactory idGeneratorFactory, FileSystemAbstraction fileSystemAbstraction)
    {
        super( logger, conf, idType, idGeneratorFactory, fileSystemAbstraction );
    }

    /**
     * Returns the fixed size of each record in this store.
     *
     * @return The record size
     */
    public abstract int getRecordSize();

    @Override
    protected long figureOutHighestIdInUse()
    {
        try
        {
            return getFileChannel().size()/getRecordSize();
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }
    
    @Override
    protected void createStorage() throws Throwable 
    {
        String typeAndVersionDescriptor = getTypeAndVersionDescriptor();
        
        // sanity checks
        if ( storageFileName == null )
        {
            throw new IllegalArgumentException( "Null filename" );
        }
        if ( fileSystemAbstraction.fileExists( storageFileName ) )
        {
            throw new IllegalStateException( "Can't create store[" + storageFileName
                    + "], file already exists" );
        }

        // write the header
        try
        {
            FileChannel channel = fileSystemAbstraction.create(storageFileName);
            int endHeaderSize = UTF8.encode(typeAndVersionDescriptor).length;
            ByteBuffer buffer = ByteBuffer.allocate( endHeaderSize );
            buffer.put( UTF8.encode( typeAndVersionDescriptor ) ).flip();
            channel.write( buffer );
            channel.force( false );
            channel.close();
        }
        catch ( IOException e )
        {
            throw new UnderlyingStorageException( "Unable to create store "
                    + storageFileName, e );
        }
        idGeneratorFactory.create( fileSystemAbstraction, storageFileName + ".id" );
    }

    @Override
    protected int getEffectiveRecordSize()
    {
        return getRecordSize();
    }

    @Override
    protected void readAndVerifyBlockSize() throws IOException
    {
        // record size is fixed for non-dynamic stores, so nothing to do here
    }

    @Override
    protected void verifyFileSizeAndTruncate() throws IOException
    {
        int expectedVersionLength = UTF8.encode( buildTypeDescriptorAndVersion( getTypeDescriptor() ) ).length;
        long fileSize = getFileChannel().size();
        if ( getRecordSize() != 0
            && (fileSize - expectedVersionLength) % getRecordSize() != 0  && !isReadOnly() )
        {
            setStoreNotOk( new IllegalStateException( "Misaligned file size " + fileSize + " for " + this + ", expected version length:" + expectedVersionLength ) );
        }
        if ( getStoreOk() && !isReadOnly() )
        {
            getFileChannel().truncate( fileSize - expectedVersionLength );
        }
    }

    /**
     * Sets the high id of {@link IdGenerator}.
     *
     * @param id
     *            The high id
     */
    public void setHighId( int id )
    {
        super.setHighId( id );
    }

    private long findHighIdBackwards() throws IOException
    {
        // Duplicated method
        FileChannel fileChannel = getFileChannel();
        int recordSize = getRecordSize();
        long fileSize = fileChannel.size();
        long highId = fileSize / recordSize;
        ByteBuffer byteBuffer = ByteBuffer.allocate( getRecordSize() );
        for ( long i = highId; i > 0; i-- )
        {
            fileChannel.position( i * recordSize );
            if ( fileChannel.read( byteBuffer ) > 0 )
            {
                byteBuffer.flip();
                boolean isInUse = isRecordInUse( byteBuffer );
                byteBuffer.clear();
                if ( isInUse )
                {
                    return i;
                }
            }
        }
        return 0;
    }

    protected boolean isRecordInUse(ByteBuffer buffer)
    {
        byte inUse = buffer.get();
        return ( ( inUse & 0x1 ) == Record.IN_USE.byteValue() );
    }

    /**
     * Rebuilds the {@link IdGenerator} by looping through all records and
     * checking if record in use or not.
     *
     * @throws IOException
     *             if unable to rebuild the id generator
     */
    @Override
    protected void rebuildIdGenerator()
    {
        if ( isReadOnly() && !isBackupSlave() )
        {
            throw new ReadOnlyDbException();
        }

        logger.debug( "Rebuilding id generator for[" + getStorageFileName()
            + "] ..." );
        closeIdGenerator();
        if ( fileSystemAbstraction.fileExists( getStorageFileName() + ".id" ) )
        {
            boolean success = fileSystemAbstraction.deleteFile( getStorageFileName() + ".id" );
            assert success;
        }
        createIdGenerator( getStorageFileName() + ".id" );
        openIdGenerator( false );
        FileChannel fileChannel = getFileChannel();
        long highId = 1;
        long defraggedCount = 0;
        try
        {
            long fileSize = fileChannel.size();
            int recordSize = getRecordSize();
            boolean fullRebuild = true;
            if ( config.getBoolean( Configuration.rebuild_idgenerators_fast) )
            {
                fullRebuild = false;
                highId = findHighIdBackwards();
            }
            ByteBuffer byteBuffer = ByteBuffer.allocate( recordSize );
            // Duplicated code block
            LinkedList<Long> freeIdList = new LinkedList<Long>();
            if ( fullRebuild )
            {
                for ( long i = 0; i * recordSize < fileSize && recordSize > 0;
                    i++ )
                {
                    fileChannel.position( i * recordSize );
                    byteBuffer.clear();
                    fileChannel.read( byteBuffer );
                    byteBuffer.flip();
                    if ( !isRecordInUse( byteBuffer ) )
                    {
                        freeIdList.add( i );
                    }
                    else
                    {
                        highId = i;
                        setHighId( highId+1 );
                        while ( !freeIdList.isEmpty() )
                        {
                            freeId( freeIdList.removeFirst() );
                            defraggedCount++;
                        }
                    }
                }
            }
        }
        catch ( IOException e )
        {
            throw new UnderlyingStorageException(
                "Unable to rebuild id generator " + getStorageFileName(), e );
        }
        setHighId( highId + 1 );
        logger.debug( getStorageFileName() + " rebuild id generator, highId=" + getHighId() +
                " defragged count=" + defraggedCount, true );
        closeIdGenerator();
        openIdGenerator( false );
    }

    public abstract List<WindowPoolStats> getAllWindowPoolStats();
}