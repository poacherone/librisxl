package whelk;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class PortableScript implements Serializable
{
    final String scriptText;
    final Set<String> ids;
    final public String comment;
    final boolean useThreads;

    public PortableScript(String scriptText, Set<String> ids, String comment, boolean useThreads)
    {
        this.scriptText = scriptText;
        if (ids != null)
            this.ids = Collections.unmodifiableSet(ids);
        else
            this.ids = null;
        this.comment = comment;
        this.useThreads = useThreads;
    }

    public Path execute() throws IOException
    {
        Path scriptWorkingDir = Files.createTempDirectory("xl_script");
        Path scriptFilePath = scriptWorkingDir.resolve("script.groovy");
        Path inputFilePath = scriptWorkingDir.resolve("input");
        Path reportPath = scriptWorkingDir.resolve("report");
        Files.createDirectories(reportPath);

        String flattenedScriptText = scriptText;
        if (ids != null)
        {
            Files.write(inputFilePath, ids);
            // On windows inputFilePath.toString() produces backslashes, which need to be escaped.
            flattenedScriptText = scriptText.replace("INPUT", inputFilePath.toString().replace("\\", "\\\\"));
        }
        Files.write(scriptFilePath, flattenedScriptText.getBytes());

        if (useThreads) {
            String[] args =
                    {
                            "--allow-loud",
                            "--report",
                            reportPath.toString(),
                            scriptFilePath.toString(),
                    };

            whelk.datatool.WhelkTool.main(args);
        } else {
            String[] args =
                    {
                            "--no-threads",
                            "--allow-loud",
                            "--report",
                            reportPath.toString(),
                            scriptFilePath.toString(),
                    };

            whelk.datatool.WhelkTool.main(args);
        }

        return reportPath;
    }
}