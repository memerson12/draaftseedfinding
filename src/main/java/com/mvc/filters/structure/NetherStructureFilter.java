package com.mvc.filters.structure;

import com.mvc.Config;
import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mcbiome.source.NetherBiomeSource;
import com.seedfinding.mccore.block.Block;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.math.DistanceMetric;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mcterrain.terrain.NetherTerrainGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

public class NetherStructureFilter {
    private final long structureSeed;
    private final ChunkRand chunkRand;
    private CPos bastionPos;
    private CPos fortressPos;
    private NetherBiomeSource netherBiomeSource;
    private NetherTerrainGenerator netherTerrainGenerator;

    private static final int MAX_SEARCH_DEPTH = 100; // Stop after checking 100 chunks
    private static final double HEURISTIC_MULTIPLIER = 1.5; // Bias factor (1.0 is neutral, 2.0 is very greedy)
    private static final int MAX_PATH_LENGTH = 14;   // Max chunks to travel

    public NetherStructureFilter(long structureSeed, ChunkRand chunkRand) {
        this.structureSeed = structureSeed;
        this.chunkRand = chunkRand;
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
        // Pre-check: If Manhattan distance is > 10, it's mathematically impossible to reach in 10 steps.
        if (Math.abs(start.getX() - target.getX()) + Math.abs(start.getZ() - target.getZ()) > MAX_PATH_LENGTH) {
            return false;
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        // Map tracks the fewest steps taken to reach a chunk.
        // If we reach a chunk in 5 steps, we reject future paths that reach it in 6 steps.
        Map<CPos, Integer> visitedSteps = new HashMap<>();

        openSet.add(new Node(start, 0, getHeuristic(start, target), 0, null));
        visitedSteps.put(start, 0);

        int checks = 0;

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (checks++ > MAX_SEARCH_DEPTH) return false; // Computation limit
            if (current.pos.equals(target)) {
                Node temp = current;
                while (temp != null) {
                    temp = temp.parent;
                }
                return true;   // Success
            }

            if (current.pos.distanceTo(target, DistanceMetric.EUCLIDEAN) > Config.BASTION_DISTANCE) { // Went too far from the target
                visitedSteps.put(current.pos, current.steps);
                continue;
            }

            // Optimization: If we found a path to this node previously that was shorter/equal steps, skip.
            if (visitedSteps.containsKey(current.pos) && visitedSteps.get(current.pos) < current.steps) {
                continue;
            }

            // Standard Neighbors
            int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

            for (int[] dir : directions) {
                CPos neighborPos = new CPos(current.pos.getX() + dir[0], current.pos.getZ() + dir[1]);
                int newSteps = current.steps + 1;

                // 1. HARD LIMIT: Max 10 Chunks
                if (newSteps > MAX_PATH_LENGTH) continue;

                // 2. Loop/Redundancy Check
                if (visitedSteps.containsKey(neighborPos) && visitedSteps.get(neighborPos) <= newSteps) {
                    continue;
                }

                // 3. Terrain Validation (Walls/Lava)
                if (!isChunkWalkable(neighborPos)) continue;

                // 4. Cost Calculation
                double traversalCost = getBiomeCost(neighborPos);
                double newGCost = current.gCost + traversalCost;
                double newHCost = getHeuristic(neighborPos, target);

                visitedSteps.put(neighborPos, newSteps);
                openSet.add(new Node(neighborPos, newGCost, newHCost, newSteps, current));
            }
        }
        return false;
    }

    private boolean isSpaceForPortal() {
        int x = 0;
        int z = 0;
        netherTerrainGenerator = new NetherTerrainGenerator(netherBiomeSource);

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


    private boolean isSSV() {
        netherBiomeSource = new NetherBiomeSource(Config.VERSION, structureSeed);

        return netherBiomeSource.getBiome(fortressPos.toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(-4, 0).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(4, 0).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(0, -4).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY) &&
                netherBiomeSource.getBiome(fortressPos.add(0, 4).toBlockPos()).equals(Biomes.SOUL_SAND_VALLEY);
    }

    private static class Node implements Comparable<Node> {
        CPos pos;
        double gCost;
        double hCost;
        double fCost;
        int steps;
        Node parent;

        public Node(CPos pos, double gCost, double hCost, int steps, Node parent) {
            this.pos = pos;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.steps = steps;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            // 1. Compare Total Cost (Standard A*)
            int compare = Double.compare(this.fCost, other.fCost);

            // 2. NEW: Tie-Breaker
            // If total costs are equal, prefer the node with lower hCost (closer to target)
            if (compare == 0) {
                return Double.compare(this.hCost, other.hCost);
            }

            return compare;
        }
    }

    // Manhattan distance is faster than Euclidean and fits grid movement
    private double getHeuristic(CPos a, CPos b) {
        // Manhattan distance
        double distance = Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());

        // Multiply by 1.5 (or higher) to make the algorithm favor direction over terrain cost.
        return distance * HEURISTIC_MULTIPLIER;
    }

    private double getBiomeCost(CPos pos) {
        Biome biome = netherBiomeSource.getBiomeForNoiseGen(pos.getX() * 4, 0, pos.getZ() * 4);
        // High penalty for Basalt Deltas (obstacles), moderate for Soul Sand (slow)
        if (biome.getCategory() == Biome.Category.NETHER) {
            // Note: You'll need to map your specific Biome objects here
            if (biome.getId() == Biomes.BASALT_DELTAS.getId()) return 4.0;
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
                } else if (block.get().equals(Blocks.LAVA)) {
                    hasFloor = false; // Reset on lava
                }
            }
        }
        return false;
    }
}
