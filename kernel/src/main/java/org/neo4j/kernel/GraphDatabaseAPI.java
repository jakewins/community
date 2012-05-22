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

package org.neo4j.kernel;

import java.util.Collection;
import javax.transaction.TransactionManager;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.guard.Guard;
import org.neo4j.kernel.impl.core.KernelPanicEventGenerator;
import org.neo4j.kernel.impl.core.LockReleaser;
import org.neo4j.kernel.impl.core.NodeManager;
import org.neo4j.kernel.impl.core.RelationshipTypeHolder;
import org.neo4j.kernel.impl.persistence.PersistenceSource;
import org.neo4j.kernel.impl.transaction.LockManager;
import org.neo4j.kernel.impl.transaction.XaDataSourceManager;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.kernel.info.DiagnosticsManager;

/**
 * This API can be used to get access to services.
 *
 * TODO: The methods exposing internal services directly should go away. It indicates lack of abstractions somewhere. DO NOT ADD MORE USAGE OF THESE!
 */
public interface GraphDatabaseAPI
    extends GraphDatabaseService
{
    /**
     * This is the preferred way to get access to internal services.
     *
     * @return a dependency resolver which can be used to access internal services
     */
    DependencyResolver getDependencyResolver();

    @Deprecated
    NodeManager getNodeManager();

    @Deprecated
    LockReleaser getLockReleaser();

    @Deprecated
    LockManager getLockManager();

    @Deprecated
    XaDataSourceManager getXaDataSourceManager();

    @Deprecated
    TransactionManager getTxManager();

    @Deprecated
    DiagnosticsManager getDiagnosticsManager();
    
    @Deprecated
    StringLogger getMessageLog();

    @Deprecated
    RelationshipTypeHolder getRelationshipTypeHolder();

    @Deprecated
    IdGeneratorFactory getIdGeneratorFactory();

    @Deprecated
    String getStoreDir();

    @Deprecated
    KernelData getKernelData();

    @Deprecated
    <T> T getSingleManagementBean( Class<T> type );

    @Deprecated
    TransactionBuilder tx();
    
    @Deprecated
    PersistenceSource getPersistenceSource();

    @Deprecated
    <T> Collection<T> getManagementBeans( Class<T> type );
    
    @Deprecated
    KernelPanicEventGenerator getKernelPanicGenerator();

    @Deprecated
    Guard getGuard();
}
