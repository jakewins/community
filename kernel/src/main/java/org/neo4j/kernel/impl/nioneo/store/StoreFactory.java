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

import org.neo4j.kernel.IdGeneratorFactory;
import org.neo4j.kernel.IdType;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.core.LastCommittedTxIdSetter;
import org.neo4j.kernel.impl.transaction.TxHook;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.logging.StringLogger;

public class StoreFactory {

    private Config config;
    private FileSystemAbstraction fileSystem;
    private IdGeneratorFactory idGeneratorFactory;
    private StringLogger log;
    private LifeSupport life;
    private TxHook txHook;
    private LastCommittedTxIdSetter lastCommittedTxIdSetter;

    public StoreFactory(Config config,
            LastCommittedTxIdSetter lastCommittedTxIdSetter,
            IdGeneratorFactory idGeneratorFactory, FileSystemAbstraction fileSystem,
            StringLogger log, TxHook txHook, LifeSupport life)
    {
        this.config = config;
        this.idGeneratorFactory = idGeneratorFactory;
        this.fileSystem = fileSystem;
        this.log = log;
        this.life = life;
        this.txHook = txHook;
        this.lastCommittedTxIdSetter = lastCommittedTxIdSetter;
    }
    
    /**
     * Creates and assembles the full structure of Neo4j Stores,
     * and adds those stores in appropriate order to the LifeSupport
     * system provided when this factory was instantiated.
     * 
     * You will still need to {@link LifeSupport#start()} before your 
     * {@link NeoStore} is ready for use.
     * 
     * @return a brand new NeoStore
     */
    public NeoStore createNeoStore() {
        
        // Relationship types
        DynamicStringStore relationshipTypeNameStore = new DynamicStringStore( config, IdType.RELATIONSHIP_TYPE_BLOCK, idGeneratorFactory, fileSystem, log);
        RelationshipTypeStore relationshipTypeStore = new RelationshipTypeStore( config, idGeneratorFactory, fileSystem, log, 
                relationshipTypeNameStore);
        
        // String properties
        DynamicStringStore stringPropertyStore = new DynamicStringStore( config, IdType.STRING_BLOCK, idGeneratorFactory, fileSystem, log);

        // Property names
        DynamicStringStore propertyIndexNameStore = new DynamicStringStore( config, IdType.PROPERTY_INDEX_BLOCK, idGeneratorFactory, fileSystem, log);
        PropertyIndexStore propertyIndexStore = new PropertyIndexStore( config, idGeneratorFactory, fileSystem, log, 
                propertyIndexNameStore );
        
        // Array properties
        DynamicArrayStore arrayPropertyStore = new DynamicArrayStore( config, IdType.ARRAY_BLOCK, idGeneratorFactory, fileSystem, log);
        
        // Property storage
        PropertyStore propertyStore = new PropertyStore( config, idGeneratorFactory, fileSystem, log,
                stringPropertyStore, 
                propertyIndexStore, 
                arrayPropertyStore);
        
        // Relationship storage
        RelationshipStore relationshipStore = new RelationshipStore( config, idGeneratorFactory, fileSystem, log);
        
        // Node storage
        NodeStore nodeStore = new NodeStore( config, idGeneratorFactory, fileSystem, log);
        
        // Core storage
        NeoStore neoStore = new NeoStore( config, lastCommittedTxIdSetter, 
                idGeneratorFactory, fileSystem, log, txHook,
                relationshipTypeStore,
                propertyStore,
                relationshipStore,
                nodeStore);
        
        // Start/stop order for stores
        life.add(neoStore);
        life.add(nodeStore);
        life.add(relationshipStore);
        life.add(propertyStore);
        life.add(arrayPropertyStore);
        life.add(propertyIndexStore);
        life.add(propertyIndexNameStore);
        life.add(stringPropertyStore);
        life.add(relationshipTypeStore);
        life.add(relationshipTypeNameStore);
        
        return neoStore;
    }

}
