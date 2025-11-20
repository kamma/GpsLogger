package cz.kamma.gpslogger;

import java.io.Serializable;

public class ReplayState implements Serializable {

    private final boolean isReplaying;
    private final boolean isPaused;
    private final boolean isReverse;
    private final int replaySpeed;
    private final int currentPosition;
    private final int maxPosition;
    private final String fileName;

    public ReplayState(boolean isReplaying, boolean isPaused, boolean isReverse, int replaySpeed, int currentPosition, int maxPosition, String fileName) {
        this.isReplaying = isReplaying;
        this.isPaused = isPaused;
        this.isReverse = isReverse;
        this.replaySpeed = replaySpeed;
        this.currentPosition = currentPosition;
        this.maxPosition = maxPosition;
        this.fileName = fileName;
    }

    public boolean isReplaying() {
        return isReplaying;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isReverse() {
        return isReverse;
    }

    public int getReplaySpeed() {
        return replaySpeed;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public int getMaxPosition() {
        return maxPosition;
    }

    public String getFileName() {
        return fileName;
    }
}
