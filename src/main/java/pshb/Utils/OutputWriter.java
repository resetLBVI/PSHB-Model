package pshb.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

public class OutputWriter {
    public String outputFilePath;

    public OutputWriter(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public void createFile(String[] header) {
        // create file
        try {
            File myObj = new File(this.outputFilePath);
            if (myObj.createNewFile()) {
                if (header.length > 0){
                    this.addToFile(String.join(",", header));
                }
                System.out.println("File created: " + myObj.getAbsolutePath());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void addToFile(String text) {
        // Try block to check for exceptions
        try {
            // Open given file in append mode by creating an
            // object of BufferedWriter class
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(this.outputFilePath, true));
            // Writing on output stream
            out.write(text + "\n");
            // Closing the connection
            out.close();
        }

        // Catch block to handle the exceptions
        catch (IOException e) {

            // Display message when exception occurs
            System.out.println("exception occurred" + e);
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
