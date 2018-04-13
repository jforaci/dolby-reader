package org.foraci.dolby.util.bwave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Creates a stereo Broadcast WAVE file from two input streams
 */
public class BWaveFileMuxer implements Runnable
{
    private static final Logger log = LoggerFactory.getLogger(BWaveFileMuxer.class);

    public static void main(String[] args) throws IOException
    {
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(args[2]));
        BWaveFileMuxer muxer = new BWaveFileMuxer(new File(args[0]), new File(args[1]),
                3, 48000, out);
        muxer.process();
    }

    private DataInputStream in1, in2;
    private final File inputFileChannel1;
    private final File inputFileChannel2;
    private final int sampleSize;
    private final int sampleRate;
    private byte[] sampleBuffer;
    private final DataOutputStream output;

    /**
     * Creates a stereo BWAVE muxer with the given parameters describing the input files
     * @param inputFileChannel1 file for channel 1
     * @param inputFileChannel2 file for channel 2
     * @param sampleSize the sample size, in bytes
     * @param sampleRate the sample rate (e.g. 48,000 Hz)
     * @param output the output stream to write the BWAVE file
     * @throws FileNotFoundException if any input file doesn't exist
     */
    public BWaveFileMuxer(File inputFileChannel1, File inputFileChannel2, int sampleSize, int sampleRate,
                          OutputStream output) throws FileNotFoundException
    {
        this.inputFileChannel1 = inputFileChannel1;
        this.inputFileChannel2 = inputFileChannel2;
        this.sampleSize = sampleSize;
        this.sampleRate = sampleRate;
        this.sampleBuffer = new byte[sampleSize];
        this.output = new DataOutputStream(output);
    }

    private DataInputStream createInputStream(File inputFile) throws FileNotFoundException
    {
        return new DataInputStream(new BufferedInputStream(new FileInputStream(inputFile)));
    }

    public void process() throws IOException
    {
        this.in1 = createInputStream(inputFileChannel1);
        this.in2 = createInputStream(inputFileChannel2);
        long length = checkInput();
        writeHeader32(length);
//        writeHeader64(length);
        long read = 0;
        try {
            while (read < length) {
                byte[] sample = readSample(in1);
                output.write(sample);
                sample = readSample(in2);
                output.write(sample);
                read += sampleSize;
            }
            output.flush();
        } finally {
            output.close();
        }
    }

    private long checkInput() throws IOException
    {
        if (inputFileChannel1.length() != inputFileChannel2.length()) {
            throw new IOException("input files are not the same size");
        }
        return inputFileChannel1.length();
    }

    private void writeHeader32(long length) throws IOException
    {
        short numChannels = 2; // fixed at two channels
        long dataSize = length * numChannels;
        // RIFF chunk
        output.write("RIFF".getBytes("ISO-8859-1"));
        long chunkSize = (int) (dataSize + 36); // assumes PCM fmt chunk
        writeLe((int) chunkSize);
        output.write("WAVE".getBytes("ISO-8859-1"));
        // fmt subchunk
        output.write("fmt ".getBytes("ISO-8859-1"));
        writeLe(16); // 16 == PCM chunk size
        short type = 1; // 1 == PCM
        writeLe(type);
        writeLe(numChannels);
        writeLe(sampleRate);
        int byteRate = sampleRate * sampleSize * numChannels;
        writeLe(byteRate);
        short blockAlign = (short) (numChannels * sampleSize);
        writeLe(blockAlign);
        short bitsPerSample = (short) (sampleSize * 8);
        writeLe(bitsPerSample);
        // data subchunk
        output.write("data".getBytes("ISO-8859-1"));
        writeLe((int) dataSize);
    }

    private void writeHeader64(long length) throws IOException
    {
        short numChannels = 2; // fixed at two channels
        long dataSize = length * numChannels;
        // RIFF chunk
        output.write("RF64".getBytes("ISO-8859-1"));
        writeLe(-1); // indication to look for the true size in the "ds64" chunk
        output.write("WAVE".getBytes("ISO-8859-1"));
        // ds64 subchunk
        output.write("ds64".getBytes("ISO-8859-1"));
        writeLe(28);
        writeLe(dataSize + 72); // assumes PCM fmt chunk and ds64 chunk with zero size extra chunk table
        writeLe(dataSize);
        long sampleCount = length / sampleSize;
        writeLe(sampleCount);
        writeLe(0);
        // fmt subchunk
        output.write("fmt ".getBytes("ISO-8859-1"));
        writeLe(16); // 16 == PCM chunk size
        short type = 1; // 1 == PCM
        writeLe(type);
        writeLe(numChannels);
        writeLe(sampleRate);
        int byteRate = sampleRate * sampleSize * numChannels;
        writeLe(byteRate);
        short blockAlign = (short) (numChannels * sampleSize);
        writeLe(blockAlign);
        short bitsPerSample = (short) (sampleSize * 8);
        writeLe(bitsPerSample);
        // data subchunk
        output.write("data".getBytes("ISO-8859-1"));
        writeLe(-1); // indication to look for the true size in the "ds64" chunk
    }

    private void writeLe(long value) throws IOException
    {
        writeLe((int) value);
        writeLe((int) (value >> 32));
    }

    private void writeLe(int value) throws IOException
    {
        output.write(value & 0xFF);
        output.write((value >> 8) & 0xFF);
        output.write((value >> 16) & 0xFF);
        output.write((value >> 24) & 0xFF);
    }

    private void writeLe(short value) throws IOException
    {
        output.write(value & 0xFF);
        output.write((value >> 8) & 0xFF);
    }

    private byte[] readSample(DataInputStream in) throws IOException
    {
        in.readFully(sampleBuffer);
        return sampleBuffer;
    }

    public void run()
    {
        try {
            process();
        } catch (IOException e) {
            log.error("I/O error", e);
        }
    }
}
