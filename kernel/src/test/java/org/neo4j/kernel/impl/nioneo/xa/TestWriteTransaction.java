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
package org.neo4j.kernel.impl.nioneo.xa;

import static org.mockito.Mockito.mock;

import javax.transaction.xa.XAException;

import junit.framework.TestCase;
import org.junit.Test;
import org.mockito.Mockito;
import org.neo4j.kernel.impl.core.LockReleaser;
import org.neo4j.kernel.impl.nioneo.store.NeoStore;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;
import org.neo4j.kernel.impl.nioneo.store.PropertyRecord;
import org.neo4j.kernel.impl.nioneo.store.Record;
import org.neo4j.kernel.impl.transaction.LockManager;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;

public class TestWriteTransaction
{

    @Test
    public void commandsWrittenToLogShouldBeProperlyOrdered() throws XAException
    {
        // Given
        XaLogicalLog mockLog = mock(XaLogicalLog.class);
        NeoStore mockStore = mock( NeoStore.class );
        LockReleaser mockReleaser = mock( LockReleaser.class );
        LockManager mockLockManager = mock( LockManager.class );

        WriteTransaction tx = new WriteTransaction( 0, mockLog, mockStore, mockReleaser, mockLockManager );

        tx.addNodeRecord( new NodeRecord( 0l, Record.NO_NEXT_RELATIONSHIP.intValue(), Record.NO_NEXT_PROPERTY.intValue() ) );
        tx.addPropertyRecord( new PropertyRecord( 0l ) );


        // When
        tx.doPrepare();

        // Then


    }

}
