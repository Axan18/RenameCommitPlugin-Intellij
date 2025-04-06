package axan18.commitrenameplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.history.GitHistoryUtils;
import org.jetbrains.annotations.NotNull;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import com.intellij.openapi.project.Project;

import java.util.Collections;
import java.util.List;

public class CommitRenameAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return; // No project is open
        }
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
        GitRepository repository = repositoryManager.getRepositories().stream()
                .filter(repo -> repo.getRoot().getPath().equals(project.getBasePath()))
                .findFirst()
                .orElse(null);
        if (repository == null) {
            Messages.showMessageDialog("Repository not found", "Error", Messages.getErrorIcon());
            return; // No repository found
        }
        String newCommitMessage = Messages.showInputDialog(project, "Enter new commit message:", "Commit Rename", Messages.getQuestionIcon());
        if (newCommitMessage == null || newCommitMessage.isEmpty()) {
            Messages.showMessageDialog("Commit message cannot be empty", "Error", Messages.getErrorIcon());
            return; // No commit message provided
        }
        Task.Backgroundable task = new Task.Backgroundable(project, "Amending Last Commit") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                VirtualFile root = repository.getRoot();
                List<GitCommit> history;
                try {
                    history = GitHistoryUtils.history(project, root, "-1");
                } catch (VcsException e) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showMessageDialog(project, "No commits found", "Error", Messages.getErrorIcon())
                    );
                    return;
                }
                GitLineHandler handler = new GitLineHandler(project, root, GitCommand.COMMIT);
                handler.setSilent(false);
                handler.addParameters("--amend");
                handler.addParameters("-m", newCommitMessage);
                GitCommandResult result = Git.getInstance().runCommand(handler);
                if (!result.success()) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showMessageDialog(project, "Failed to rename commit: " + result.getErrorOutputAsJoinedString(), "Error", Messages.getErrorIcon())
                    );
                    return;
                }
                GitRepositoryManager.getInstance(project).updateRepository(root);
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showMessageDialog(project, "Commit successfully renamed", "Success", Messages.getInformationIcon())
                );
            }
        };
        ProgressManager.getInstance().run(task);

    }
}
