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

import java.util.ArrayList;
import java.util.List;

/**
 * Buffers all messages sent to it, and is able to 
 * replay those messages into another StringLogger.
 * 
 * This can be used to start up services that need logging
 * when they start, but where, for one reason or another, we
 * have not yet set up proper logging in the application lifecycle.
 * 
 * This will replay messages in the order they are recieved, *however*, 
 * it will not preserve the time stamps of the original messages.
 * 
 * You should not use this for logging messages where the time stamps are 
 * important.
 */
public class BufferingLogger extends StringLogger {

    private static class LogMessage 
    {
        public LogMessage(Level level, String message, Throwable throwable) 
        {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }
        
        public Level level;
        public String message;
        public Throwable throwable;
    }
    
    private List<LogMessage> buffer = new ArrayList<LogMessage>();
    
    @Override
    public void logMessage(Level level, String msg)
    {
        logMessage(level, msg, null);
    }

    @Override
    public void logMessage(Level level, String msg, Throwable throwable)
    {
        buffer.add(new LogMessage(level, msg, throwable));
    }
    
    /**
     * Replays buffered messages and clears the buffer.
     */
    public void replayInto(StringLogger other) 
    {
        List<LogMessage> oldBuffer = buffer;
        buffer = new ArrayList<LogMessage>();
        
        for(LogMessage message : oldBuffer) 
        {
            other.logMessage(message.level, message.message, message.throwable);
        }
    }

    @Override
    public void close()
    {
        // no-op
    }

}
