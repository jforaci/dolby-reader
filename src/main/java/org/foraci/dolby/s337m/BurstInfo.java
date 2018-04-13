package org.foraci.dolby.s337m;

/**
 * Represents data from the burst of a S337 stream
 *
 * @author Joe Foraci
 */
public class BurstInfo
{
    public static final int PREAMBLE_16_W1 = 0xF8720000;
    public static final int PREAMBLE_20_W1 = 0x6F872000;
    public static final int PREAMBLE_24_W1 = 0x96F87200;
    public static final int PREAMBLE_16_W2 = 0x4E1F0000;
    public static final int PREAMBLE_20_W2 = 0x54E1F000;
    public static final int PREAMBLE_24_W2 = 0xA54E1F00;

    public static final int DATA_TYPE_NULL = 0;
    public static final int DATA_TYPE_ATSC_A_52B_AC3_AUDIO = 1;
    public static final int DATA_TYPE_TIME_STAMP_DATA = 2;
    public static final int DATA_TYPE_PAUSE_DATA = 3;
    public static final int DATA_TYPE_MPEG1_LAYER1_AUDIO = 4;
    public static final int DATA_TYPE_MPEG1_LAYER23_DATA_MPEG2_WO_EXT_AUDIO = 5;
    public static final int DATA_TYPE_MPEG2_WITH_EXT = 6;
    public static final int DATA_TYPE_MPEG2_LAYER1_LOW_SAMP_FREQ_AUDIO = 8;
    public static final int DATA_TYPE_MPEG2_LAYER23_LOW_SAMP_FREQ_AUDIO = 9;
    public static final int DATA_TYPE_MPEG4_AAC_DATA = 10;
    public static final int DATA_TYPE_MPEG4_HE_AAC_DATA = 11;
    public static final int DATA_TYPE_ATSC_A_52B_ENHANCED_AC3_AUDIO = 16;
    public static final int DATA_TYPE_UTILITY_DATA_TYPE_V_SYNC = 26;
    public static final int DATA_TYPE_SNPTE_KLV_DATA = 27;
    public static final int DATA_TYPE_DOLBYE = 28;
    public static final int DATA_TYPE_CAPTIONING_DATA = 29;
    public static final int DATA_TYPE_USER_DEFINED_DATA = 30;
    
    private final int streamNumber;
    private final int dataTypeData;
    private final boolean errors;
    private final int dataMode;
    private final int dataType;
    private final int bitLength;

    public BurstInfo(int streamNumber, int dataTypeData, boolean errors, int dataMode, int dataType, int bitLength)
    {
        this.streamNumber = streamNumber;
        this.dataTypeData = dataTypeData;
        this.errors = errors;
        this.dataMode = dataMode;
        this.dataType = dataType;
        this.bitLength = bitLength;
    }

    public int getStreamNumber()
    {
        return streamNumber;
    }

    public int getDataTypeData()
    {
        return dataTypeData;
    }

    public boolean hasErrors()
    {
        return errors;
    }

    public int getDataMode()
    {
        return dataMode;
    }

    public int getDataType()
    {
        return dataType;
    }

    public int getBitLength()
    {
        return bitLength;
    }
}
