package wtf.carnation.utils;

import java.util.Arrays;

/** Port of Naven's {@code SharedESPData}. */
public final class SharedESPData {
    public String displayName;
    public double posX;
    public double posY;
    public double posZ;
    public double health;
    public double maxHealth;
    public double absorption;
    public double[] renderPosition;
    public String[] tags;
    public long updateTime;

    public SharedESPData(String displayName, double posX, double posY, double posZ, double health,
                         double maxHealth, double absorption, double[] renderPosition, String[] tags, long updateTime) {
        this.displayName = displayName;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.health = health;
        this.maxHealth = maxHealth;
        this.absorption = absorption;
        this.renderPosition = renderPosition;
        this.tags = tags;
        this.updateTime = updateTime;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getPosX() {
        return posX;
    }

    public double getPosY() {
        return posY;
    }

    public double getPosZ() {
        return posZ;
    }

    public double getHealth() {
        return health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getAbsorption() {
        return absorption;
    }

    public double[] getRenderPosition() {
        return renderPosition;
    }

    public String[] getTags() {
        return tags;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public void setPosZ(double posZ) {
        this.posZ = posZ;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public void setAbsorption(double absorption) {
        this.absorption = absorption;
    }

    public void setRenderPosition(double[] renderPosition) {
        this.renderPosition = renderPosition;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "SharedESPData(" + displayName + "," + posX + "," + posY + "," + posZ + ","
                + Arrays.toString(tags) + ")";
    }
}
