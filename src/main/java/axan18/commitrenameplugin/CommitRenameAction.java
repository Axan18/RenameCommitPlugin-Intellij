package axan18.commitrenameplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class CommitRenameAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        GitService gitService = new GitService(e.getProject());
        Project project = e.getProject();
        if (project == null) // No project is open
            return;

        if (!gitService.repositoryExists()) { // No repository found
            Messages.showMessageDialog("Repository not found", "Error", Messages.getErrorIcon());
            return;
        }
        String newCommitMessage;
        while (true) {
            newCommitMessage = Messages.showInputDialog(project, "Enter new commit message:", "Rename Commit", Messages.getQuestionIcon());
            if (newCommitMessage == null) // User cancelled the input dialog
                return;
            else if (newCommitMessage.isEmpty()) // Empty commit message
                Messages.showMessageDialog("Commit message cannot be empty", "Error", Messages.getErrorIcon());
             else  // Valid commit message
                break;
        }
        gitService.changeLastCommitMessage(newCommitMessage);
    }
}
