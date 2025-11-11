package com.mvc.filters.biome;

import com.mvc.Config;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mcfeature.structure.DesertPyramid;
import com.seedfinding.mcfeature.structure.Village;

public class OverworldBiomeFilter {
    private final long structureSeed;
    private final ChunkRand chunkRand;
    private final OverworldBiomeSource overworldBiomeSource;
    public OverworldBiomeFilter(long worldSeed, long structureSeed, ChunkRand chunkRand) {
        this.structureSeed = structureSeed;
        this.chunkRand = chunkRand;
        this.overworldBiomeSource = new OverworldBiomeSource(Config.VERSION, worldSeed);
    }

    public boolean filterBiomes() {
        return hasVillage() && hasTemple() && hasMidgameTemples(5);
    }

    private boolean hasVillage() {
        Village village = new Village(Config.VERSION);
        CPos villagePos = village.getInRegion(structureSeed, 0, 0, chunkRand);

        return village.isValidBiome(overworldBiomeSource.getBiome(villagePos.toBlockPos()));
    }

    private boolean hasTemple() {
        DesertPyramid temple = new DesertPyramid(Config.VERSION);
        CPos templePos = temple.getInRegion(structureSeed, 0, 0, chunkRand);

        return temple.isValidBiome(overworldBiomeSource.getBiome(templePos.toBlockPos()));
    }

    private boolean hasMidgameTemples(int minCount) {
        DesertPyramid temple = new DesertPyramid(Config.VERSION);
        int count = 0;

        for (int x = -2; x <= 1; x++) {
            for (int z = -2; z <= 1; z++) {
                //already checked spawn temple exists
                if (x == 0 && z == 0) {
                    count++;
                    break;
                }

                CPos templePos = temple.getInRegion(structureSeed, x, z, chunkRand);

                if (temple.isValidBiome(overworldBiomeSource.getBiome(templePos.toBlockPos()))) {
                    count++;
                }
            }
        }

        return count >= minCount;
    }
}
