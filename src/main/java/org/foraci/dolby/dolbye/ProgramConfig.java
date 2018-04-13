package org.foraci.dolby.dolbye;

/**
 * Represents the audio program configuration found in AC3 metadata
 *
 * @author Joe Foraci
 */
public class ProgramConfig
{
    private static final ProgramConfig[] configs = {
            new ProgramConfig(8, 2),
            new ProgramConfig(8, 3),
            new ProgramConfig(8, 2),
            new ProgramConfig(8, 3),
            new ProgramConfig(8, 4),
            new ProgramConfig(8, 5),
            new ProgramConfig(8, 4),
            new ProgramConfig(8, 5),
            new ProgramConfig(8, 6),
            new ProgramConfig(8, 7),
            new ProgramConfig(8, 8),
            new ProgramConfig(6, 1),
            new ProgramConfig(6, 2),
            new ProgramConfig(6, 3),
            new ProgramConfig(6, 3),
            new ProgramConfig(6, 4),
            new ProgramConfig(6, 5),
            new ProgramConfig(6, 6),
            new ProgramConfig(4, 1),
            new ProgramConfig(4, 2),
            new ProgramConfig(4, 3),
            new ProgramConfig(4, 4),
            new ProgramConfig(8, 1),
            new ProgramConfig(8, 1),
    };

    public static ProgramConfig valueOf(int configId)
    {
        if (configId < 0 || configId  > 63) {
            throw new IllegalArgumentException("bad program config: " + configId);
        }
        if (configId >= 24 && configId <= 62) {
            throw new IllegalArgumentException("reserved program config: " + configId);
        }
        if (configId == 63) {
            throw new IllegalArgumentException("program config set to 63: PCM bypass is illegal in Dolby E frame");
        }
        return configs[configId];
    }

    private int channels;
    private int programs;

    public ProgramConfig(int channels, int programs)
    {
        this.channels = channels;
        this.programs = programs;
    }

    public int getChannels()
    {
        return channels;
    }

    public int getPrograms()
    {
        return programs;
    }

    public String toString()
    {
        return "" + getPrograms() + " programs, " + getChannels() + " channels";
    }
}
