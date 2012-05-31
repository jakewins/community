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
package org.neo4j.server;

import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.helpers.Service;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.lifecycle.LifecycleException;

/**
 * Start the Neo4j Server.
 */
public class Main 
{

    private static final Integer OK = 0;
    private static final Integer WEB_SERVER_STARTUP_ERROR_CODE = 1;
    private static final Integer GRAPH_DATABASE_STARTUP_ERROR_CODE = 2;
    private static final Integer WEB_SERVER_SHUTDOWN_ERROR_CODE = 3;
    
    public static void main( String[] args )
    {
        LifeSupport life = new LifeSupport();

        life.add( loadMostDerivedBootstrapper() );

        try
        {
            addShutdownHook(life);
            life.start();
        }
        catch( LifecycleException e )
        {
            if (e.getCause() instanceof TransactionFailureException)
            {
                System.exit( GRAPH_DATABASE_STARTUP_ERROR_CODE );
            } else
            {
                System.exit( WEB_SERVER_STARTUP_ERROR_CODE );
            }
        }
    }


    public static Bootstrapper loadMostDerivedBootstrapper()
    {
        Bootstrapper winner = new CommunityBootstrapper();
        for ( Bootstrapper candidate : Service.load( Bootstrapper.class ) )
        {
            if ( candidate.isMoreDerivedThan( winner ) ) winner = candidate;
        }
        return winner;
    }
    

    private static void addShutdownHook(final LifeSupport life)
    {
        Runtime.getRuntime()
                .addShutdownHook( new Thread()
                {
                    @Override
                    public void run()
                    {
                        try 
                        {
                            life.shutdown();
                            System.exit(OK);
                        } catch(Throwable e) 
                        {
                            System.exit(WEB_SERVER_SHUTDOWN_ERROR_CODE);
                        }
                    }
                } );
    }
    
}
