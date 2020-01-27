package com.songoda.ultimatetimber.hook;

import com.gmail.nossr50.api.AbilityAPI;
import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.SecondaryAbility;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.PerksUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.songoda.ultimatetimber.tree.ITreeBlock;
import com.songoda.ultimatetimber.tree.TreeBlockSet;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;

public class McMMOClassic12Hook implements TimberHook {

    @Override
    public void applyExperience(Player player, TreeBlockSet<Block> treeBlocks) {
        if (player.getGameMode().equals(GameMode.CREATIVE))
            return;

        int xp = 0;
        for (ITreeBlock<Block> treeBlock : treeBlocks.getLogBlocks()) {
            Block block = treeBlock.getBlock();
            MaterialData materialData = block.getState().getData();
            xp += ExperienceConfig.getInstance().getXp(SkillType.WOODCUTTING, materialData);
        }

        ExperienceAPI.addXP(player, "woodcutting", xp, "pve");
    }

    @Override
    public boolean shouldApplyDoubleDrops(Player player) {
        if (player == null || !player.hasMetadata("mcMMO: Player Data") || SkillType.WOODCUTTING.getDoubleDropsDisabled())
            return false;

        int skillLevel = UserManager.getPlayer(player).getSkillLevel(SkillType.WOODCUTTING);
        int activationChance = PerksUtils.handleLuckyPerks(player, SkillType.WOODCUTTING);
        return Permissions.secondaryAbilityEnabled(player, SecondaryAbility.WOODCUTTING_DOUBLE_DROPS)
                && SkillUtils.activationSuccessful(SecondaryAbility.WOODCUTTING_DOUBLE_DROPS, player, skillLevel, activationChance);
    }

    @Override
    public boolean isUsingAbility(Player player) {
        return AbilityAPI.treeFellerEnabled(player);
    }

}