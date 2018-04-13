package org.foraci.dolby.anc;

import org.foraci.anc.anc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.EOFException;

/**
 * GXF implementation for <code>Smpte291InputStream</code>
 *
 * @author jforaci
 */
public class Smpte291GxfInputStream extends Smpte291InputStream
{
    private static final Logger log = LoggerFactory.getLogger(Smpte291GxfInputStream.class);

    private AncChecksum checksum = new AncChecksum();

    public Smpte291GxfInputStream(InputStream in, AncTrackReader context)
    {
        super(in, context);
    }

    private void verifyAncPacketWord(int value, int parity)
    {
        int ck = ((parity & 1) << 8) + value;
        checksum.add(ck);
        int count = 0;
        int mask = 1;
        for (int i = 0; i < 8; i++) {
            if ((value & mask) != 0) {
                count++;
            }
            mask = mask << 1;
        }
        if ((parity & 0x01) != (count % 2)) {
            log.warn("bad anc packet word at "
                    + Long.toHexString(context.getPosition()) + ": value=" + Integer.toHexString(value)
                    + ",parity=" + Integer.toHexString(parity));
        }
        if ((parity & 0x01) == (parity & 0x02)) {
            log.warn("bad anc packet word (parity) at "
                    + Long.toHexString(context.getPosition()) + ": value=" + Integer.toHexString(value)
                    + ",parity=" + Integer.toHexString(parity));
        }
    }

    protected void verifyAncPacketChecksumWord(int value, int parity)
    {
//        int checkSumValue = ((parity & 1) << 8) + value;
        int checkSumValue = value; // because GXF from Grass Valley only computes 8-bit checksum
        int found = checksum.sum() & 0xFF;
        if (checkSumValue != found) {
            log.warn("bad anc packet word (checksum) at "
                    + Long.toHexString(context.getPosition()) + ": value=" + Integer.toHexString(checkSumValue)
                    + ", actual=" + Integer.toHexString(checksum.sum())
                    + ",parity=" + (parity & 0x02));
        }
        if ((parity & 0x01) == ((parity >> 1) & 0x01)) {
            log.warn("bad anc packet word (checksum parity) at "
                    + Long.toHexString(context.getPosition()) + ": value=" + Integer.toHexString(checkSumValue)
                    + ",parity=" + (parity & 0x02));
        }
    }

    protected int getChecksum()
    {
        return checksum.sum() & 0xFF;
    }

    public int readWord() throws IOException
    {
        int word = in.read();
        if (word == -1) {
            throw new EOFException();
        }
        int parity = in.read();
        if (parity == -1) {
            throw new EOFException();
        }
        verifyAncPacketWord(word, parity);
        return word;
    }

    public AncPacketHeader readAncPacket() throws IOException
    {
        checksum.reset();
        int did = readWord();
        int sdid = readWord();
        int dataCount = readWord();
        return createAncPacketHeader(did, sdid, dataCount);
    }

    public void skipAncPacket(AncPacketHeader header) throws IOException
    {
        header.skip(this);
        int checkSumValue = in.read();
        verifyAncPacketChecksumWord(checkSumValue, in.read());
        header.setChecksum(getChecksum());
    }

    public AncPacketUserData readAncPacketUserData(AncPacketHeader header) throws IOException
    {
        AncPacketUserData data = header.read(this, context);
        int checkSumValue = in.read();
        verifyAncPacketChecksumWord(checkSumValue, in.read());
        header.setChecksum(getChecksum());
        return data;
    }

    public AncPacketRawUserData readAncPacketRawUserData(AncPacketHeader header) throws IOException
    {
        int[] words = new int[header.getDataCount()];
        for (int i = 0; i < header.getDataCount(); i++) {
            words[i] = readWord();
        }
        int checkSumValue = in.read();
        verifyAncPacketChecksumWord(checkSumValue, in.read());
        header.setChecksum(getChecksum());
        return new AncPacketRawUserData(words);
    }
}
