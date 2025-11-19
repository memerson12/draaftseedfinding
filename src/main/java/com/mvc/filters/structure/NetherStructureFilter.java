package com.mvc.filters.structure;

import com.mvc.Config;
import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mcbiome.source.NetherBiomeSource;
import com.seedfinding.mccore.block.Block;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mcterrain.terrain.NetherTerrainGenerator;

import java.util.*;

public class NetherStructureFilter {
    private final long structureSeed;
    private final ChunkRand chunkRand;
    private final NetherTerrainGenerator netherTerrainGenerator;
    private CPos bastionPos;
    private CPos fortressPos;
    private final NetherBiomeSource netherBiomeSource;

    private static final int MAX_SEARCH_DEPTH = 250; // Stop after checking 500 chunks
    private static final int HEURISTIC_WEIGHT = 2; // Multiplier to make it greedy-ish (faster, less perfect)

    public NetherStructureFilter(long structureSeed, ChunkRand chunkRand) {
        this.structureSeed = structureSeed;
        this.chunkRand = chunkRand;
        this.netherBiomeSource = new NetherBiomeSource(Config.VERSION, structureSeed);
        this.netherTerrainGenerator = new NetherTerrainGenerator(netherBiomeSource);
    }

    public boolean filterStructures() {
        return hasBastion() && hasFortress() && isSSV() && isSpaceForPortal() && canPathToBastion(new CPos(0, 0), bastionPos);
    }

    private boolean hasBastion() {
        BastionRemnant bastion = new BastionRemnant(Config.VERSION);

        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                CPos curBastion = bastion.getInRegion(structureSeed, x, z, chunkRand);
                if (curBastion != null && curBastion.getMagnitude() <= Config.BASTION_DISTANCE) {
                    if (bastionPos != null) {
                        return false;
                    }
                    bastionPos = curBastion;
                }
            }
        }
        return bastionPos != null && bastion.canSpawn(bastionPos, new NetherBiomeSource(Config.VERSION, structureSeed));
    }

    private boolean hasFortress() {
        Fortress fortress = new Fortress(Config.VERSION);

        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                fortressPos = fortress.getInRegion(structureSeed, x, z, chunkRand);
                if (fortressPos != null && fortressPos.getMagnitude() <= Config.FORTRESS_DISTANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canPathToBastion(CPos start, CPos target) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<CPos> closedSet = new HashSet<>();

        openSet.add(new Node(start, 0, getHeuristic(start, target)));

        int checks = 0;

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (checks++ > MAX_SEARCH_DEPTH) return false; // Took too long
            if (current.pos.equals(target)) return true; // Reached destination
            if (closedSet.contains(current.pos)) continue;

            closedSet.add(current.pos);

            // Check neighbors (North, South, East, West)
            int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

            for (int[] dir : directions) {
                CPos neighborPos = new CPos(current.pos.getX() + dir[0], current.pos.getZ() + dir[1]);

                if (closedSet.contains(neighborPos)) continue;

                // 1. Check Physical Terrain (Walls/Lava)
                if (!isChunkWalkable(neighborPos)) {
                    continue; // Wall or Lava detected
                }

                // 2. Calculate Movement Cost (incorporating Biomes)
                double traversalCost = getBiomeCost(neighborPos);
                double newGCost = current.gCost + traversalCost;
                double newHCost = getHeuristic(neighborPos, target);

                openSet.add(new Node(neighborPos, newGCost, newHCost));
            }
        }
        return false; // No path found
    }

    private boolean isSpaceForPortal() {
        int x = 0;
        int z = 0;

        // Iterate from just above the lava ocean (32) to the ceiling (approx 120)
        // We look for a solid block that has air immediately above it.
        for (int y = 32; y < 120; y++) {

            Optional<Block> floor = netherTerrainGenerator.getBlockAt(x, y, z);

            // 1. Check if we have a solid floor
            if (floor.isPresent() && !floor.get().equals(Blocks.AIR) && !floor.get().equals(Blocks.LAVA)) {

                // 2. Check for clearance (space for the portal frame)
                // We need roughly 3-4 blocks of air above the floor.
                boolean hasClearance = true;
                for (int offset = 1; offset <= 4; offset++) {
                    Optional<Block> airSpace = netherTerrainGenerator.getBlockAt(x, y + offset, z);
                    if (airSpace.isPresent() && !airSpace.get().equals(Blocks.AIR)) {
                        hasClearance = false;
                        break;
                    }
                }

                if (hasClearance) {
                    return true; // Found valid terrain
                }
            }
        }

        return false; // No valid spot found in the column
    }

    private boolean hasBastionTerrainAirSampling() {
        Random random = new Random();
        int air = 0;
        CPos spawn = new CPos(0, 0);

        while (spawn.getX() != bastionPos.getX() || spawn.getZ() != bastionPos.getZ()) {
            // move toward bastion along axis we are furthest from
            if (Math.abs(bastionPos.getX() - spawn.getX()) < Math.abs(bastionPos.getZ() - spawn.getZ())) {
                spawn = spawn.add(0, bastionPos.getZ() > 0 ? 1 : -1);
            } else {
                spawn = spawn.add(bastionPos.getX() > 0 ? 1 : -1, 0);
            }

            // sample chunk and see if it meets air threshold
            for (int s = 0; s < 25; s++) {
                int x = random.nextInt(16);
                int y = random.nextInt(16);
                int z = random.nextInt(16);

                Optional<Block> block = netherTerrainGenerator.getBlockAt(spawn.toBlockPos().getX() + x, 57 + y, spawn.toBlockPos().getZ() + z);
                if (block.isPresent() && block.get().equals(Blocks.AIR)) {
                    air++;
                }
            }

            if (air < 1) {
                return false;
            }
        }
        return true;
    }

    private boolean hasBastionTerrainHeightCheck() {
        NetherTerrainGenerator netherTerrainGenerator = new NetherTerrainGenerator(netherBiomeSource);
        BPos approxBastion = new BPos(bastionPos.toBlockPos(64));

        for (int i = 1; i <= 10; i++) {
            double t = (double) i / 10;
            int x = (int) (approxBastion.getX() * t);
            int z = (int) (approxBastion.getZ() * t);
            Block[] column = netherTerrainGenerator.getColumnAt(x, z);
            int air = 0;
            boolean lastBlockAir = false;

            for (int b = 40; b < 100; b++) {
                if (!lastBlockAir && column[b].equals(Blocks.AIR)) {
                    lastBlockAir = true;
                    continue;
                }
                if (lastBlockAir && column[b].equals(Blocks.AIR)) {
                    if (++air > 5) {
                        return true;
                    }
                }
                if (!column[b].equals(Blocks.AIR)) {
                    lastBlockAir = false;
                    air = 0;
                }
            }
        }
        return false;
    }

    private boolean isSSV() {
        return netherBiomeSource.getBiome(fortressPos.toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(-4, 0).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(4, 0).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(0, -4).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(0, 4).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY);
    }

    private static class Node implements Comparable<Node> {
        CPos pos;
        double gCost; // Cost from start
        double hCost; // Heuristic (estimated dist to end)
        double fCost; // Total cost (g + h)

        public Node(CPos pos, double gCost, double hCost) {
            this.pos = pos;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost, other.fCost);
        }
    }

    // Manhattan distance is faster than Euclidean and fits grid movement
    private double getHeuristic(CPos a, CPos b) {
        return (Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ())) * HEURISTIC_WEIGHT;
    }

    private double getBiomeCost(CPos pos) {
        Biome biome = netherBiomeSource.getBiomeForNoiseGen(pos.getX() * 4, 0, pos.getZ() * 4);
        // High penalty for Basalt Deltas (obstacles), moderate for Soul Sand (slow)
        if (biome.getCategory() == Biome.Category.NETHER) {
            // Note: You'll need to map your specific Biome objects here
            if (biome.getId() == Biomes.BASALT_DELTAS.getId()) return 8.0;
            if (biome.getId() == Biomes.SOUL_SAND_VALLEY.getId()) return 2.0;
        }
        return 1.0;
    }

    // The optimized column check from previous discussion
    private boolean isChunkWalkable(CPos chunkPos) {
        int centerX = (chunkPos.getX() << 4) + 8;
        int centerZ = (chunkPos.getZ() << 4) + 8;
        boolean hasFloor = false;

        // Sparse scan from Y=32 to Y=90
        for (int y = 32; y < 90; y += 4) {
            Optional<Block> block = this.netherTerrainGenerator.getBlockAt(centerX, y, centerZ);

            if (block.isPresent()) {
                if (block.get().equals(Blocks.AIR)) {
                    if (hasFloor) return true; // Valid space found
                } else if (!block.get().equals(Blocks.LAVA)) {
                    hasFloor = true; // Solid ground
                } else {
                    hasFloor = false; // Reset on lava
                }
            }
        }
        return false;
    }

//    Seems hard to get access ti underlying noise function from NetherTerrainGenerator. Maybe we don't need this
//    public boolean isChunkWalkableFast(CPos chunkPos) {
//        int centerX = (chunkPos.getX() << 4) + 8;
//        int centerZ = (chunkPos.getZ() << 4) + 8;
//
//        boolean hasFloor = false;
//
//        // Scan the vertical column using raw math
//        for (int y = 32; y < 90; y += 4) {
//
//            // 1. The Raw Calculation
//            // This is the heavy optimization. No "Block" objects created.
//            double density = netherTerrainGenerator.sa(centerX, y, centerZ);
//
//            // 2. Interpret the Math
//            boolean isSolid = density > 0;
//
//            // 3. Logic (same as before, but using density)
//            if (!isSolid) {
//                // It is "Empty" space.
//                // Check specific Y-level to distinguish Air vs Lava
//                if (y >= LAVA_LEVEL) {
//                    // It is Air
//                    if (hasFloor) return true; // Found Air + Floor below = Walkable!
//                } else {
//                    // It is Lava (Empty space below sea level)
//                    hasFloor = false; // Cannot walk on lava
//                }
//            } else {
//                // It is Wall/Ground
//                hasFloor = true;
//            }
//        }
//        return false;
//    }
//}
}
