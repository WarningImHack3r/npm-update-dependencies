# Troubleshooting

## Enable and extract logs

You can enable the plugin's debug logs to help diagnose your issue. Here's how you can do it:

1. Enable the plugin's debug logs by setting the line below in <kbd>Help</kbd> > <kbd>Diagnostic Tools</kbd> > <kbd>Debug Log Settings…</kbd>:
  ```
  #com.github.warningimhack3r.npmupdatedependencies
  ```

2. Restart your IDE, wait for your problem to occur or trigger it, and open the directory containing the `idea.log` file by going to <kbd>Help</kbd> > <kbd>Show Log in Finder</kbd>/<kbd>Show Log in Explorer</kbd>.

3. In there, you have 3 options to copy the logs:
   - Open the `idea.log` file with a text editor or a log viewer and copy the parts related to the plugin, i.e., the lines containing `npmupdatedependencies`. You can for example use tools like `grep` to filter the logs.
   - Open the `idea.log` file with a text editor or a log viewer and copy the last 50–100 lines.
   - If you're comfortable with it, you can share the whole `idea.log` file.

4. To share it, you have a few options:
   - Share them in the issue using [Markdown code blocks](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-and-highlighting-code-blocks#fenced-code-blocks)
   - Paste them in a text file you can drag and drop in the issue (or directly drag and drop the `idea.log` file)
   - Use a service like [GitHub Gist](https://gist.github.com/) or [Pastebin](https://pastebin.com/) and send the link

5. It is highly recommended to **disable the debug logs after you're done**: debug logs can slow down your IDE and take up a lot of disk space. The <kbd>Debug Log Settings…</kbd> process also enables internal plugin features that are not meant for regular use.

## Share your `package.json` file

The `package.json` file is the key for the plugin to get most of the information it needs to work.  
In most cases of crashes or unexpected behavior, I'll ask you to share your `package.json` file so I can reproduce the issue on my side.

If you don't want to share it, you're free to do so, but it may be harder for me to help you.

Only a few fields are relevant to me: `dependencies`, `devDependencies`, and `packageManager`. When you share them or the whole file, please do not omit any information from them, as it may be crucial to diagnosing the issue.

You can either:
- Copy the content of the `package.json` file or the relevant fields and paste it/them in the issue using [Markdown code blocks](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-and-highlighting-code-blocks#fenced-code-blocks)
- Paste the content in a text file you can drag and drop in the issue, or directly drag and drop the `package.json` file
- Use a service like [GitHub Gist](https://gist.github.com/) or [Pastebin](https://pastebin.com/) and send the link

## Run commands manually in the terminal

If you're having trouble with the plugin, I may ask you to run some commands manually in the terminal. Here's how you can do it:

1. **Open the terminal** in your IDE by going to <kbd>View</kbd> > <kbd>Tool Windows</kbd> > <kbd>Terminal</kbd>. You can also use the terminal of your choice outside of the IDE, but you'll need to navigate to your project's directory.

2. **Run the command(s)** I ask you to run. They often have no side effects and are run by the plugin in its regular operation, but it's always good to double-check. If you're unsure about the command, you can ask me for more details or search for the command's documentation.

3. **I most often don't need the output of the command**; I just need to know if it runs without errors. If it does, you can tell me that it ran successfully. If it doesn't, you can share the error message with me. If you're comfortable with it, the output is short, and you don't really understand the output, you can share it with me: I'll pick the relevant parts and delete your message afterward if it contains sensitive information.

## Clear the plugin's cache

The plugin caches some data to speed up its operations. If you're having trouble with the plugin, you can clear its cache if I ask you to do so or part of your regular use of the plugin if you think it may help.

Here's how you can do it:

1. Open the `package.json` file of your project.

2. Click the `Invalidate Scan Caches` option by either:
   - Right-clicking on the file and selecting <kbd>NPM Update Dependencies</kbd> > <kbd>Invalidate Scan Caches</kbd>.
   - Going to <kbd>Tools</kbd> > <kbd>NPM Update Dependencies</kbd> > <kbd>Invalidate Scan Caches</kbd> while the file is open and focused.
