package pshb.Utils;

/**
 * Calculation is a utility class that provides mathematical helper functions, including a method to generate
 * Poisson-distributed random numbers based on a specified mean (λ).
 */
public class Calculation {

    public static int getPoisson (double lambda) {
        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;
        do {
            k++;
            p *= Math.random();
        } while (p > L);
        return k-1;
    }
}
