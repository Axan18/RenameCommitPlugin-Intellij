package axan18.commitrenameplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.util.concurrent.CompletableFuture;

public class MessagesHandler {

    public static void showErrorDialog(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(message, "Error")
        );
    }
    public static void showInfoDialog(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showInfoMessage(message, "Information")
        );
    }
    public static boolean canForcePushDialog(Project project) {
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


}
