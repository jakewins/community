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
package org.neo4j.server.web;

import java.util.Collection;
import java.util.Set;

import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.server.NeoServer;
import org.neo4j.server.configuration.ConfigurationProvider;
import org.neo4j.server.database.AbstractInjectableProvider;
import org.neo4j.server.database.Database;
import org.neo4j.server.plugins.Injectable;
import org.neo4j.server.plugins.PluginInvocatorProvider;
import org.neo4j.server.rest.paging.LeaseManagerProvider;
import org.neo4j.server.rest.repr.InputFormatProvider;
import org.neo4j.server.rest.repr.OutputFormatProvider;
import org.neo4j.server.rest.repr.RepresentationFormatRepository;
import org.rrd4j.core.RrdDb;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import com.sun.jersey.spi.container.servlet.WebConfig;

@SuppressWarnings( "serial" )
public class NeoServletContainer extends ServletContainer
{
    private final NeoServer server;
    private final Collection<Injectable<?>> injectables;
    private DependencyResolver dependencyResolver;

    public NeoServletContainer( DependencyResolver dependency, NeoServer server, Collection<Injectable<?>> injectables )
    {
        this.server = server;
        this.injectables = injectables;
        this.dependencyResolver = dependency;
    }

    @Override
    protected void configure( WebConfig wc, ResourceConfig rc, WebApplication wa )
    {
        super.configure( wc, rc, wa );

        Set<Object> singletons = rc.getSingletons();
        
        // TODO: Add an iterator method to the dependency resolver,
        // and then just make all available dependencies injectable.
        addInjectableDependency(singletons, Database.class);
        addInjectableDependency(singletons, GraphDatabaseAPI.class);
        addInjectableDependency(singletons, NeoServer.class);
        addInjectableDependency(singletons, Config.class);
        addInjectableDependency(singletons, WebServer.class);
        addInjectableDependency(singletons, RrdDb.class);
        
        singletons.add( new LeaseManagerProvider() );
        singletons.add( new ConfigurationProvider( server.getConfiguration() ) );

        RepresentationFormatRepository repository = new RepresentationFormatRepository( server.getExtensionManager() );
        singletons.add( new InputFormatProvider( repository ) );
        singletons.add( new OutputFormatProvider( repository ) );
        
        // TODO: Refactor ExtensionManager to be reuseable, then make
        // it available as a general dependency, rather than via this getter
        // on the server interface
        singletons.add( new PluginInvocatorProvider( server.getExtensionManager() ) );
        

        for ( final Injectable<?> injectable : injectables )
        {
            singletons.add( new InjectableWrapper( injectable ) );
        }
    }

    /**
     * Make a class that is available via the dependency resolver also
     * available through dependency injection in jersey.
     * 
     * @param singletons
     * @param clazz
     */
    private void addInjectableDependency(Set<Object> singletons, Class<?> clazz)
    {
        singletons.add( AbstractInjectableProvider.create( dependencyResolver.resolveDependency(clazz)));
    }

    private static class InjectableWrapper extends AbstractInjectableProvider<Object>
    {
        private final Injectable<?> injectable;

        public InjectableWrapper( Injectable injectable )
        {
            super( injectable.getType() );
            this.injectable = injectable;
        }

        @Override
        public Object getValue( HttpContext c )
        {
            return injectable.getValue();
        }
    }
}
