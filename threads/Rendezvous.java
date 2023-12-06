package nachos.threads;

import java.util.HashMap;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */     

    public Rendezvous () {
        lock = new Lock();
        createCondLock = new Lock();
        conds = new HashMap<>();
        tagvals = new HashMap<>();
    }
    

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    
    public int exchange (int tag, int value) {
        createCondLock.acquire();
        if(conds.containsKey(tag) == false) {
            conds.put(tag, new Condition2(lock));
        }
        createCondLock.release();

        lock.acquire();
        if(tagvals.containsKey(tag) == false) { // thread 1
            tagvals.put(tag, value);
            conds.get(tag).sleep();
            
            int valExchange = tagvals.get(tag);
            tagvals.remove(tag);
            lock.release();
            return valExchange;
        }
        else {
            int valExchange = tagvals.get(tag); // thread 2
            tagvals.put(tag, value);

            conds.get(tag).wake();
            lock.release();

            return valExchange;
        }	  
    }

    private Lock lock;
    private Lock createCondLock;
    private HashMap<Integer, Condition2> conds;
    private HashMap <Integer, Integer> tagvals;

    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
            t2.setName("t2");
    
            t1.fork(); t2.fork();
        // assumes join is implemented correctly
            t1.join(); t2.join();
        }
    
        public static void rendezTest2() {
            final Rendezvous r = new Rendezvous();
        
            KThread t1 = new KThread( new Runnable () {
                public void run() {
                    int tag = 1;
                    int send = -10;
        
                    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange (tag, send);
                    Lib.assertTrue (recv == 4, "Was expecting " + 4 + " but received " + recv);
                    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
                });
            t1.setName("t1");
            KThread t2 = new KThread( new Runnable () {
                public void run() {
                    int tag = 1;
                    int send = 4;
        
                    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange (tag, send);
                    Lib.assertTrue (recv == -10, "Was expecting " + -10 + " but received " + recv);
                    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
                });
                t2.setName("t2");
        
        
                KThread t3 = new KThread( new Runnable () {
                    public void run() {
                        int tag = 2;
                        int send = 0;
            
                        System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                        int recv = r.exchange (tag, send);
                        Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                        System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
                    }
                    });
                t3.setName("t3");
                KThread t4 = new KThread( new Runnable () {
                    public void run() {
                        int tag = 2;
                        int send = -1;
            
                        System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                        int recv = r.exchange (tag, send);
                        Lib.assertTrue (recv == 0, "Was expecting " + 0 + " but received " + recv);
                        System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
                    }
                    });
                    t4.setName("t4");
                    
                    t1.fork(); t2.fork();
                    // assumes join is implemented correctly
                    t3.fork(); t4.fork();
                // assumes join is implemented correctly
                    t1.join(); t2.join();
                    t3.join(); t4.join();
                

            }
        // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()
    
        public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
            rendezTest1();
            rendezTest2();   
        }
}
