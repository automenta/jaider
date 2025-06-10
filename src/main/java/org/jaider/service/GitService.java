package org.jaider.service;

import java.io.File;

// Placeholder for Git Service
public interface GitService {
    boolean applyDiff(File projectRoot, String filePath, String diffContent);
    boolean revertChanges(File projectRoot, String filePath);
    boolean commitChanges(File projectRoot, String filePath, String commitMessage);
}
