package pshb.Utils;

/**
 * The InfoIdentifier class is a simple data container used to store and organize parsed input information from the input file.
 * Specifically, it holds: (1) A longitude coordinate (inputX) (2) A latitude coordinate (inputY)
 * (3) The number of PSHB agents (numOfPSHBAgents) associated with that location
 * It includes a constructor to initialize these values and getter methods to access each field, but no setters,
 * indicating the data is read-only after creation (i.e., immutable).
 */
public class InfoIdentifier {
    private final Double inputX;
    private final Double inputY;
    private final Integer numOfPSHBAgents;




    public InfoIdentifier(Double inputX, Double inputY, Integer numOfPSHBAgents) {
        this.inputX = inputX; //longitude first
        this.inputY = inputY; //latitude
        this.numOfPSHBAgents = numOfPSHBAgents;
    }


    /*
    ***********************************************************************
    *                       Getters
    * *********************************************************************
     */
    public double getInputX() { return inputX;}

    public double getInputY() {
        return inputY;
    }

    public int getNumOfPSHBAgents() {
        return numOfPSHBAgents;
    }

}
