package org.foraci.dolby;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A batch helper with simple logging and other utilities
 *
 * @author jforaci
 */
public abstract class ParserHelper
{
    public static final int LOGLEVEL_WARN = 0;
    public static final int LOGLEVEL_INFO = 1;
    public static final int LOGLEVEL_DEBUG = 2;
    
    protected int logLevel = LOGLEVEL_INFO;
    private String levelPrefix = "";

    protected static boolean findArg(String[] args, String name)
    {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    protected static String getArg(String[] args, String name)
    {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(name)) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    public int getLogLevel()
    {
        return logLevel;
    }

    public void setLogLevel(int logLevel)
    {
        this.logLevel = logLevel;
    }

    protected void push()
    {
        levelPrefix += "\t";
    }

    protected void pop()
    {
        if (levelPrefix.length() == 0) {
            return;
        }
        levelPrefix = levelPrefix.substring(0, levelPrefix.length() - 1);
    }

    protected static void log(String message)
    {
        System.out.println(message);
    }

    protected void info(String message)
    {
//        if (!(message.trim().startsWith("frameCounter")
//              || message.trim().startsWith("timecode")
//            )) return;
//        if (true)return;
        if (logLevel < LOGLEVEL_INFO) return;
        System.out.println(levelPrefix + message);
    }

    protected void info(String message, boolean up)
    {
        info(message);
        if (up) push(); else pop();
    }

    protected void warn(String message)
    {
        if (logLevel < LOGLEVEL_WARN) return;
        System.out.println("WARN: " + message);
    }

    protected void debug(String message)
    {
        if (logLevel < LOGLEVEL_DEBUG) return;
        System.out.println(message);
    }

    protected String pad(int i)
    {
        if (i < 10) return "0" + i;
        return Integer.toString(i);
    }

    protected int readIntLe(DataInputStream in) throws IOException
    {
        int i = (in.readByte() & 0xFF);
        i |= ((in.read() & 0xFF) << 8);
        i |= ((in.read() & 0xFF) << 16);
        i |= ((in.read() & 0xFF) << 24);
        return i;
    }

    protected static void skipFully(InputStream in, long len) throws IOException
    {
        do {
            long s = in.skip(len);
            if (s <= 0) {
                return;
            }
            len -= s;
        } while (len > 0);
    }
}
