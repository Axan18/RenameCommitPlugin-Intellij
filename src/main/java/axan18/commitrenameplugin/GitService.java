package axan18.commitrenameplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitBranchManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitService {
    private final Project project;
    private GitRepository repository;
    public GitService(Project project) {
        this.project = project;
    }

    public boolean repositoryExists(){
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
        repository = repositoryManager.getRepositories().stream()
                .filter(repo -> repo.getRoot().getPath().equals(project.getBasePath()))
                .findFirst()
                .orElse(null);
        return repository != null;
    }
    public void changeLastCommitMessage(String newCommitMessage) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Amending last commit") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    if (commitMissing()) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                Messages.showMessageDialog(project, "No commits found", "Error", Messages.getErrorIcon())
                        );
                        return;
                    }
                    GitCommandResult result;
                    try {
                        if (isLastCommitPushed()) {
                            if (canForcePushDialog()) {
                                result = amendRemoteCommit(newCommitMessage);
                                if (!result.success()) {
                                    showErrorDialog("Failed to rename commit: " + result.getErrorOutputAsJoinedString());
                                }
                            } else
                                return;
                        } else {
                                result = amendLastCommit(newCommitMessage);
                            if (!result.success()) {
                                showErrorDialog("Failed to rename commit: " + result.getErrorOutputAsJoinedString());
                            }
                        }
                    } catch (VcsException e) {
                        showErrorDialog("Failed to rename commit: " + e.getMessage());
                        return;
                    }
                    GitRepositoryManager.getInstance(project).updateRepository(repository.getRoot());
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showMessageDialog(project, "Commit successfully renamed", "Success", Messages.getInformationIcon())
                    );
                }
            });
        });
    }
    private GitCommandResult amendRemoteCommit(String newCommitMessage) {
        GitCommandResult result = amendLastCommit(newCommitMessage);
        if (!result.success()) {
            showErrorDialog("Failed to rename commit: " + result.getErrorOutputAsJoinedString());
            return result;
        }
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.PUSH);
        handler.setSilent(false);
        String remoteURL = repository.getRemotes().iterator().next().getFirstUrl();
        if(remoteURL == null) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showMessageDialog(project, "No remote repository found", "Error", Messages.getErrorIcon())
            );
            throw new IllegalStateException("No remote repository found");
        }
        handler.setUrl(remoteURL);
        handler.addParameters("--force-with-lease");// to prevent overwriting other's commits
        handler.addParameters("origin", repository.getCurrentBranchName());
        return Git.getInstance().runCommand(handler);
    }
    private GitCommandResult amendLastCommit(String newCommitMessage) {
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.COMMIT);
        handler.setSilent(false);
        handler.addParameters("--amend", "-m", newCommitMessage);
        return Git.getInstance().runCommand(handler);
    }

    private boolean commitMissing() {
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.LOG);
        handler.addParameters("-n", "1"); // Get last commit
        GitCommandResult result = Git.getInstance().runCommand(handler);
        return !result.success() || result.getOutput().isEmpty();
    }

    private boolean isLastCommitPushed() throws VcsException {
        // Fetch remote references
        GitLineHandler fetchHandler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.FETCH);
        GitCommandResult fetchResult = Git.getInstance().runCommand(fetchHandler);
        if (!fetchResult.success()) {
            throw new VcsException("Failed to fetch from remote repository.");
        }

        // check if commit exists in remote repository
        Collection<GitRemoteBranch> remoteBranches = repository.getBranches().getRemoteBranches();
        for (GitRemoteBranch remoteBranch : remoteBranches) {
            GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.LOG);
            handler.addParameters(remoteBranch.getName(), getLastCommitId());
            GitCommandResult result = Git.getInstance().runCommand(handler);

            if (result.success()) {
                // If result is successful, it means commit exists on this remote branch
                return true;
            }
        }
        return false; // Commit not found on any remote branch
    }

    private String getLastCommitId() throws VcsException {
        GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.LOG);
        handler.addParameters("-n", "1"); // Get the last commit
        GitCommandResult result = Git.getInstance().runCommand(handler);
        if (!result.success()) {
            throw new VcsException("Failed to get last commit.");
        }

        String output = result.getOutputOrThrow();  // extracting commit ID from the output
        return output.split("\n")[0].split(" ")[1];
    }


    private boolean canForcePushDialog() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeAndWait(() -> {
            boolean result = Messages.showYesNoDialog(
                    project,
                    "The last commit has already been pushed. Do you want to force push?",
                    "Force Push",
                    Messages.getQuestionIcon()) == Messages.YES;
            future.complete(result);
        });
        return future.join();
    }
    private void showErrorDialog(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showMessageDialog(project, message, "Error", Messages.getErrorIcon())
        );
    }
}
