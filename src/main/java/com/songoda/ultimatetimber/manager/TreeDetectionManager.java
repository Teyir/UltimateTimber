package com.songoda.ultimatetimber.manager;

import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.third_party.com.cryptomorin.xseries.XMaterial;
import com.songoda.ultimatetimber.UltimateTimber;
import com.songoda.ultimatetimber.tree.DetectedTree;
import com.songoda.ultimatetimber.tree.ITreeBlock;
import com.songoda.ultimatetimber.tree.TreeBlock;
import com.songoda.ultimatetimber.tree.TreeBlockSet;
import com.songoda.ultimatetimber.tree.TreeBlockType;
import com.songoda.ultimatetimber.tree.TreeDefinition;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeDetectionManager extends Manager {
    private final Set<Vector> VALID_TRUNK_OFFSETS, VALID_BRANCH_OFFSETS, VALID_LEAF_OFFSETS;

    private TreeDefinitionManager treeDefinitionManager;
    private PlacedBlockManager placedBlockManager;
    private int numLeavesRequiredForTree;
    private boolean onlyBreakLogsUpwards, entireTreeBase, destroyLeaves;

    public TreeDetectionManager(UltimateTimber plugin) {
        super(plugin);

        this.VALID_BRANCH_OFFSETS = new HashSet<>();
        this.VALID_TRUNK_OFFSETS = new HashSet<>();
        this.VALID_LEAF_OFFSETS = new HashSet<>();

        // 3x2x3 centered around log, excluding -y axis
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    this.VALID_BRANCH_OFFSETS.add(new Vector(x, y, z));
                }
            }
        }

        // 3x3x3 centered around log
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    this.VALID_TRUNK_OFFSETS.add(new Vector(x, y, z));
                }
            }
        }

        // Adjacent blocks to log
        for (int i = -1; i <= 1; i += 2) {
            this.VALID_LEAF_OFFSETS.add(new Vector(i, 0, 0));
            this.VALID_LEAF_OFFSETS.add(new Vector(0, i, 0));
            this.VALID_LEAF_OFFSETS.add(new Vector(0, 0, i));
        }
    }

    @Override
    public void reload() {
        this.treeDefinitionManager = this.plugin.getTreeDefinitionManager();
        this.placedBlockManager = this.plugin.getPlacedBlockManager();
        this.numLeavesRequiredForTree = ConfigurationManager.Setting.LEAVES_REQUIRED_FOR_TREE.getInt();
        this.onlyBreakLogsUpwards = ConfigurationManager.Setting.ONLY_DETECT_LOGS_UPWARDS.getBoolean();
        this.entireTreeBase = ConfigurationManager.Setting.BREAK_ENTIRE_TREE_BASE.getBoolean();
        this.destroyLeaves = ConfigurationManager.Setting.DESTROY_LEAVES.getBoolean();
    }

    @Override
    public void disable() {
    }

    /**
     * Detects a tree given an initial starting block
     *
     * @param initialBlock The starting Block of the detection
     * @return A DetectedTree if one was found, otherwise null
     */
    public DetectedTree detectTree(Block initialBlock) {
        TreeDefinitionManager treeDefinitionManager = this.plugin.getTreeDefinitionManager();

        TreeBlock initialTreeBlock = new TreeBlock(initialBlock, TreeBlockType.LOG);
        TreeBlockSet<Block> detectedTreeBlocks = new TreeBlockSet<>(initialTreeBlock);
        Set<TreeDefinition> possibleTreeDefinitions = this.treeDefinitionManager.getTreeDefinitionsForLog(initialBlock);

        if (possibleTreeDefinitions.isEmpty()) {
            return null;
        }

        // Detect tree trunk
        List<Block> trunkBlocks = new ArrayList<>();
        trunkBlocks.add(initialBlock);
        Block targetBlock = initialBlock;
        while (this.isValidLogType(possibleTreeDefinitions, null, (targetBlock = targetBlock.getRelative(BlockFace.UP)))) {
            trunkBlocks.add(targetBlock);
            XMaterial resolvedMaterial = CompatibleMaterial.getMaterial(targetBlock.getType()).orElse(null);
            possibleTreeDefinitions.retainAll(this.treeDefinitionManager.narrowTreeDefinition(possibleTreeDefinitions, resolvedMaterial, TreeBlockType.LOG));
        }

        if (!this.onlyBreakLogsUpwards) {
            targetBlock = initialBlock;
            while (this.isValidLogType(possibleTreeDefinitions, null, (targetBlock = targetBlock.getRelative(BlockFace.DOWN)))) {
                trunkBlocks.add(targetBlock);
                XMaterial resolvedMaterial = CompatibleMaterial.getMaterial(targetBlock.getType()).orElse(null);
                possibleTreeDefinitions.retainAll(this.treeDefinitionManager.narrowTreeDefinition(possibleTreeDefinitions, resolvedMaterial, TreeBlockType.LOG));
            }
        }

        // Lowest blocks at the front of the list
        Collections.reverse(trunkBlocks);

        // Detect branches off the main trunk
        Set<Location> visitedBranchLocations = new HashSet<>();
        for (Block trunkBlock : trunkBlocks) {
            this.recursiveBranchSearch(possibleTreeDefinitions, trunkBlocks, detectedTreeBlocks, trunkBlock, initialBlock.getLocation().getBlockY(), visitedBranchLocations);
        }

        // Pre-compute whether diagonal leaf detection is needed
        boolean detectLeavesDiagonally = possibleTreeDefinitions.stream().anyMatch(TreeDefinition::shouldDetectLeavesDiagonally);

        // Detect leaves off the trunk/branches
        Set<ITreeBlock<Block>> branchBlocks = new HashSet<>(detectedTreeBlocks.getLogBlocks());
        Set<Location> visitedLeafLocations = new HashSet<>();
        for (ITreeBlock<Block> branchBlock : branchBlocks) {
            this.recursiveLeafSearch(possibleTreeDefinitions, detectedTreeBlocks, branchBlock.getBlock(), visitedLeafLocations, detectLeavesDiagonally);
        }

        // Use the first tree definition in the set
        TreeDefinition actualTreeDefinition = possibleTreeDefinitions.iterator().next();

        // Trees need at least a certain number of leaves
        if (detectedTreeBlocks.getLeafBlocks().size() < this.numLeavesRequiredForTree) {
            return null;
        }

        // Remove leaves if we don't care about the leaves
        if (!this.destroyLeaves) {
            detectedTreeBlocks.removeAll(TreeBlockType.LEAF);
        }

        // Check that the tree isn't on the ground if enabled
        if (this.entireTreeBase) {
            Set<Block> groundBlocks = new HashSet<>();
            for (ITreeBlock<Block> treeBlock : detectedTreeBlocks.getLogBlocks()) {
                if (treeBlock != detectedTreeBlocks.getInitialLogBlock() && treeBlock.getLocation().getBlockY() == initialBlock.getLocation().getBlockY()) {
                    groundBlocks.add(treeBlock.getBlock());
                }
            }

            for (Block block : groundBlocks) {
                Block blockBelow = block.getRelative(BlockFace.DOWN);
                boolean blockBelowIsLog = this.isValidLogType(possibleTreeDefinitions, null, blockBelow);
                boolean blockBelowIsSoil = false;
                XMaterial belowMaterial = CompatibleMaterial.getMaterial(blockBelow.getType()).orElse(null);
                for (XMaterial material : treeDefinitionManager.getPlantableSoilMaterial(actualTreeDefinition)) {
                    if (material == belowMaterial) {
                        blockBelowIsSoil = true;
                        break;
                    }
                }

                if (blockBelowIsLog || blockBelowIsSoil) {
                    return null;
                }
            }
        }

        return new DetectedTree(actualTreeDefinition, detectedTreeBlocks);
    }

    /**
     * Recursively searches for branches off a given block
     *
     * @param treeDefinitions        The possible tree definitions
     * @param trunkBlocks            The tree trunk blocks
     * @param treeBlocks             The detected tree blocks
     * @param block                  The next block to check for a branch
     * @param startingBlockY         The Y coordinate of the initial block
     * @param visitedBranchLocations Set of already visited locations to avoid redundant checks
     */
    private void recursiveBranchSearch(Set<TreeDefinition> treeDefinitions, List<Block> trunkBlocks, TreeBlockSet<Block> treeBlocks, Block block, int startingBlockY, Set<Location> visitedBranchLocations) {
        for (Vector offset : this.onlyBreakLogsUpwards ? this.VALID_BRANCH_OFFSETS : this.VALID_TRUNK_OFFSETS) {
            Block targetBlock = block.getRelative(offset.getBlockX(), offset.getBlockY(), offset.getBlockZ());
            Location targetLocation = targetBlock.getLocation();
            if (visitedBranchLocations.contains(targetLocation)) {
                continue;
            }
            visitedBranchLocations.add(targetLocation);
            if (this.isValidLogType(treeDefinitions, trunkBlocks, targetBlock)) {
                TreeBlock treeBlock = new TreeBlock(targetBlock, TreeBlockType.LOG);
                treeBlocks.add(treeBlock);
                XMaterial resolvedMaterial = CompatibleMaterial.getMaterial(targetBlock.getType()).orElse(null);
                treeDefinitions.retainAll(this.treeDefinitionManager.narrowTreeDefinition(treeDefinitions, resolvedMaterial, TreeBlockType.LOG));
                if (!this.onlyBreakLogsUpwards || targetBlock.getLocation().getBlockY() > startingBlockY) {
                    this.recursiveBranchSearch(treeDefinitions, trunkBlocks, treeBlocks, targetBlock, startingBlockY, visitedBranchLocations);
                }
            }
        }
    }

    /**
     * Recursively searches for leaves that are next to this tree
     *
     * @param treeDefinitions        The possible tree definitions
     * @param treeBlocks             The detected tree blocks
     * @param block                  The next block to check for a leaf
     * @param visitedLeafLocations   Set of already visited locations to avoid redundant checks
     * @param detectLeavesDiagonally Whether to detect leaves diagonally (pre-computed)
     */
    private void recursiveLeafSearch(Set<TreeDefinition> treeDefinitions, TreeBlockSet<Block> treeBlocks, Block block, Set<Location> visitedLeafLocations, boolean detectLeavesDiagonally) {
        for (Vector offset : !detectLeavesDiagonally ? this.VALID_LEAF_OFFSETS : this.VALID_TRUNK_OFFSETS) {
            Block targetBlock = block.getRelative(offset.getBlockX(), offset.getBlockY(), offset.getBlockZ());
            Location targetLocation = targetBlock.getLocation();
            if (visitedLeafLocations.contains(targetLocation)) {
                continue;
            }
            visitedLeafLocations.add(targetLocation);

            XMaterial resolvedMaterial = CompatibleMaterial.getMaterial(targetBlock.getType()).orElse(null);
            if (this.isValidLeafType(treeDefinitions, treeBlocks, targetBlock, resolvedMaterial) && !this.doesLeafBorderInvalidLog(treeDefinitions, treeBlocks, targetBlock)) {
                TreeBlock treeBlock = new TreeBlock(targetBlock, TreeBlockType.LEAF);
                treeBlocks.add(treeBlock);
                treeDefinitions.retainAll(this.treeDefinitionManager.narrowTreeDefinition(treeDefinitions, resolvedMaterial, TreeBlockType.LEAF));
                this.recursiveLeafSearch(treeDefinitions, treeBlocks, targetBlock, visitedLeafLocations, detectLeavesDiagonally);
            }
        }
    }

    /**
     * Checks if a leaf is bordering a log that isn't part of this tree
     *
     * @param treeDefinitions The possible tree definitions
     * @param treeBlocks      The detected tree blocks
     * @param block           The block to check
     * @return True if the leaf borders an invalid log, otherwise false
     */
    private boolean doesLeafBorderInvalidLog(Set<TreeDefinition> treeDefinitions, TreeBlockSet<Block> treeBlocks, Block block) {
        for (Vector offset : this.VALID_TRUNK_OFFSETS) {
            Block targetBlock = block.getRelative(offset.getBlockX(), offset.getBlockY(), offset.getBlockZ());
            if (this.isValidLogType(treeDefinitions, null, targetBlock) && !treeBlocks.contains(new TreeBlock(targetBlock, TreeBlockType.LOG))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a given block is valid for the given TreeDefinitions
     *
     * @param treeDefinitions The Set of TreeDefinitions to compare against
     * @param trunkBlocks     The trunk blocks of the tree for checking the distance
     * @param block           The Block to check
     * @return True if the block is a valid log type, otherwise false
     */
    private boolean isValidLogType(Set<TreeDefinition> treeDefinitions, List<Block> trunkBlocks, Block block) {
        // Check if block is placed
        if (this.placedBlockManager.isBlockPlaced(block)) {
            return false;
        }

        // Resolve the block material once
        XMaterial blockMaterial = CompatibleMaterial.getMaterial(block.getType()).orElse(null);

        // Check if it matches the tree definition
        boolean isCorrectType = false;
        for (TreeDefinition treeDefinition : treeDefinitions) {
            for (XMaterial material : treeDefinition.getLogMaterial()) {
                if (material == blockMaterial) {
                    isCorrectType = true;
                    break;
                }
            }
        }

        if (!isCorrectType) {
            return false;
        }

        // Check that it is close enough to the trunk
        if (trunkBlocks == null || trunkBlocks.isEmpty()) {
            return true;
        }

        Location location = block.getLocation();
        for (TreeDefinition treeDefinition : treeDefinitions) {
            double maxDistance = treeDefinition.getMaxLogDistanceFromTrunk() * treeDefinition.getMaxLogDistanceFromTrunk();
            if (!this.onlyBreakLogsUpwards) // Help detect logs more often if the tree isn't broken at the base
            {
                maxDistance *= 1.5;
            }
            for (Block trunkBlock : trunkBlocks) {
                if (location.distanceSquared(trunkBlock.getLocation()) < maxDistance) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if a given block is valid for the given TreeDefinitions
     *
     * @param treeDefinitions The Set of TreeDefinitions to compare against
     * @param treeBlocks      The detected blocks of the tree for checking leaf distance
     * @param block           The Block to check
     * @param blockMaterial   The pre-resolved XMaterial of the block
     * @return True if the block is a valid leaf type, otherwise false
     */
    private boolean isValidLeafType(Set<TreeDefinition> treeDefinitions, TreeBlockSet<Block> treeBlocks, Block block, XMaterial blockMaterial) {
        // Check if block is placed
        if (this.placedBlockManager.isBlockPlaced(block)) {
            return false;
        }

        // Check if it matches the tree definition
        boolean isCorrectType = false;
        for (TreeDefinition treeDefinition : treeDefinitions) {
            for (XMaterial material : treeDefinition.getLeafMaterial()) {
                if (material == blockMaterial) {
                    isCorrectType = true;
                    break;
                }
            }
        }

        if (!isCorrectType) {
            return false;
        }

        // Check that it is close enough to a log
        if (treeBlocks == null || treeBlocks.isEmpty()) {
            return true;
        }

        // Compute max distance once
        int maxDistanceFromLog = 0;
        for (TreeDefinition treeDefinition : treeDefinitions) {
            int dist = treeDefinition.getMaxLeafDistanceFromLog();
            if (dist > maxDistanceFromLog) {
                maxDistanceFromLog = dist;
            }
        }
        double maxDistanceSquared = (double) maxDistanceFromLog * maxDistanceFromLog;
        Location blockLocation = block.getLocation();
        for (ITreeBlock<Block> logBlock : treeBlocks.getLogBlocks()) {
            if (logBlock.getLocation().distanceSquared(blockLocation) < maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }
}
