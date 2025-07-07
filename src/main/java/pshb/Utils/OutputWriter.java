package pshb.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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
}
