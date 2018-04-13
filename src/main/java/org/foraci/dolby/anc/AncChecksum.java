package org.foraci.dolby.anc;

/**
 * @author Joe Foraci
 */
public class AncChecksum
{
    private byte sum = 0;

    public AncChecksum()
    {
    }

    public void add(int value)
    {
        sum += value;
    }

    public int sum()
    {
        return (sum & 0x1FF);
    }

    public void reset()
    {
        sum = 0;
    }
}
