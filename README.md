# Rename Commit - Intellij Plugin

Simple intellij plugin for renaming the most recent git commit. After choosing option of renaming plugin,
user is prompted to input new commit message. 
After that, plugin will rename the commit and show a notification about success.
If given message is empty, or commit doesn't exist yet, appropriate message will be shown.
If commit is already pushed to remote repository, user can decide if he wants to force push the commit or not.
Cancellation is also handled properly.

## Running:
1. Clone the repository:
    ``` 
    git clone https://github.com/Axan18/RenameCommitPlugin-Intellij.git 
   ```
2. Open cloned project in Intellij Idea
3. Open Gradle tool window (View -> Tool Windows -> Gradle) and reload project
4. Run the plugin:
    - In the Gradle tool window, navigate to Tasks -> intellij -> runIde
    - Double-click on the task to run it

Plugin is located in git section of the menu bar.