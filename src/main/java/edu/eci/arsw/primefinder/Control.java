
package edu.eci.arsw.primefinder;

import java.util.Scanner;

public class Control extends Thread {

    private final static int NTHREADS = 3;
    private final static int MAXVALUE = 30000000;
    private final static int TMILISECONDS = 5000;

    private final int NDATA = MAXVALUE / NTHREADS;

    private PrimeFinderThread pft[];

    private final Object pauseLock = new Object();

    private Control() {

        super();

        this.pft = new PrimeFinderThread[NTHREADS];

        int i;

        for (i = 0; i < NTHREADS - 1; i++) {

            PrimeFinderThread elem =
                    new PrimeFinderThread(
                            i * NDATA,
                            (i + 1) * NDATA,
                            pauseLock);

            pft[i] = elem;
        }

        pft[i] =
                new PrimeFinderThread(
                        i * NDATA,
                        MAXVALUE + 1,
                        pauseLock);
    }

    public static Control newControl() {
        return new Control();
    }

    @Override
    public void run() {

        for (int i = 0; i < NTHREADS; i++) {
            pft[i].start();
        }

        Scanner scanner = new Scanner(System.in);

        try {

            while (true) {

                Thread.sleep(TMILISECONDS);

                for (PrimeFinderThread thread : pft) {
                    thread.pauseThread();
                }

                int totalPrimes = 0;

                for (PrimeFinderThread thread : pft) {
                    totalPrimes += thread.getPrimes().size();
                }

                System.out.println("EJECUCION PAUSADA");
                System.out.println("El total de primos encontrados fue de:" + totalPrimes);
                System.out.println("Presione ENTER para continuar");


                scanner.nextLine();

                for (PrimeFinderThread thread : pft) {
                    thread.resumeThread();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}