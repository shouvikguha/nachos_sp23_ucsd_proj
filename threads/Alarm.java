package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	
	//We will be using a priority queue to store threads that are currently
	//waiting to be woken up. This will allow us to store the threads in a way 
	//where the threads with the highest priority (closer wake up times) will be woken sooner
	private PriorityQueue<waiting_thread> currently_waiting;

	public Alarm() {
		currently_waiting = new PriorityQueue<waiting_thread>();
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	//Class for threads that are currently waiting from waitUntil()
	//has waketime for the thread, links it to the current thread (the one that is being slept)
	//implements Comparable so we can implement a comparison method for two different strings
	//that are waiting.
	//wake_thread() wakes up the thread
	public class waiting_thread implements Comparable<waiting_thread> {
		private long wakeTime;
		private KThread curr;
		public waiting_thread(long wakeTime, KThread curr) {
			this.wakeTime = wakeTime;
			this.curr = KThread.currentThread();
		}
		
		@Override
		public int compareTo(waiting_thread o) {
			return Long.valueOf(wakeTime).compareTo(o.wakeTime);
		}

		public void wake_thread() {
			curr.ready();
		}
		
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		while(currently_waiting.isEmpty() == false && currently_waiting.peek().wakeTime <= Machine.timer().getTime()) {
			waiting_thread waiting = currently_waiting.poll();
			waiting.wake_thread();
		}
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		if(x <= 0) {
			return;
		}
		long wakeTime = Machine.timer().getTime() + x;
		boolean status = Machine.interrupt().disable();
		KThread curr = KThread.currentThread();
		waiting_thread thread = new waiting_thread(wakeTime, curr);
		synchronized(currently_waiting) {
			currently_waiting.add(thread);
		}
		curr.sleep();
		Machine.interrupt().restore(status);
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
    public boolean cancel(KThread thread) {
		boolean status = Machine.interrupt().disable();

		for(waiting_thread wt: currently_waiting) {
			if(wt.curr == thread) {
				currently_waiting.remove(wt);
				wt.curr.ready();
				Machine.interrupt().restore(status);
				return false;
			}
		}
		
		Machine.interrupt().restore(status);
		return false;
	}
    // Add Alarm testing code to the Alarm class
    
    public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
	
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void alarmTest2() {
		long start, end, timer;
		timer = 0;
		start = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(timer);
		end = Machine.timer().getTime();
		System.out.println("alarmTest2: waited for " + (end - start) + " ticks");
	}

	public static void alarmTest3() {
		long start, end, timer;
		timer = -10;
		start = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(timer);
		end = Machine.timer().getTime();
		System.out.println("alarmTest3: waited for " + (end - start) + " ticks");
	}

	
	// Implement more test methods here ...
	
	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();
		alarmTest2();
		alarmTest3();
	}
}
