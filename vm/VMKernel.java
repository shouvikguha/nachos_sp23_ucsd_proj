package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapFile = ThreadedKernel.fileSystem.open("SWAPFILE", true);
		freeSwapPages = new LinkedList<>();
		currSwapPointer = 0;
		totalpins = 0;
		VMLock = new Lock();
		CV = new Condition(VMLock);
		InvertedPageTable = new InvertedPageEntry[Machine.processor().getNumPhysPages() + 1];
		for(int i = 0; i < Machine.processor().getNumPhysPages(); i++){
			InvertedPageTable[i] = new InvertedPageEntry(null, false, null);
		}
		toEvict = 0;
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
		ThreadedKernel.fileSystem.remove("SWAPFILE");
		super.terminate();
	}

	public class InvertedPageEntry {
		public VMProcess process;
		public boolean isPinned;
		public TranslationEntry entry;
	
		public InvertedPageEntry(VMProcess process, boolean isPinned, TranslationEntry entry) {
			this.process = process;
			this.isPinned = isPinned;
			this.entry = entry;
		}
	}

	public static InvertedPageEntry [] InvertedPageTable;

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';


	public static LinkedList<Integer> freeSwapPages;
	public static int totalpins = 0;
	public static int currSwapPointer = 0;
	public static  OpenFile swapFile;

	public static Condition CV;
	public static Lock VMLock;

	public static int toEvict = 0;
	
}
