package com.mvc;

import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.version.MCVersion;

import java.io.File;

import static com.mvc.Config.FILTER_TYPE.*;

public class Config {
    public static final int SEED_MATCHES = 100_000;
    public static final int LOG_DELAY = 10_000;
    public static final MCVersion VERSION = MCVersion.v1_16_1;
    public static final FILTER_TYPE FILTER = RANDOM;
    public static final Dimension DIMENSION = Dimension.OVERWORLD;
    public static final File INPUT_FILE = new File("./src/main/resources/input.txt");
    public static final File OUTPUT_FILE = new File("./src/main/resources/overworld_seeds.txt");
    public static final int VILLAGE_DISTANCE = 12;
    public static final int TEMPLE_DISTANCE = 12;
    public static final int OUTPOST_DISTANCE = 64;
    public static final int MONUMENT_DISTANCE = 64;
    public static final int BASTION_DISTANCE = 10;
    public static final int FORTRESS_DISTANCE = 10;
    public static final int END_CITY_DISTANCE = 6;

    public enum FILTER_TYPE {
        FILE,
        INCREMENTAL,
        RANDOM
    }
}
