package com.songoda.ultimatetimber.manager;

import com.songoda.ultimatetimber.UltimateTimber;
import com.songoda.ultimatetimber.tree.TreeBlock;
import com.songoda.ultimatetimber.tree.TreeDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.Deque;

public class BlockReplacementManager extends Manager implements Runnable {
    private final Deque<QueuedBlockReplacement> queue;

    private String mode;
    private int playerThreshold;
    private int maxPerTick;

    public BlockReplacementManager(UltimateTimber plugin) {
        super(plugin);
        this.queue = new ArrayDeque<>();
        Bukkit.getScheduler().runTaskTimer(plugin, this, 0, 1L);
    }

    @Override
    public void reload() {
        this.mode = ConfigurationManager.Setting.QUEUED_BLOCK_REPLACEMENT.getString();
        this.playerThreshold = ConfigurationManager.Setting.QUEUED_BLOCK_REPLACEMENT_THRESHOLD.getInt();
        this.maxPerTick = ConfigurationManager.Setting.QUEUED_BLOCK_REPLACEMENT_MAX_PER_TICK.getInt();
    }

    @Override
    public void disable() {
        this.processAll();
    }

    @Override
    public void run() {
        if (this.queue.isEmpty()) {
            return;
        }

        int processed = 0;
        while (!this.queue.isEmpty() && processed < this.maxPerTick) {
            QueuedBlockReplacement entry = this.queue.poll();
            this.executeReplacement(entry);
            processed++;
        }
    }

    /**
     * Replaces a block, either immediately or queued depending on the current mode
     *
     * @param treeBlock      The TreeBlock to replace
     * @param treeDefinition The TreeDefinition for sapling replanting
     */
    public void replaceBlock(TreeBlock treeBlock, TreeDefinition treeDefinition) {
        if (this.isQueuingActive()) {
            this.queue.add(new QueuedBlockReplacement(treeBlock, treeDefinition));
        } else {
            this.executeReplacement(new QueuedBlockReplacement(treeBlock, treeDefinition));
        }
    }

    /**
     * Checks if queuing is currently active based on the mode and player count
     *
     * @return True if queuing should be used
     */
    private boolean isQueuingActive() {
        switch (this.mode.toUpperCase()) {
            case "ALWAYS":
                return true;
            case "DYNAMIC":
                return Bukkit.getOnlinePlayers().size() >= this.playerThreshold;
            default:
                return false;
        }
    }

    /**
     * Immediately executes a single block replacement
     */
    private void executeReplacement(QueuedBlockReplacement entry) {
        entry.treeBlock.getBlock().setType(Material.AIR);
        this.plugin.getSaplingManager().replantSapling(entry.treeDefinition, entry.treeBlock);
    }

    /**
     * Processes all remaining queued replacements immediately
     */
    private void processAll() {
        while (!this.queue.isEmpty()) {
            this.executeReplacement(this.queue.poll());
        }
    }

    private static class QueuedBlockReplacement {
        private final TreeBlock treeBlock;
        private final TreeDefinition treeDefinition;

        QueuedBlockReplacement(TreeBlock treeBlock, TreeDefinition treeDefinition) {
            this.treeBlock = treeBlock;
            this.treeDefinition = treeDefinition;
        }
    }
}
