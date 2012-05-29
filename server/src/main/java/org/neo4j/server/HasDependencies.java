package org.neo4j.server;

import org.neo4j.graphdb.DependencyResolver;

public interface HasDependencies {

    /**
     * Called by the neo4j server as the first method call after the 
     * constructor. The {@link DependencyResolver} provided
     * here will allow you to access core components of the server.
     * 
     * @param resolver
     */
    public void resolveDependencies(DependencyResolver resolver);
    
}
