package pshb.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * OutputWriter is a file management utility class that handles creating, locating, and appending to output files,
 * supporting both IDE and JAR execution environments for consistent model input/output operations.
 */
public class OutputWriter {
    public String outputFilePath;
    private BufferedWriter writer;

    public OutputWriter(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public void createFile(String[] header) {
        try {
            File myObj = new File(this.outputFilePath);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getAbsolutePath());
            } else {
                System.out.println("File already exists.");
            }
            // Open writer once and keep it open for the simulation lifetime
            writer = new BufferedWriter(new FileWriter(this.outputFilePath, true));
            if (header.length > 0) {
                addToFile(String.join(",", header));
            }
        } catch (IOException e) {
            System.out.println("An error occurred when creating the file.");
            e.printStackTrace();
        }
    }

    public void addToFile(String text) {
        try {
            // No per-write flush: BufferedWriter batches writes and is flushed on close().
            // Flushing every line forces a synchronous disk sync and is a major slowdown.
            writer.write(text + "\n");
        } catch (IOException e) {
            System.out.println("exception occurred when addToFile" + e);
        }
    }

    public void close() {
        try {
            if (writer != null) { writer.close(); }
        } catch (IOException e) {
            System.out.println("exception occurred when closing writer: " + e);
        }
    }

    public static String getFileName(String fileName, boolean mustExist) throws IOException {
        try {
            File codeSource = new File(
                    OutputWriter.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );

            boolean runningFromJar = codeSource.isFile();
            File baseDir;

            if (runningFromJar) {
                // 🎯 Running from JAR: just use the JAR’s containing directory
                baseDir = codeSource.getParentFile();
            } else {
                // 🧠 Running in IDE: use working directory and walk up to find "src"
                baseDir = new File(System.getProperty("user.dir"));
                while (baseDir != null && !containSrc(baseDir)) {
                    baseDir = baseDir.getParentFile();
                }
                if (baseDir == null) {
                    throw new IOException("Couldn't find project root with 'src' folder.");
                }
                baseDir = baseDir.getParentFile(); // One level above root
            }
            //Wherever you're getting the file name for writing, set mustExist = false
            //if you are reading a file, like input config, set mustExist = true
            File target = new File(baseDir, fileName);
            if (mustExist && !target.exists()) {
                throw new IOException("File not found: " + target.getAbsolutePath());
            }
            if(!mustExist) {
                File parent = target.getParentFile();
                if(parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
            }

            return target.getAbsolutePath();

        } catch (URISyntaxException e) {
            throw new IOException("Failed to resolve path", e);
        }
    }


    //Helper: detects if a folder is the project root by looking for "src"
    private static boolean containSrc(File dir) {
        File[] childern = dir.listFiles();
        if(childern == null) {return false;}
        for(File child : childern) {
            if(child.getName().equals("src") && child.isDirectory()) {
                return true;
            }
        }
        return false;
    }
}
