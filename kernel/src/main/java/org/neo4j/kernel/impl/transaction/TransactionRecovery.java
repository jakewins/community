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

package org.neo4j.kernel.impl.transaction;

import java.util.Iterator;
import java.util.List;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

/**
 * Perform transaction recovery on startup
 */
public class TransactionRecovery
    extends LifecycleAdapter
{
    private final StringLogger msgLog;
    private XaDataSourceManager xaDataSourceManager;
    private TxManager txManager;

    public TransactionRecovery( TxManager txManager,
                                StringLogger msgLog,
                                XaDataSourceManager xaDataSourceManager
    )
    {
        this.txManager = txManager;
        this.msgLog = msgLog;
        this.xaDataSourceManager = xaDataSourceManager;
    }

    @Override
    public void start()
        throws Throwable
    {
        // Do recovery on start - all Resources should be registered by now
        TxLog txManagerTxLog = txManager.getTxLog();
        Iterator<List<TxLog.Record>> danglingRecordList =
            txManagerTxLog.getDanglingRecords();
        boolean danglingRecordFound = danglingRecordList.hasNext();
        if ( danglingRecordFound )
        {
            msgLog.warn( "Unresolved transactions found in " + txManagerTxLog.getName()+", recovery started...", true );

            // Recover DataSources
            xaDataSourceManager.recover(danglingRecordList);

            msgLog.warn( "Recovery completed, all transactions have been " +
                "resolved to a consistent state." );
        }
        txManagerTxLog.truncate();
    }
}
