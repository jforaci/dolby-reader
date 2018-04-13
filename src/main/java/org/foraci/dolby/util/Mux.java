package org.foraci.dolby.util;

import org.foraci.dolby.ParserHelper;

import java.io.*;

/**
 * Simple program to mux together two files
 *
 * @author jforaci
 */
public class Mux extends ParserHelper
{
    private static BufferedOutputStream out = null;

    public static void main(String[] args) throws IOException
    {
        final int buffSize = 8* 1024;
        BufferedInputStream in1 = new BufferedInputStream(new FileInputStream(new File(args[0])), buffSize);
        BufferedInputStream in2 = new BufferedInputStream(new FileInputStream(new File(args[1])), buffSize);
        out = new BufferedOutputStream(new FileOutputStream("out.mux"));
        int sampleSize = Integer.parseInt(args[2]);
        long offset = Long.parseLong(args[3]);
        long len = Long.parseLong(args[4]);
        skipFully(in1, offset);
        skipFully(in2, offset);
        do {
            out.write(0);
            for (int i = 0; i < sampleSize; i++) {
                int b = in1.read();
                if (b == -1) throw new EOFException();
                out.write(b);
            }
            out.write(0);
            for (int i = 0; i < sampleSize; i++) {
                int b = in2.read();
                if (b == -1) throw new EOFException();
                out.write(b);
            }
            len -= sampleSize;
        } while (len > 0);
    }
}
