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

package org.neo4j.kernel.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public abstract class StringLogger
{

    public static final String DEFAULT_NAME = "messages.log";
    public static final StringLogger SYSTEM =
        new FileStringLogger( new PrintWriter( System.out ) );
    private static final int DEFAULT_THRESHOLD_FOR_ROTATION = 100 * 1024 * 1024;

    public static StringLogger logger( File logfile )
    {
        try
        {
            return new FileStringLogger( new PrintWriter( new FileWriter( logfile, true ) ) );
        }
        catch ( IOException cause )
        {
            throw new RuntimeException( "Could not create log file: " + logfile, cause );
        }
    }

    public static StringLogger wrap( final StringBuffer target )
    {
        return new FileStringLogger( new PrintWriter( new Writer()
        {
            @Override
            public void write( char[] cbuf, int off, int len ) throws IOException
            {
                target.append( cbuf, off, len );
            }

            @Override
            public void write( int c ) throws IOException
            {
                target.appendCodePoint( c );
            }

            @Override
            public void write( char[] cbuf ) throws IOException
            {
                target.append( cbuf );
            }

            @Override
            public void write( String str ) throws IOException
            {
                target.append( str );
            }

            @Override
            public void write( String str, int off, int len ) throws IOException
            {
                target.append( str, off, len );
            }

            @Override
            public Writer append( char c ) throws IOException
            {
                target.append( c );
                return this;
            }

            @Override
            public Writer append( CharSequence csq ) throws IOException
            {
                target.append( csq );
                return this;
            }

            @Override
            public Writer append( CharSequence csq, int start, int end ) throws IOException
            {
                target.append( csq, start, end );
                return this;
            }

            @Override
            public void flush() throws IOException
            {
                // do nothing
            }

            @Override
            public void close() throws IOException
            {
                // do nothing
            }
        } ) );
    }

    @Deprecated
    public void logMessage( String msg )
    {
        logMessage( Level.INFO, msg );
    }

    @Deprecated
    public void logMessage( String msg, Throwable cause )
    {
        logMessage( Level.ERROR, msg, cause );
    }

    public void debug( String msg)
    {
        logMessage( Level.DEBUG, msg );
    }

    public void debug(String msg, Object... args)
    {
        debug( String.format( msg, args ) );
    }

    public void debug( String msg, Throwable throwable )
    {
        logMessage( Level.DEBUG, msg, throwable );
    }

    public void info( String msg)
    {
        logMessage( Level.WARN, msg );
    }

    public void info(String msg, Object... args)
    {
        info( String.format( msg, args ) );
    }

    public void info( String msg, Throwable throwable )
    {
        logMessage( Level.WARN, msg, throwable );
    }

    public void warn( String msg)
    {
        logMessage( Level.WARN, msg );
    }

    public void warn(String msg, Object... args)
    {
        warn( String.format( msg, args ) );
    }

    public void warn( String msg, Throwable throwable )
    {
        logMessage( Level.WARN, msg, throwable );
    }

    public void error( String msg)
    {
        logMessage( Level.WARN, msg );
    }

    public void error(String msg, Object... args)
    {
        error( String.format( msg, args ) );
    }

    public void error( String msg, Throwable throwable )
    {
        logMessage( Level.WARN, msg, throwable );
    }

    public abstract void logMessage(Level level, String msg);

    public abstract void logMessage(Level level, String msg, Throwable throwable);

    @Deprecated
    public void logMessage( String msg, boolean flush )
    {
        info(msg );
    }

    @Deprecated
    public void logMessage( String msg, Throwable cause, boolean flush )
    {
        warn( msg, cause );
    }

    public abstract void close();

    public static final StringLogger DEV_NULL = new StringLogger()
    {
        @Override
        public void logMessage( Level level, String msg )
        {
        }

        @Override
        public void logMessage( Level level, String msg, Throwable throwable )
        {
        }

        @Override
        public void close() {}
    };
}
