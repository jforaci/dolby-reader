package org.foraci.dolby;

import org.foraci.anc.util.io.CountingInputStream;
import org.foraci.anc.util.io.MultiplexingInputStream;
import org.foraci.anc.util.timecode.NtscConverter;
import org.foraci.dolby.dolbye.ProgramConfig;
import org.foraci.dolby.s337m.BurstInfo;

import java.io.*;

/**
 * This is a Dolby E reader that can read 337M-wrapped Dolby E frames from a single file
 * or two files (as output from GxfReader 1/2 or 3/4 audio tracks).
 *
 * @author jforaci
 */
public class DolbyEReader extends ParserHelper
{
    private static final int EXIT_BAD_ARGS = -1;
    private static final String ARG_IN1 = "-1";
    private static final String ARG_IN2 = "-2";
    private static final String ARG_OFFSET = "-offset";
    private static final String ARG_ALIGN = "-align";
    private static final String ARG_AESPROBE = "-aesprobe";
    private static final String ARG_SAMPLE_SIZE = "-sample";
    private static final String ARG_LOG_LEVEL = "-v";

    public static final int E_SYNC_16 = 0x78E;
    public static final int E_SYNC_20 = 0x788E;
    public static final int E_SYNC_24 = 0x7888E;

    private DataInputStream in;
    private int sampleSize;
    private boolean subframeMode;
    private int frameCount;
    private int eBitDepth = 0;

    public DolbyEReader(DataInputStream in, int sampleSize, boolean subframeMode)
    {
        this.in = in;
        this.sampleSize = sampleSize;
        this.subframeMode = subframeMode;
        this.frameCount = 0;
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length == 0) {
            System.err.println("No file specified; specify with: -1 filename [-2 filename2]");
            System.exit(EXIT_BAD_ARGS);
        }
        final int buffSize = 8 * 1024;
        CountingInputStream cin1 = null, cin2 = null;
        String first = getArg(args, ARG_IN1);
        if (first == null || "-".equals(first)) {
            System.err.println("No file(s) specified; specify with: -1 filename [-2 filename2]");
            System.exit(EXIT_BAD_ARGS);
        }
        File firstFile = new File(first);
        DataInputStream in1 = new DataInputStream(cin1 = new CountingInputStream(new BufferedInputStream(new FileInputStream(firstFile), buffSize)));
        DataInputStream in2 = null;
        String second = getArg(args, ARG_IN2);
        File secondFile = null;
        if (second != null && !"-".equals(second)) {
            secondFile = new File(second);
            in2 = new DataInputStream(cin2 = new CountingInputStream(new BufferedInputStream(new FileInputStream(secondFile), buffSize)));
        }
        boolean align = findArg(args, ARG_ALIGN); // whether to align to the first non-zero byte in the input(s)
        boolean probe = findArg(args, ARG_AESPROBE); // whether to probe for the next 337M burst preamble in the input(s)
        if (align && probe) {
            System.err.println("You can not specify both " + ARG_ALIGN + " and " + ARG_AESPROBE);
            System.exit(EXIT_BAD_ARGS);
        }
        int sampleSize = (findArg(args, ARG_SAMPLE_SIZE)) ? Integer.parseInt(getArg(args, ARG_SAMPLE_SIZE)) : ((in2 == null) ? 4 : 3); // seems to be 3 for two separate GXF tracks; 4 for one .e file from DP600 (seems only the higher 3 bytes are used though)
        boolean subframeMode = (in2 != null); // true for two separate GXF track inputs
        MultiplexingInputStream in = new MultiplexingInputStream(in1, in2, sampleSize);
        long offset = (findArg(args, ARG_OFFSET)) ? Long.parseLong(getArg(args, ARG_OFFSET)) : 0;
        skipFully(in, offset);
        DolbyEReader reader = new DolbyEReader(in, sampleSize, subframeMode);
        if (findArg(args, ARG_LOG_LEVEL)) {
            int logLevel = Integer.parseInt(getArg(args, ARG_LOG_LEVEL));
            reader.setLogLevel(logLevel);
        }
        try {
            //in.align();
            while (true) {
                if (align) {
                    in.align();
                }
                if (probe) {
                    reader.probeForAESFrame();
                }
                reader.readFrame();
            }
        } catch (EOFException e) {
            if (cin1 != null) {
                log("1st stream at EOF: " + (cin1.getPosition() == firstFile.length()));
            }
            if (cin2 != null) {
                log("2nd stream at EOF: " + (cin2.getPosition() == secondFile.length()));
            }
        } catch (Exception e) {
            if (cin1 != null) {
                log("Exception at cin1=" + cin1.getPosition());
            }
            if (cin2 != null) {
                log("Exception at cin2=" + cin2.getPosition());
            }
            throw e;
        } finally {
            log("last frame is " + reader.getFrameDescription(reader.getFrameCount()));
        }
    }

    public int getFrameCount()
    {
        return frameCount;
    }

    private String getFrameDescription(int frame)
    {
        return "" + frame + " -> " + new NtscConverter().convertFromFrames(frame * 2, false);
    }

    public void readPreamble() throws IOException
    {
        int depth;
        int preamble = readIntLe(in);
        // assume it's written as little-endian
        if (preamble == BurstInfo.PREAMBLE_16_W1) {
            depth = 16;
        } else if (preamble == BurstInfo.PREAMBLE_20_W1) {
            depth = 20;
        } else if (preamble == BurstInfo.PREAMBLE_24_W1) {
            depth = 24;
        } else {
            throw new IOException("bad preamble");
        }
        preamble = readIntLe(in);
        //check against second preamble word
        boolean ok = false;
        if (preamble == BurstInfo.PREAMBLE_16_W2) {
            ok = true;
            if (depth != 16) {
                throw new IOException("bad preamble for " + eBitDepth + "-bit mode");
            }
        }
        if (preamble == BurstInfo.PREAMBLE_20_W2) {
            ok = true;
            if (depth != 20) {
                throw new IOException("bad preamble for " + eBitDepth + "-bit mode");
            }
        }
        if (preamble == BurstInfo.PREAMBLE_24_W2) {
            ok = true;
            if (depth != 24) {
                throw new IOException("bad preamble for " + eBitDepth + "-bit mode");
            }
        }
        if (!ok) {
            throw new IOException("bad preamble (2nd word)");
        }
        eBitDepth = depth;
    }

    public void probeForAESFrame() throws IOException
    {
        probeForAESFrame(1024 * 1024 * 9);
    }
    
    public void probeForAESFrame(final int limit) throws IOException
    {
        int pos = 0;
        in.mark(8);
        while (pos <= limit) {
            int test = readIntLe(in);
            if (test == 0) {
                in.mark(8);
                pos += sampleSize;
                continue;
            }
            in.reset();
            in.mark(8);
            try {
                readPreamble();
                in.reset();
                final int BAND_SIZE_THRESHOLD = 243 * 2;
                if (pos > BAND_SIZE_THRESHOLD) {
                    warn("probed for " + pos + " bytes before finding an AES frame!");
                    warn("\tlooking for frame " + getFrameDescription(getFrameCount() + 1));
                }
                return;
            } catch (IOException ioe) {
                in.reset();
                skipFully(in, 1);
                in.mark(8);
                pos += ((subframeMode) ? 2 : 1); // since we skip a byte in all streams, this gets incremented by one for each (i.e. 1 for one stream, 2 if muxing together two streams)
            }
        }
        throw new IOException("no preamble found");
    }

    public BurstInfo readBurstInfo() throws IOException
    {
        int word = readIntLe(in);
        int streamNumber = ((word >> 29) & 0x7);
        int dataTypeData = ((word >> 24) & 0x1F);
        boolean errors = (((word >> 23) & 0x1) == 1);
        int dataMode = ((word >> 21) & 0x3);
        int dataType = ((word >> 16) & 0x1F);
        word = readIntLe(in);
        int bitLength = ((word >> 12) & 0xFFFFF);
        if (dataTypeData != 0) {
            warn("dataTypeData is not zero: " + dataTypeData);
        }
        if (dataMode != 1) {
            warn("dataMode is not 1: " + dataMode);
        }
        if (dataType != 28) {
            warn("dataType is not 28: " + dataType);
        }
        BurstInfo info = new BurstInfo(streamNumber, dataTypeData, errors, dataMode, dataType, bitLength);
        return info;
    }

    public void readFrame() throws IOException
    {
        info("AES frame:", true);
        readPreamble();
        BurstInfo info = readBurstInfo();
        info("streamNumber: " + info.getStreamNumber());
        info("dataTypeData: " + info.getDataTypeData());
        info("errors: " + info.hasErrors());
        info("dataMode: " + info.getDataMode());
        info("dataType: " + info.getDataType());
        info("bitLength: " + info.getBitLength());
        pop();
        debug("AES frame end");
        if (info.hasErrors()) {
            warn("errors in AES payload");
        }
        if (info.getDataType() != BurstInfo.DATA_TYPE_DOLBYE) {
            throw new IOException("AES data type is not Dolby E");
        }

        readE(info);

//        skipFully(in, dataLength);

        frameCount++;
    }

    private int lastFrame = -1;
    private int wasdrop = -1;

    private void readE(BurstInfo info) throws IOException
    {
        info("Dolby E frame:", true);
        int dataLength = (info.getBitLength() / eBitDepth) * sampleSize; // in bytes: samples are packed in sampleSize-byte words
        if (subframeMode) {
            if (dataLength % 2 == 1) {
                throw new IOException("odd data length in subframe mode");
            }
            dataLength /= 2;
        }
        info("dataLength: " + dataLength + " bytes");

        reset();
        readSync();
        resetCrcWord();
        readMetadata();
        readFrameDist();
        readProgramMetadata();
        readChannelMetadata();
        readMetadataSubsegments();
        skipAudioSegment();
        if (lowFrameRate) {
            readMetadataExtSubsegments();
            skipAudioExtSegment();
        }
        readMeterSegment();
        info("Dolby E frame end", false);

        // skip the rest of the AES payload
        if (subframeMode) {
            eByteCount = eByteCount / 2;
        }
        if (dataLength - eByteCount < 0) {
            throw new IllegalStateException("over-read of E: " + eByteCount + " bytes; calculated 337M payload length is " + dataLength);
        }
        skipFully(in, dataLength - eByteCount);
    }

    private int[] peakMeter;
    private int[] rmsMeter;

    private void readMeterSegment() throws IOException
    {
        info("meter segment:", true);
        readKey();
        peakMeter = new int[config.getChannels()];
        for (int c = 0; c < config.getChannels(); c++) {
            peakMeter[c] = getEBits(10);
            info("peakMeter[" + c + "]: " + peakMeter[c]);
        }
        rmsMeter = new int[config.getChannels()];
        for (int c = 0; c < config.getChannels(); c++) {
            rmsMeter[c] = getEBits(10);
            info("rmsMeter[" + c + "]: " + rmsMeter[c]);
        }
        readReserved(meterSegmentSize);
        readCrc();
        pop();
        debug("meter segment end");
    }

    private void skipAudioExtSegment() throws IOException
    {
        debug("skipping audio ext segment...");
        readKey();
        for (int c = 0; c < (config.getChannels() / 2); c++) {
            for (int n = 0; n < channelSizes[c]; n++) {
                getEBits(eBitDepth);
            }
        }
        readCrc();
        readKey();
        for (int c = (config.getChannels() / 2); c < config.getChannels(); c++) {
            for (int n = 0; n < channelSizes[c]; n++) {
                getEBits(eBitDepth);
            }
        }
        readCrc();
        debug("skipping audio ext segment end");
    }

    private void readMetadataExtSubsegments() throws IOException
    {
        info("metadata ext subsegments:", true);
        readKey();
        while (true) {
            int metadataSubsegmentId = getEBits(4);
            if (metadataSubsegmentId == 0) {
                debug("end of metadata ext subsegments");
                break;
            }
            int metadataSubsegmentLen = getEBits(12);
            info("metadata ext subsegment " + metadataSubsegmentId + " (" + metadataSubsegmentLen + ") bits");
            int toread = metadataSubsegmentLen;
            if (metadataSubsegmentId == 1) {
                info("AC3+XBSI");
                readAc3ExtMetadataXBsi();
                continue;
            }
            while (toread > 0) {
                if (toread >= 32) {
                    getEBits(32);
                    toread -= 32;
                } else {
                    getEBits(toread);
                    toread = 0;
                }
            }
        }
        readReserved(metadataExtSegmentSize);
        readCrc();
        pop();
        debug("metadata ext subsegments end");
    }

    private void readAc3ExtMetadataXBsi() throws IOException
    {
        for (int p = 0; p < config.getPrograms(); p++) {
            int compr2 = getEBits(8);
            int[] dynrng = new int[4];
            for (int r = 0; r < 4; r++) {
                dynrng[r] = getEBits(8);
            }
        }
    }

    private void skipAudioSegment() throws IOException
    {
        debug("skipping audio...");
        readKey();
        for (int c = 0; c < (config.getChannels() / 2); c++) {
            for (int n = 0; n < channelSizes[c]; n++) {
                getEBits(eBitDepth);
            }
        }
        readCrc();
        readKey();
        for (int c = (config.getChannels() / 2); c < config.getChannels(); c++) {
            for (int n = 0; n < channelSizes[c]; n++) {
                getEBits(eBitDepth);
            }
        }
        readCrc();
        debug("skipping audio end");
    }

    private void readSync() throws IOException
    {
        info("frame sync:", true);
        int word = getEBits(eBitDepth);
        int sync = (word & 0xFFFFFFFE);
        if ((word & 0x1) == 1) { // check if XOR key is present (unsupported)
            keyPresent = true;
            readKey();
        }
        if ((eBitDepth == 16 && sync != E_SYNC_16)
                || (eBitDepth == 20 && sync != E_SYNC_20)
                || (eBitDepth == 24 && sync != E_SYNC_24)) {
            throw new IOException("BAD SYNC: " + Integer.toHexString(sync));
        }
        info("bit depth: " + eBitDepth);
        pop();
        debug("frame sync end");
    }

    private void readMetadataSubsegments() throws IOException
    {
        info("metadata subsegments:", true);
        while (true) {
            int metadataSubsegmentId = getEBits(4);
            if (metadataSubsegmentId == 0) {
                debug("end of metadata subsegments");
                break;
            }
            int metadataSubsegmentLen = getEBits(12);
            info("metadata subsegment " + metadataSubsegmentId + " (" + metadataSubsegmentLen + ") bits");
            int toread = metadataSubsegmentLen;
            if (metadataSubsegmentId == 1) {
                info("AC3+XBSI");
                readAc3MetadataXBsi();
                continue;
            }
            if (metadataSubsegmentId == 2) {
                info("AC3 NO XBSI");
                readAc3MetadataNoXBsi(); // TODO: implement, most of the same fields as from readAc3MetadataXBsi
                continue;
            }
            while (toread > 0) {
                if (toread >= 32) {
                    getEBits(32);
                    toread -= 32;
                } else {
                    getEBits(toread);
                    toread = 0;
                }
            }
        }
        readReserved(metadataSize);
        readCrc();
        pop();
        debug("metadata subsegments end");
    }

    private void readReserved(int metadataLength) throws IOException
    {
        int reserved = getEBits(ebitsLeft);
        while ((metadataLength * eBitDepth) - eBitsReadTotal > 0) {
            reserved = getEBits(eBitDepth);
        }
    }

    private void readAc3MetadataNoXBsi() throws IOException
    {
        for (int i = 0; i < config.getPrograms(); i++) {
            info("program " + i, true);
            int datarate = getEBits(5);
            int bsmod = getEBits(3);
            int acmod = getEBits(3);
            int cmixlev = getEBits(2);
            int surmixlev = getEBits(2);
            int dsurmod = getEBits(2);
            int lfeon = getEBits(1);
            int dialnorm = getEBits(5);
            info("dialnorm: " + dialnorm);
            int langcode = getEBits(1);
            int langcod = getEBits(8);
            int audprodie = getEBits(1);
            int mixlevel = getEBits(5);
            int roomtyp = getEBits(2);
            int copyrightb = getEBits(1);
            int origbs = getEBits(1);
            // 42 so far

            int timecod1e = getEBits(1);
//            if (timecod1e == 1) {
                int timecod1 = getEBits(14);
//            }

            int timecod2e = getEBits(1);
//            if (timecod2e == 1) {
                int timecod2 = getEBits(14);
//            }

            int hpfon = getEBits(1);
            int bwlpfon = getEBits(1);
            int lfelpfon = getEBits(1);
            int sur90on = getEBits(1);
            int suratton = getEBits(1);
            int rfpremphon = getEBits(1);
            int compre = getEBits(1);
            int compr1 = getEBits(8);
            int dynrnge = getEBits(1);
            int[] dynrng = new int[4];
            for (int r = 0; r < 4; r++) {
                dynrng[r] = getEBits(8);
            }
            // 40 since hpfon
        }
        for (int i = 0; i < config.getPrograms(); i++) {
            int addbsie = getEBits(1);
            if (addbsie == 1) {
                int addbsil = getEBits(6);
                int toread = (addbsil + 1) * 8;
                while (toread > 0) {
                    if (toread >= 32) {
                        getEBits(32);
                        toread -= 32;
                    } else {
                        getEBits(toread);
                        toread = 0;
                    }
                }
            }
        }
    }

    private void readAc3MetadataXBsi() throws IOException
    {
        for (int i = 0; i < config.getPrograms(); i++) {
            info("program " + i, true);
            int datarate = getEBits(5);
            if (datarate >= 19 && datarate <= 30) {
                warn("datarate: RESERVED value used");
            } else if (datarate == 31) {
                info("datarate: not specified");
            } else {
                final int[] dataRateLut = { 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640 };
                info("datarate: " + dataRateLut[datarate] + " kbps");
            }
            final String[] bsmodLut = { "main audio service: complete main (CM)", "main audio service: music and effects (ME)", "associated service: visually impaired (VI)",
                    "associated service: hearing impaired (HI)", "associated service: dialog (D)", "\tassociated service: commentary (C)",
                    "associated service: emergency (E)" };
            int bsmod = getEBits(3);
            int acmod = getEBits(3);
            if (bsmod != 7) {
                info("bsmod: " + bsmodLut[bsmod]);
            } else if (acmod == 1) {
                info("bsmod: associated service: voice over (VO)");
            } else if (acmod == 2 || acmod == 7) {
                info("bsmod: main audio service: karaoke");
            } else {
                warn("bsmod: bad value with acmod (" + bsmod + ")");
            }
            if (acmod == 0) {
                warn("acmod should not be zero (mono 1+1)");
            }
            info("acmod: " + acmod);
            final String[] cmixlevLut = { "0.707 (-3.0 dB)", "0.595 (-4.5 dB)", "0.500 (-6.0 dB)", "RESERVED" };
            info("cmixlev: " + cmixlevLut[getEBits(2)]);
            final String[] surmixlevLut = { "0.707 (-3.0 dB)", "0.500 (-6.0 dB)", "0 (0 dB)", "RESERVED" };
            info("surmixlev: " + surmixlevLut[getEBits(2)]);
            final String[] dsurmodLut = { "not indicated", "Not Dolby Surround encoded", "Dolby Surround encoded", "RESERVED" };
            info("dsurmod: " + dsurmodLut[getEBits(2)]);
            info("lfeon: " + ((getEBits(1) == 1) ? "on" : "off"));
            info("dialnorm: " + getEBits(5));
            info("langcode (RESERVED): " + getEBits(1));
            info("langcod (RESERVED): " + getEBits(8));
            info("audprodie: " + getEBits(1));
            info("\tmixlevel: " + getEBits(5));
            info("\troomtyp: " + getEBits(2));
            info("copyrightb: " + getEBits(1));
            info("origbs: " + getEBits(1));
            // 42 so far

            int xbsi1e = getEBits(1);
//            if (xbsi1e == 1) {
            String[] dmixmodLut = { "not indicated", "Lt/Rt downmix preferred", "Lo/Ro downmix preferred", "RESERVED" };
            info("dmixmod: " + dmixmodLut[getEBits(2)]);
            String[] dmixlevLut = { "1.414 (+3 dB)", "1.189 (+1.5 dB)", "1 (0 dB)", "0.841 (-1.5 dB)", "0.707 (-3 dB)", "0.595 (-4.5 dB)", "0.5 (-6 dB)", "0 (-\u221e dB)" };
            info("\tltrtcmixlev: " + dmixlevLut[getEBits(3)]);
            info("\tltrtsurmixlev: " + dmixlevLut[getEBits(3)]);
            info("\tlorocmixlev: " + dmixlevLut[getEBits(3)]);
            info("\tlorosurmixlev: " + dmixlevLut[getEBits(3)]);
//            }

            int xbsi2e = getEBits(1);
//            if (xbsi2e == 1) {
            String[] dsurexmodLut = { "not indicated", "Not Dolby Digital Surround EX encoded", "Dolby Digital Surround EX encoded", "RESERVED" };
            info("dsurexmod: " + dsurexmodLut[getEBits(2)]);
            String[] dheadphonmodLut = { "not indicated", "Not Dolby Headphone encoded", "Dolby Headphone encoded", "RESERVED" };
            info("dheadphonmod: " + dheadphonmodLut[getEBits(2)]);
            info("adconvtyp: " + ((getEBits(1) == 1) ? "HDCD" : "Standard"));
            info("xbsi2 (RESERVED): " + getEBits(8));
            info("encinfo (RESERVED): " + getEBits(1));
//            }

            info("hpfon: " + ((getEBits(1) == 1) ? "on" : "off"));
            info("bwlpfon: " + ((getEBits(1) == 1) ? "on" : "off"));
            info("lfelpfon: " + ((getEBits(1) == 1) ? "on" : "off"));
            info("sur90on: " + ((getEBits(1) == 1) ? "on" : "off"));
            info("suratton: " + ((getEBits(1) == 1) ? "on" : "off"));
            info("rfpremphon: " + ((getEBits(1) == 1) ? "on" : "off"));
            int compre = getEBits(1);
            info("compre: " + compre);
            final String[] rfcompr1Lut = { "None", "Film Standard", "Film Light", "Music Standard", "Music Light", "Speech" };
            int compr1 = getEBits(8);
            if (compre == 0) {
                if (compre < 6) {
                    info("compr1: " + rfcompr1Lut[compr1]);
                } else {
                    warn("compr1: RESERVED");
                }
            } else {
                info("compr1: " + compr1);
            }
            int dynrnge = getEBits(1);
            int[] dynrng = new int[4];
            for (int r = 0; r < 4; r++) {
                dynrng[r] = getEBits(8);
                if (dynrnge == 0) {
                    if (dynrnge < 6) {
                        info("dynrng" + (r + 1) + ": " + rfcompr1Lut[dynrng[r]]);
                    } else {
                        warn("dynrng" + (r + 1) + ": RESERVED");
                    }
                } else {
                    info("dynrng" + (r + 1) + ": " + dynrng[r]);
                }
            }
            pop();
            // 40 since hpfon
        }
        for (int i = 0; i < config.getPrograms(); i++) {
            int addbsie = getEBits(1);
            if (addbsie == 1) {
                int addbsil = getEBits(6);
                int toread = (addbsil + 1) * 8;
                while (toread > 0) {
                    if (toread >= 32) {
                        getEBits(32);
                        toread -= 32;
                    } else {
                        getEBits(toread);
                        toread = 0;
                    }
                }
            }
        }
    }

    private void readChannelMetadata() throws IOException
    {
        info("channel metadata:", true);
        for (int i = 0; i < config.getChannels(); i++) {
            int revisionId = getEBits(4);
//            log("revision: " + revisionId);
            boolean bitpool = (getEBits(1) == 1);
//            log("bitpool: " + bitpool);
            int beginGain = getEBits(10);
            info("beginGain[" + i + "]: 0x" + Integer.toHexString(beginGain));
            int endGain = getEBits(10);
            info("endGain[" + i + "]: 0x" + Integer.toHexString(endGain));
        }
        pop();
        debug("channel metadata end");
    }

    private StringBuffer sb = new StringBuffer();
    private int descState = 0;

    private void readProgramMetadata() throws IOException
    {
        info("program metadata:", true);
        for (int i = 0; i < config.getPrograms(); i++) {
            int ci = getEBits(8);
            if (ci != 0) {
                if (ci == 0x2) {
                    descState = 1;
                } else if (ci == 0x3) {
                    descState = 0;
                    info("desc text: " + sb.toString());
                    sb.setLength(0);
                } else if (descState == 1 && ci >= 0x20 && ci <= 0x7E) {
                    sb.append((char)ci);
                }
            }
            int bandwidthId = getEBits(2);
        }
        pop();
        debug("program metadata end");
    }

    private int[] channelSizes;
    private int metadataExtSegmentSize;
    private int meterSegmentSize;

    private void readFrameDist() throws IOException
    {
        info("frame distribution:", true);
        channelSizes = new int[config.getChannels()];
        for (int i = 0; i < channelSizes.length; i++) {
            channelSizes[i] = getEBits(10);
            info("channelSize[" + i + "]: " + channelSizes[i]);
        }
        if (lowFrameRate) {
            metadataExtSegmentSize = getEBits(8);
            info("metadataExtSegmentSize: " + metadataExtSegmentSize);
        }
        meterSegmentSize = getEBits(8);
        info("meterSegmentSize: " + meterSegmentSize);
        pop();
        debug("frame distribution end");
    }

    private boolean lowFrameRate = true;
    private int metadataSize;
    private ProgramConfig config;

    private void readMetadata() throws IOException
    {
        NtscConverter converter = new NtscConverter();
        info("metadata segment:", true);
        int metadataId = getEBits(4);
        info("metadataId: " + metadataId);
        metadataSize = getEBits(10);
        info("metadataSize: " + metadataSize);
        int programConfig = getEBits(6);
        config = ProgramConfig.valueOf(programConfig);
        info("program config: " + config);
        int frameRateCode = getEBits(4);
        if (frameRateCode == 0 || frameRateCode > 8) {
            throw new IllegalStateException("reserved frame rate code");
        }
        lowFrameRate = (frameRateCode >= 1 && frameRateCode <= 5);
        info("lowFrameRate: " + lowFrameRate);
        int originalFrameRateCode = getEBits(4);
        int frameCounter = getEBits(16);
        info("frameCounter: " + frameCounter);
        int unknown = getEBits(10);
        int hh = getEBits(2);
        int h = getEBits(4);
        int unknown1 = getEBits(9);
        int mm = getEBits(3);
        int m = getEBits(4);
        int unknown2 = getEBits(9);
        int ss = getEBits(3);
        int s = getEBits(4);
        int unknown3 = getEBits(9);
        boolean dropFrame = (getEBits(1) == 1);
        int ff = getEBits(2);
        int f = getEBits(4);
        String currentTimecode;
        if (hh == 0x3 && h == 0xF) {
            currentTimecode = "[Marked invalid]";
        } else {
            currentTimecode = pad(hh * 10 + h) + ":" + pad(mm * 10 + m) + ":" + pad(ss * 10 + s) + "." + pad(ff * 10 + f);
        }
        info("timecode: " + currentTimecode);
        if (hh != 3 || h != 15) {
            if (dropFrame) {
                if (wasdrop != 1) {
                    info("dropframe ACTIVE at " + currentTimecode);
                    wasdrop = 1;
                }
            } else {
                if (wasdrop != 0) {
                    info("dropframe OFF    at " + currentTimecode);
                    wasdrop = 0;
                }
            }
            if (lastFrame != -1) {
                String expectedTimecode = converter.convertFromFrames(lastFrame + 2, false);
                if (!expectedTimecode.equals(currentTimecode)) {
                    warn("bad tc, found " + currentTimecode + ", expected " + expectedTimecode);
                }
            }
            lastFrame = converter.convertToFrames(currentTimecode, false);
        } else {
            wasdrop = -1;
        }
        int reserved = getEBits(8);
        pop();
        debug("metadata segment end");
    }

    private int ebitsLeft = 0; // offset counter for getEBits()
    private int ebitsWord = 0; // buffer for getEBits()
    private boolean keyPresent = false;
    private int ekey = 0;
    private int eByteCount = 0;
    private int eBitsReadTotal = 0;
    private long eCrcWord = 0;

    private void reset()
    {
        eByteCount = 0;
        ebitsLeft = 0;
        ebitsWord = 0;
        ekey = 0;
    }

    private void resetCrcWord()
    {
        eCrcWord = 0;
    }

    private int getEBits(int n) throws IOException
    {
        int v = 0;
        if (n > 32) { // let's pretend v is arbitrary size...
            throw new RuntimeException("getEBits not implemented for size > 32");
        }
        while (n > ebitsLeft) {
            int mask = (ebitsLeft == 32) ? 0xFFFFFFFF : (1 << ebitsLeft) - 1;
            v |= ((ebitsWord & mask) << (n - ebitsLeft));
//            v = (v << (n - ebitsLeft));
            eBitsReadTotal += ebitsLeft;
            n -= ebitsLeft;
            ebitsWord = readIntLe(in);
            eByteCount += sampleSize;
            ebitsWord = ebitsWord >>> (32 - eBitDepth);
            if (keyPresent) {
                ebitsWord ^= ekey;
            }
            updateCrc(ebitsWord);
            ebitsLeft = eBitDepth;
        }
        int mask = (n == 32) ? 0xFFFFFFFF : (1 << n) - 1;
        v |= ((ebitsWord >>> (ebitsLeft - n)) & mask);
        eBitsReadTotal += n;
        ebitsLeft -= n;
        return v;
    }

    private void updateCrc(int message)
    {
        final int n = 16;
        long d = 0x18005L; // 0x1C002 // 0x1A001
        final int shift = eBitDepth + n - 1;
        long mask = 0x1L << shift;
        long m = (eCrcWord << eBitDepth) | (message);
        int s = shift - n;
        d = d << s;
        do {
            if ((m & mask) != 0) {
                m = d ^ m;
            }
            mask = mask >>> 1;
            d = d >>> 1;
            s--;
        } while (s >= 0);
        eCrcWord = m;
    }

    private void readCrc() throws IOException
    {
        int crc = getEBits(eBitDepth);
        if (eCrcWord != 0) {
            warn("bad crc: stored " + Integer.toHexString(crc) + ", calc " + Long.toHexString(eCrcWord));
            warn("\tframe is " + getFrameDescription(frameCount + 1));
        }
        resetCrcWord();
    }

    private void readKey() throws IOException
    {
        if (!keyPresent) {
            eBitsReadTotal = 0;
            return;
        }
        ekey = 0;
        ekey = getEBits(eBitDepth);
        eBitsReadTotal = 0;
        resetCrcWord();
    }

    protected int readIntLe(DataInputStream in) throws IOException
    {
        int b = (sampleSize == 3) ? 0 : in.read();
        if (b == -1) throw new EOFException();
        int i = (b & 0xFF);
        b = in.read();
        if (b == -1) throw new EOFException();
        i |= ((b & 0xFF) << 8);
        b = in.read();
        if (b == -1) throw new EOFException();
        i |= ((b & 0xFF) << 16);
        b = in.read();
        if (b == -1) throw new EOFException();
        i |= ((b & 0xFF) << 24);
        return i;
    }
}
