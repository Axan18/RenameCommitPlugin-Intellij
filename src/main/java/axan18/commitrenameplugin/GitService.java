package axan18.commitrenameplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitRemoteBranch;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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
                    if (commitMissing())
                        MessagesHandler.showErrorDialog("Commit not found");
                    try {
                        if (isLastCommitPushed()) {
                            if (MessagesHandler.canForcePushDialog(project)) {
                                amendRemoteCommit(newCommitMessage);
                            } else return;
                        }
                        else {
                            amendLastCommit(newCommitMessage);
                        }
                    } catch (VcsException e) {
                        MessagesHandler.showErrorDialog("Failed to rename commit: " + e.getMessage());
                        return;
                    }
                    GitRepositoryManager.getInstance(project).updateRepository(repository.getRoot());
                    MessagesHandler.showInfoDialog("Commit message changed successfully");
                }
            });
        });
    }
    private void amendRemoteCommit(String newCommitMessage) throws VcsException{
        GitCommandResult result = amendLastCommit(newCommitMessage);
        if (!result.success()) {
            throw new VcsException(result.getErrorOutputAsJoinedString());
        }
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.PUSH);
        handler.setSilent(false);
        String remoteURL = repository.getRemotes().iterator().next().getFirstUrl();
        if(remoteURL == null) {
            throw new VcsException("No remote repository found");
        }
        handler.setUrl(remoteURL);
        handler.addParameters("--force-with-lease");// to prevent overwriting other's commits
        handler.addParameters("origin", repository.getCurrentBranchName());
        Git.getInstance().runCommand(handler);
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

    public boolean isLastCommitPushed() {
        // Fetch remote branches to make sure we have the latest data
        GitLineHandler fetchHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.FETCH);
        Git.getInstance().runCommand(fetchHandler);

        // Get the latest commit from the local branch
        GitLineHandler localCommitHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.LOG);
        localCommitHandler.addParameters("-1");
        String localCommitHash = Git.getInstance().runCommand(localCommitHandler).getOutput().get(0).split(" ")[1];

        // Check if this commit exists in the remote branch
        GitLineHandler remoteCommitHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.LOG);
        remoteCommitHandler.addParameters("origin/" + repository.getCurrentBranchName(), "-n", "1");
        String remoteCommitHash = Git.getInstance().runCommand(remoteCommitHandler).getOutput().get(0).split(" ")[1];

        return localCommitHash.equals(remoteCommitHash);
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
}
