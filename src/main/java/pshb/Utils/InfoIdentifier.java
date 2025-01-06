package pshb.Utils;

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
