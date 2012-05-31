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
package org.neo4j.kernel.info;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.neo4j.helpers.Format;
import org.neo4j.kernel.logging.StringLogger;

import static org.neo4j.helpers.Format.*;

enum SystemDiagnostics implements DiagnosticsProvider
{
    SYSTEM_MEMORY( "System memory information:" )
    {
        private static final String SUN_OS_BEAN = "com.sun.management.OperatingSystemMXBean";
        private static final String IBM_OS_BEAN = "com.ibm.lang.management.OperatingSystemMXBean";
        
        @Override
        void dump( List<String> logger )
        {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            logBeanBytesProperty( logger, "Total Physical memory: ", os, SUN_OS_BEAN, "getTotalPhysicalMemorySize" );
            logBeanBytesProperty( logger, "Free Physical memory: ", os, SUN_OS_BEAN, "getFreePhysicalMemorySize" );
            logBeanBytesProperty( logger, "Committed virtual memory: ", os, SUN_OS_BEAN, "getCommittedVirtualMemorySize" );
            logBeanBytesProperty( logger, "Total swap space: ", os, SUN_OS_BEAN, "getTotalSwapSpaceSize" );
            logBeanBytesProperty( logger, "Free swap space: ", os, SUN_OS_BEAN, "getFreeSwapSpaceSize" );
            logBeanBytesProperty( logger, "Total physical memory: ", os, IBM_OS_BEAN, "getTotalPhysicalMemory" );
            logBeanBytesProperty( logger, "Free physical memory: ", os, IBM_OS_BEAN, "getFreePhysicalMemorySize" );
        }

        private void logBeanBytesProperty( List<String> logger, String message, Object bean, String type,
                String method )
        {
            Object value = getBeanProperty( bean, type, method, null );
            if( value instanceof Number )
            {
                logger.add( message + bytes( ( (Number) value ).longValue() ) );
            }
        }
    },
    JAVA_MEMORY( "JVM memory information:" )
    {
        @Override
        void dump( List<String> logger )
        {
            logger.add( "Free  memory: " + bytes( Runtime.getRuntime().freeMemory() ) );
            logger.add( "Total memory: " + bytes( Runtime.getRuntime().totalMemory() ) );
            logger.add( "Max   memory: " + bytes( Runtime.getRuntime().maxMemory() ) );
            for ( GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans() )
            {
                logger.add( "Garbage Collector: " + gc.getName() + ": " + Arrays.toString( gc.getMemoryPoolNames() ) );
            }
            for ( MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans() )
            {
                MemoryUsage usage = pool.getUsage();
                logger.add( String.format( "Memory Pool: %s (%s): committed=%s, used=%s, max=%s, threshold=%s",
                        pool.getName(), pool.getType(), usage == null ? "?" : bytes( usage.getCommitted() ),
                        usage == null ? "?" : bytes( usage.getUsed() ), usage == null ? "?" : bytes( usage.getMax() ),
                        pool.isUsageThresholdSupported() ? bytes( pool.getUsageThreshold() ) : "?" ) );
            }
        }
    },
    OPERATING_SYSTEM( "Operating system information:" )
    {
        private static final String SUN_UNIX_BEAN = "com.sun.management.UnixOperatingSystemMXBean";
        
        @Override
        void dump( List<String> logger )
        {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            logger.add( String.format( "Operating System: %s; version: %s; arch: %s; cpus: %s", os.getName(),
                    os.getVersion(), os.getArch(), Integer.valueOf( os.getAvailableProcessors() ) ) );
            logBeanProperty( logger, "Max number of file descriptors: ", os, SUN_UNIX_BEAN, "getMaxFileDescriptorCount" );
            logBeanProperty( logger, "Number of open file descriptors: ", os, SUN_UNIX_BEAN, "getOpenFileDescriptorCount" );
            logger.add( "Process id: " + runtime.getName() );
            logger.add( "Byte order: " + ByteOrder.nativeOrder() );
        }

        private void logBeanProperty( List<String> logger, String message, Object bean, String type, String method )
        {
            Object value = getBeanProperty( bean, type, method, null );
            if( value != null )
            {
                logger.add( message + value );
            }
        }
    },
    JAVA_VIRTUAL_MACHINE( "JVM information:" )
    {
        @Override
        void dump( List<String> logger )
        {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            logger.add( "VM Name: " + runtime.getVmName() );
            logger.add( "VM Vendor: " + runtime.getVmVendor() );
            logger.add( "VM Version: " + runtime.getVmVersion() );
            CompilationMXBean compiler = ManagementFactory.getCompilationMXBean();
            logger.add( "JIT compiler: " + ( ( compiler == null ) ? "unknown" : compiler.getName() ) );
            logger.add( "VM Arguments: " + runtime.getInputArguments() );
        }
    },
    CLASSPATH( "Java classpath:" )
    {
        @Override
        void dump( List<String> logger )
        {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            Collection<String> classpath;
            if ( runtime.isBootClassPathSupported() )
            {
                classpath = buildClassPath( getClass().getClassLoader(),
                        new String[] { "bootstrap", "classpath" },
                        runtime.getBootClassPath(), runtime.getClassPath() );
            }
            else
            {
                classpath = buildClassPath( getClass().getClassLoader(),
                        new String[] { "classpath" }, runtime.getClassPath() );
            }
            for ( String path : classpath )
            {
                logger.add( path );
            }
        }

        private Collection<String> buildClassPath( ClassLoader loader, String[] pathKeys, String... classPaths )
        {
            Map<String, String> paths = new HashMap<String, String>();
            assert pathKeys.length == classPaths.length;
            for( int i = 0; i < classPaths.length; i++ )
            {
                for( String path : classPaths[ i ].split( File.pathSeparator ) )
                {
                    paths.put( canonicalize( path ), pathValue( paths, pathKeys[ i ], path ) );
                }
            }
            for ( int level = 0; loader != null; level++ )
            {
                if ( loader instanceof URLClassLoader )
                {
                    URLClassLoader urls = (URLClassLoader) loader;
                    for( URL url : urls.getURLs() )
                    {
                        if( "file".equalsIgnoreCase( url.getProtocol() ) )
                        {
                            paths.put( url.toString(), pathValue( paths, "loader." + level, url.getPath() ) );
                        }
                    }
                }
                loader = loader.getParent();
            }
            List<String> result = new ArrayList<String>( paths.size() );
            for ( Map.Entry<String, String> path : paths.entrySet() )
            {
                result.add( " [" + path.getValue() + "] " + path.getKey() );
            }
            return result;
        }

        private String pathValue( Map<String, String> paths, String key, String path )
        {
            String value;
            if ( null != ( value = paths.remove( canonicalize( path ) ) ) )
            {
                value += " + " + key;
            }
            else
            {
                value = key;
            }
            return value;
        }
    },
    LIBRARY_PATH( "Library path:" )
    {
        @Override
        void dump( List<String> logger )
        {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            for ( String path : runtime.getLibraryPath().split( File.pathSeparator ) )
            {
                logger.add( canonicalize( path ) );
            }
        }
    },
    SYSTEM_PROPERTIES( "System.properties:" )
    {
        @Override
        void dump( List<String> logger )
        {
            for ( Object property : System.getProperties().keySet() )
            {
                if ( property instanceof String )
                {
                    String key = (String) property;
                    if( key.startsWith( "java." ) || key.startsWith( "os." ) || key.endsWith( ".boot.class.path" )
                        || key.equals( "line.separator" ) )
                    {
                        continue;
                    }
                    logger.add( key + " = " + System.getProperty( key ) );
                }
            }            
        }
    },
    LINUX_SCHEDULERS( "Linux scheduler information:" )
    {
        private final File SYS_BLOCK = new File( "/sys/block" );

        @Override
        boolean isApplicable()
        {
            return SYS_BLOCK.isDirectory();
        }

        @Override
        void dump( List<String> logger )
        {
            for ( File subdir : SYS_BLOCK.listFiles( new java.io.FileFilter()
            {
                @Override
                public boolean accept( File path )
                {
                    return path.isDirectory();
                }
            } ) )
            {
                File scheduler = new File( subdir, "queue/scheduler" );
                if ( scheduler.isFile() )
                {
                    try
                    {
                        BufferedReader reader = new BufferedReader( new FileReader( scheduler ) );
                        try
                        {
                            for( String line; null != ( line = reader.readLine() ); )
                            {
                                logger.add( line );
                            }
                        }
                        finally
                        {
                            reader.close();
                        }
                    }
                    catch ( IOException e )
                    {
                        // ignore
                    }
                }
            }
        }
    },
    ;
    
    private final String message;

    private SystemDiagnostics(String message) {
        this.message = message;
    }
    
    static void registerWith( DiagnosticsManager manager )
    {
        for ( SystemDiagnostics provider : values() )
        {
            if( provider.isApplicable() )
            {
                manager.appendProvider( provider );
            }
        }
    }

    boolean isApplicable()
    {
        return true;
    }

    @Override
    public String getDiagnosticsIdentifier()
    {
        return name();
    }

    @Override
    public void acceptDiagnosticsVisitor( Object visitor )
    {
        // nothing visits this
    }

    @Override
    public void dump( DiagnosticsPhase phase, StringLogger log )
    {
        if ( phase.isInitialization() || phase.isExplicitlyRequested() )
        {
            log.logMessage( Format.logLongMessage(message, new Iterable<String>()
            {
                @Override
                public Iterator<String> iterator()
                {
                    List<String> lines = new ArrayList<String>(  );
                    dump( lines );
                    return lines.iterator();
                }
            }));
        }
    }

    abstract void dump( List<String> logger );

    private static String canonicalize( String path )
    {
        try
        {
            return new File( path ).getCanonicalFile().getAbsolutePath();
        }
        catch ( IOException e )
        {
            return new File( path ).getAbsolutePath();
        }
    }

    private static Object getBeanProperty( Object bean, String type, String method, String defVal )
    {
        try
        {
            return Class.forName( type ).getMethod( method ).invoke( bean );
        }
        catch ( Exception e )
        {
            return defVal;
        }
        catch ( LinkageError e )
        {
            return defVal;
        }
    }
}
