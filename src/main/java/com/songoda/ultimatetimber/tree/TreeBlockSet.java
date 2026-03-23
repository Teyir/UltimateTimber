package com.songoda.ultimatetimber.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TreeBlockSet<BlockType> implements Collection<ITreeBlock<BlockType>> {
    private final ITreeBlock<BlockType> initialLogBlock;
    private List<ITreeBlock<BlockType>> logBlocks;
    private final List<ITreeBlock<BlockType>> leafBlocks;
    private final Set<ITreeBlock<BlockType>> allTreeBlocks;
    private final Set<ITreeBlock<BlockType>> allTreeBlocksView;

    public TreeBlockSet() {
        this.initialLogBlock = null;
        this.logBlocks = new LinkedList<>();
        this.leafBlocks = new LinkedList<>();
        this.allTreeBlocks = new HashSet<>();
        this.allTreeBlocksView = Collections.unmodifiableSet(this.allTreeBlocks);
    }

    public TreeBlockSet(ITreeBlock<BlockType> initialLogBlock) {
        this.initialLogBlock = initialLogBlock;
        this.logBlocks = new LinkedList<>();
        this.leafBlocks = new LinkedList<>();
        this.allTreeBlocks = new HashSet<>();
        this.allTreeBlocksView = Collections.unmodifiableSet(this.allTreeBlocks);

        if (initialLogBlock != null) {
            this.logBlocks.add(initialLogBlock);
            this.allTreeBlocks.add(initialLogBlock);
        }
    }

    /**
     * Gets the TreeBlock that initiated the tree topple
     *
     * @return The TreeBlock of the initial topple point
     */
    public ITreeBlock<BlockType> getInitialLogBlock() {
        return this.initialLogBlock;
    }

    /**
     * Gets all logs in this TreeBlockSet
     *
     * @return A Set of TreeBlocks
     */
    public List<ITreeBlock<BlockType>> getLogBlocks() {
        return Collections.unmodifiableList(this.logBlocks);
    }

    /**
     * Gets all leaves in this TreeBlockSet
     *
     * @return A Set of TreeBlocks
     */
    public List<ITreeBlock<BlockType>> getLeafBlocks() {
        return Collections.unmodifiableList(this.leafBlocks);
    }

    /**
     * Gets all blocks in this TreeBlockSet
     *
     * @return A Set of all TreeBlocks
     */
    public Set<ITreeBlock<BlockType>> getAllTreeBlocks() {
        return this.allTreeBlocksView;
    }

    private void rebuildAllTreeBlocksSet() {
        this.allTreeBlocks.clear();
        this.allTreeBlocks.addAll(this.logBlocks);
        this.allTreeBlocks.addAll(this.leafBlocks);
    }

    @Override
    public int size() {
        return this.allTreeBlocks.size();
    }

    @Override
    public boolean isEmpty() {
        return this.allTreeBlocks.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.allTreeBlocks.contains(o);
    }

    @Override
    public Iterator<ITreeBlock<BlockType>> iterator() {
        return this.getAllTreeBlocks().iterator();
    }

    @Override
    public Object[] toArray() {
        return this.getAllTreeBlocks().toArray();
    }

    @Override
    public boolean add(ITreeBlock<BlockType> treeBlock) {
        if (treeBlock == null) {
            return false;
        }

        if (!this.allTreeBlocks.add(treeBlock)) {
            return false;
        }

        boolean added;
        switch (treeBlock.getTreeBlockType()) {
            case LOG:
                added = this.logBlocks.add(treeBlock);
                break;
            case LEAF:
                added = this.leafBlocks.add(treeBlock);
                break;
            default:
                this.allTreeBlocks.remove(treeBlock);
                return false;
        }

        return added;
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof ITreeBlock)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        ITreeBlock<BlockType> treeBlock = (ITreeBlock<BlockType>) o;

        if (!this.allTreeBlocks.remove(treeBlock)) {
            return false;
        }

        boolean removed;
        switch (treeBlock.getTreeBlockType()) {
            case LOG:
                removed = this.logBlocks.remove(treeBlock);
                break;
            case LEAF:
                removed = this.leafBlocks.remove(treeBlock);
                break;
            default:
                this.allTreeBlocks.add(treeBlock);
                return false;
        }

        if (!removed) {
            this.allTreeBlocks.add(treeBlock);
        }

        return removed;
    }

    @Override
    public boolean addAll(Collection<? extends ITreeBlock<BlockType>> c) {
        boolean allAdded = true;
        for (ITreeBlock<BlockType> treeBlock : c) {
            if (!this.add(treeBlock)) {
                allAdded = false;
            }
        }
        return allAdded;
    }

    @Override
    public void clear() {
        if (this.allTreeBlocks.isEmpty()) {
            return;
        }

        this.logBlocks.clear();
        this.leafBlocks.clear();
        this.allTreeBlocks.clear();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = this.logBlocks.retainAll(c);
        changed |= this.leafBlocks.retainAll(c);

        if (changed) {
            this.rebuildAllTreeBlocksSet();
        }

        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = this.logBlocks.removeAll(c);
        changed |= this.leafBlocks.removeAll(c);

        if (changed) {
            this.rebuildAllTreeBlocksSet();
        }

        return changed;
    }

    public void sortAndLimit(int max) {
        if (this.logBlocks.size() < max) {
            return;
        }

        this.logBlocks = this.logBlocks.stream().sorted(Comparator.comparingInt(b -> b.getLocation().getBlockY()))
                .limit(max).collect(Collectors.toList());

        int highest = this.logBlocks.get(this.logBlocks.size() - 1).getLocation().getBlockY();

        if (this.logBlocks.size() >= max) {
            for (ITreeBlock<BlockType> leafBlock : new LinkedList<>(this.leafBlocks)) {
                if (leafBlock.getLocation().getY() > highest) {
                    this.leafBlocks.remove(leafBlock);
                }
            }
        }

        this.rebuildAllTreeBlocksSet();
    }

    /**
     * Removes all tree blocks of a given type
     *
     * @param treeBlockType The type of tree block to remove
     * @return If any blocks were removed
     */
    public boolean removeAll(TreeBlockType treeBlockType) {
        if (treeBlockType == TreeBlockType.LOG) {
            boolean removedAny = !this.logBlocks.isEmpty();
            this.logBlocks.clear();
            if (removedAny) {
                this.rebuildAllTreeBlocksSet();
            }
            return removedAny;
        }
        if (treeBlockType == TreeBlockType.LEAF) {
            boolean removedAny = !this.leafBlocks.isEmpty();
            this.leafBlocks.clear();
            if (removedAny) {
                this.rebuildAllTreeBlocksSet();
            }
            return removedAny;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!this.contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.getAllTreeBlocks().toArray(a);
    }
}
