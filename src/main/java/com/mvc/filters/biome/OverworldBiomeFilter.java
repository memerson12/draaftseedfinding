package com.mvc.filters.biome;

import com.mvc.Config;

import com.seedfinding.mcbiome.layer.BiomeLayer;
import com.seedfinding.mcbiome.layer.IntBiomeLayer;
import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mcfeature.structure.DesertPyramid;
import com.seedfinding.mcfeature.structure.Monument;
import com.seedfinding.mcfeature.structure.PillagerOutpost;
import com.seedfinding.mcfeature.structure.Village;

import java.util.ArrayList;

public class OverworldBiomeFilter {
    private final long structureSeed;
    private final ChunkRand chunkRand;
    private final long worldSeed;
    private final OverworldBiomeSource overworldBiomeSource;
    private final IntBiomeLayer biomeLayer9;
    private final IntBiomeLayer biomeLayer11;
    private final IntBiomeLayer biomeLayer16;
    private final IntBiomeLayer biomeLayer19;
    private final IntBiomeLayer biomeLayer26;
    private final IntBiomeLayer biomeLayer31;
    private final ArrayList<CPos> mushroomPositions;
    private final ArrayList<CPos> badlandsPositions;
    private final ArrayList<CPos> junglePositions;
    private final ArrayList<CPos> megaTaigaPositions;
    private final ArrayList<CPos> snowyPositions;
    public OverworldBiomeFilter(long worldSeed, long structureSeed, ChunkRand chunkRand) {
        this.structureSeed = structureSeed;
        this.chunkRand = chunkRand;
        this.worldSeed = worldSeed;
        this.overworldBiomeSource = new OverworldBiomeSource(Config.VERSION, worldSeed);
        this.biomeLayer9 = overworldBiomeSource.getLayer(9);
        this.biomeLayer11 = overworldBiomeSource.getLayer(11);
        this.biomeLayer16 = overworldBiomeSource.getLayer(16);
        this.biomeLayer19 = overworldBiomeSource.getLayer(19);
        this.biomeLayer26 = overworldBiomeSource.getLayer(26);
        this.biomeLayer31 = overworldBiomeSource.getLayer(31);
        this.mushroomPositions = new ArrayList<>();
        this.badlandsPositions = new ArrayList<>();
        this.junglePositions = new ArrayList<>();
        this.megaTaigaPositions = new ArrayList<>();
        this.snowyPositions = new ArrayList<>();
    }

    public boolean hasMidgame() {
        return hasVillage() && hasTemple() && hasMonument() && hasOutpost() && hasMidgameTemples(5);
    }

    public boolean filterBiomes() {
        /*
        mushroom 2m 23s 30 seeds 10,276 matches
        jungle 1m 37s 18 seeds 10,129 matches ~ BUGGED SOMETIMES???
        mega taiga 38s 8 seeds 10,909 ~ BUGGED SOMETIMES???
        badlands ~ SLOW AS FUCK
        snowy 53s 10 seeds 11,547 matches
         */
        return hasBiomeTiles() && hasSnowyBiomes();
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

    private boolean hasMonument() {
        Monument mm = new Monument(Config.VERSION);

        for (int x = -2; x <= 1; x++) {
            for (int z = -2; z <= 2; z++) {
                CPos mmPos = mm.getInRegion(structureSeed, x, z, chunkRand);

                if (mm.canSpawn(mmPos.getX(), mmPos.getZ(), overworldBiomeSource)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasOutpost() {
        PillagerOutpost po = new PillagerOutpost(Config.VERSION);

        for (int x = -2; x <= 1; x++) {
            for (int z = -2; z <= 2; z++) {
                CPos poPos = po.getInRegion(structureSeed, x, z, chunkRand);

                if (poPos != null && po.canSpawn(poPos.getX(), poPos.getZ(), overworldBiomeSource)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasBiomeTiles() {
        ArrayList<CPos> specialPositions = new ArrayList<>();
        long specialLayerSeed = BiomeLayer.getLayerSeed(worldSeed, 3);

        // 53% to have 3 special tiles
        for (int x = -3; x <= 2; x++) {
            for (int z = -3; z <= 2; z++) {
                long specialLocalSeed = BiomeLayer.getLocalSeed(specialLayerSeed, x, z);

                // 1 in 13 for a 1024x1024 tile to be special
                if (Math.floorMod(specialLocalSeed >> 24, 13) == 0) {
                    specialPositions.add(new CPos(x, z));
                }
            }
        }

        // need at least 3 special tiles for badlands, jungle, mega taiga
        if (specialPositions.size() < 3) {
            return false;
        }

        long mushroomLayerSeed = BiomeLayer.getLayerSeed(worldSeed, 5);

        // 76% to have 1 mushroom tile
        for (int x = -12; x <= 11; x++) {
            for (int z = -12; z <= 11; z++) {
                long mushroomLocalSeed = BiomeLayer.getLocalSeed(mushroomLayerSeed, x, z);

                // 1 in 100 for a 256x256 tile to be mushroom
                if (Math.floorMod(mushroomLocalSeed >> 24, 100) == 0) {
                    mushroomPositions.add(new CPos(x, z));
                }
            }
        }

        if (mushroomPositions.isEmpty()) {
            return false;
        }

        boolean badlands = false;
        boolean jungle = false;
        boolean megaTaiga = false;
        for (CPos pos: specialPositions) {
            if (biomeLayer9.sample(pos.getX(), 0, pos.getZ()) != 0) {
                switch (biomeLayer11.sample(pos.getX(), 0, pos.getZ())) {
                    case 1: {
                        badlands = true;
                        badlandsPositions.add(pos);
                        break;
                    }
                    case 2: {
                        jungle = true;
                        junglePositions.add(pos);
                        break;
                    }
                    case 3: {
                        megaTaiga = true;
                        megaTaigaPositions.add(pos);
                        break;
                    }
                }
            }
        }

        if (!badlands || !jungle || !megaTaiga) {
            return false;
        }

        boolean freezing = false;
        for (int x = -3; x <= 2; x++) {
            for (int z = -3; z <= 2; z++) {
                if (biomeLayer11.sample(x, 0, z) == 4) {
                    freezing = true;
                    snowyPositions.add(new CPos(x, z));
                }
            }
        }

        return freezing;
    }

    private boolean hasMushroomBiomes() {
        /*
        id 14 is mushroom_fields
        checking at 256:1

        id 15 is mushroom_field_shore
        checking at 16:1
        */
        for (CPos pos: mushroomPositions) {
            if (biomeLayer16.sample(pos.getX(), 0, pos.getZ()) == 14) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int x_16 = pos.getX() * 16 + x;
                        int z_16 = pos.getZ() * 16 + z;
                        if (biomeLayer31.sample(x_16, 0, z_16) == 15) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasJungleBiomes() {
        /*
        id 168 is bamboo_jungle
        checking at 256:1

        id 169 is bamboo_jungle_hills
        checking at 64:1
        */
        for (CPos pos: junglePositions) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    int x_256 = pos.getX() * 4 + x;
                    int z_256 = pos.getZ() * 4 + z;
                    if (biomeLayer19.sample(x_256, 0, z_256) == 168) {
                        for (int i = 0; i < 4; i++) {
                            for (int j = 0; j < 4; j++) {
                                int x_64 = x_256 * 4 + i;
                                int z_64 = z_256 * 4 + j;
                                if (biomeLayer26.sample(x_64, 0, z_64) == 169) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasMegaTaigaBiomes() {
        /*
        id 32 is giant_tree_taiga
        checking at 256:1

        id 33 is giant_tree_taiga_hills
        checking at 64:1
        */
        for (CPos pos: megaTaigaPositions) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    int x_256 = pos.getX() * 4 + x;
                    int z_256 = pos.getZ() * 4 + z;
                    if (biomeLayer19.sample(x_256, 0, z_256) == 32) {
                        for (int i = 0; i < 4; i++) {
                            for (int j = 0; j < 4; j++) {
                                int x_64 = x_256 * 4 + i;
                                int z_64 = z_256 * 4 + j;
                                if (biomeLayer26.sample(x_64, 0, z_64) == 33) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasBadlandsBiomes() {
        /*
        id 38 is wooded_badlands_plateau
        checking at 256:1

        id 39 is badlands_plateau
        checking at 256:1
        */
        boolean woodedBadlandsPlateau = false;
        boolean badlandsPlateau = false;
        for (CPos pos: badlandsPositions) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    int x_256 = pos.getX() * 4 + x;
                    int z_256 = pos.getZ() * 4 + z;
                    if (biomeLayer19.sample(x_256, 0, z_256) == 38) {
                        woodedBadlandsPlateau = true;
                    } else if (biomeLayer19.sample(x_256, 0, z_256) == 39) {
                        badlandsPlateau = true;
                    }
                }
            }
        }
        return badlandsPlateau && woodedBadlandsPlateau;
    }

    private boolean hasSnowyBiomes() {
        /*
        id 30 is snowy_taiga
        checking at 256:1

        id 31 is snowy_taiga_hills
        checking at 64:1
        */
        for (CPos pos: snowyPositions) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    int x_256 = pos.getX() * 4 + x;
                    int z_256 = pos.getZ() * 4 + z;
                    if (biomeLayer19.sample(x_256, 0, z_256) == 30) {
                        for (int i = 0; i < 4; i++) {
                            for (int j = 0; j < 4; j++) {
                                int x_64 = x_256 * 4 + i;
                                int z_64 = z_256 * 4 + j;
                                if (biomeLayer26.sample(x_64, 0, z_64) == 31) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
