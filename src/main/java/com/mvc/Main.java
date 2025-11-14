package com.mvc;

import com.mvc.filters.biome.OverworldBiomeFilter;
import com.mvc.filters.structure.EndStructureFilter;
import com.mvc.filters.structure.NetherStructureFilter;
import com.mvc.filters.structure.OverworldStructureFilter;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class Main {
    private static long seedsChecked = 0;
    private static int seedMatches = 0;
    private static long nextTime = 0;
    private static long currentTime;
    private static FileWriter output;
    public static void main(String[] args) throws IOException {
        initialize();

        if (Config.FILTER.equals(Config.FILTER_TYPE.FILE)) {
            filterFile();
        } else if (Config.FILTER.equals(Config.FILTER_TYPE.INCREMENTAL)) {
            filterIncremental(100000000);
        } else if (Config.FILTER.equals(Config.FILTER_TYPE.RANDOM)) {
            filterRandom();
        } else {
            throw new RuntimeException("Define filter type as FILE or INCREMENTAL in com.mvc.Config");
        }

        finish();
    }

    private static void filterFile() throws IOException {
        Scanner scanner = new Scanner(Config.INPUT_FILE);

        while (scanner.hasNextLong() && seedMatches < Config.SEED_MATCHES) {
            checkSeed(scanner.nextLong());
        }
    }

    private static void filterIncremental(long start) throws IOException {
        while (seedMatches < Config.SEED_MATCHES) {
            checkSeed(start & ((1L << 48) - 1));
            start++;
        }
    }

    private static void filterIncremental() throws IOException {
        filterIncremental(0L);
    }

    private static void filterRandom() throws IOException {
        Random random = new Random();

        while (seedMatches < Config.SEED_MATCHES) {
            checkSeed(random.nextLong() & ((1L << 48) - 1));
        }
    }

    private static void checkSeed(long seed) throws IOException {
        Long matchedStructureSeed = filterStructureSeed(seed) ? seed : null;

        if (matchedStructureSeed != null) {
            if (Config.DIMENSION.equals(Dimension.OVERWORLD)) {
                for (long biomeSeed = 0; biomeSeed < (1L << 16); biomeSeed++) {
                    long worldSeed = (biomeSeed << 48) | matchedStructureSeed;
                    Pair<Boolean, ArrayList<BPos>> filteredWorldSeed = filterWorldSeed(worldSeed, matchedStructureSeed);
                    Long matchedWorldSeed = filteredWorldSeed.getFirst() ? worldSeed : null;

                    if (matchedWorldSeed != null) {
                        output.write(matchedWorldSeed + " " + filteredWorldSeed.getSecond() + "\n");
                        seedMatches++;
                    }
                }
            } else {
                output.write(matchedStructureSeed + "\n");
                seedMatches++;
            }
        }
        seedsChecked++;
        currentTime = System.currentTimeMillis();

        if (currentTime > nextTime) {
            nextTime = currentTime + Config.LOG_DELAY;
            System.out.printf("%,d seeds checked with %,d matches\r", seedsChecked, seedMatches);
        }
    }

    private static boolean filterStructureSeed(long structureSeed) {
        ChunkRand chunkRand = new ChunkRand(structureSeed);

        if (Config.DIMENSION.equals(Dimension.OVERWORLD)) {
            OverworldStructureFilter overworldStructureFilter = new OverworldStructureFilter(structureSeed, chunkRand);
            return overworldStructureFilter.filterStructures();
        } else if (Config.DIMENSION.equals(Dimension.NETHER)) {
            NetherStructureFilter netherStructureFilter = new NetherStructureFilter(structureSeed, chunkRand);
            return netherStructureFilter.filterStructures();
        } else if (Config.DIMENSION.equals(Dimension.END)) {
            EndStructureFilter endStructureFilter = new EndStructureFilter(structureSeed, chunkRand);
            return endStructureFilter.filterStructures();
        } else {
            OverworldStructureFilter overworldStructureFilter = new OverworldStructureFilter(structureSeed, chunkRand);
            NetherStructureFilter netherStructureFilter = new NetherStructureFilter(structureSeed, chunkRand);
            EndStructureFilter endStructureFilter = new EndStructureFilter(structureSeed, chunkRand);
            return netherStructureFilter.filterStructures() && endStructureFilter.filterStructures() && overworldStructureFilter.filterStructures();
        }
    }

    private static Pair<Boolean, ArrayList<BPos>> filterWorldSeed(long worldSeed, long structureSeed) {
        ChunkRand chunkRand = new ChunkRand(structureSeed);
        OverworldBiomeFilter overworldBiomeFilter = new OverworldBiomeFilter(worldSeed, structureSeed, chunkRand);

        return overworldBiomeFilter.filterOverworld();
    }

    private static void initialize() throws IOException {
        System.out.println("Starting seed finding...");
        seedsChecked = 0;
        seedMatches = 0;
        nextTime = 0;
        currentTime = System.currentTimeMillis();
        output = new FileWriter(Config.OUTPUT_FILE);
    }

    private static void finish() throws IOException {
        output.close();
        System.out.printf("%,d seeds checked with %,d matches\r", seedsChecked, seedMatches);
    }
}