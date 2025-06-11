package dumb.jaider.model;

public class StagedUpdate {
    private final String filePath;
    private final String diffContent;
    private final String commitMessage;
    private final long timestamp;

    public StagedUpdate(String filePath, String diffContent, String commitMessage) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }
        if (diffContent == null || diffContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Diff content cannot be null or empty.");
        }
        if (commitMessage == null || commitMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Commit message cannot be null or empty.");
        }

        this.filePath = filePath;
        this.diffContent = diffContent;
        this.commitMessage = commitMessage;
        this.timestamp = System.currentTimeMillis();
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDiffContent() {
        return diffContent;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "StagedUpdate{" +
               "filePath='" + filePath + '\'' +
               ", commitMessage='" + commitMessage + '\'' +
               ", timestamp=" + timestamp +
               ", diffContent='\n" + diffContent + '\n' +
               '}';
    }
}
