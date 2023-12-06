package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		//int numPhysPages = Machine.processor().getNumPhysPages();
		// pageTable = new TranslationEntry[numPhysPages];
		// for (int i = 0; i < numPhysPages; i++) {
		// 	pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		// }
		fileTable = new OpenFile[16];
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();

		pagesUsed = new LinkedList<Integer>();
		UserKernel.PIDLock.acquire();
		PID = UserKernel.generatePID;
		UserKernel.generatePID++;
		UserKernel.totalProcesses++;
		UserKernel.PIDLock.release();

		runningChilds = new HashMap<>();
		childStatuses = new HashMap<>();
		childPID = new HashSet<>();
		lock = new Lock();
		cond = new Condition(lock);
		par = null;
		abnormalTermination = false;

	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	 
	public int readVirtualMemorySingle(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= pageSize * numPages || vaddr >= memory.length )
			return 0;

		if(pageTable == null) return 0;

		int amount = Math.min(length, memory.length - vaddr);
		int readAmount = 0, base_vaddr = vaddr;
		while(base_vaddr < vaddr + length) {
			if(base_vaddr >= numPages * pageSize || base_vaddr >= memory.length)
				break;
			int vpn = Processor.pageFromAddress(base_vaddr);
			int baseaddr_offset = Processor.offsetFromAddress(base_vaddr);
			int ppn = pageTable[vpn].ppn;
			if(pageTable[vpn].valid == false) break;
			int nextbase_vaddr = (vpn + 1) * pageSize;
			if(nextbase_vaddr < vaddr + length && nextbase_vaddr < numPages*pageSize) {
				amount = nextbase_vaddr   - base_vaddr;
			}
			else {
				amount = Math.min(vaddr + length, numPages * pageSize) - base_vaddr;
			}

			int memp_addr = ppn * pageSize + baseaddr_offset;
			System.arraycopy(memory, memp_addr, data, offset, amount);
			base_vaddr = nextbase_vaddr;
			offset += amount;
			readAmount += amount;
		}
		return readAmount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	
	public int writeVirtualMemorySingle(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= pageSize * numPages || vaddr >= memory.length)
			return 0;

		if(pageTable == null) return 0;

		int amount = Math.min(length, memory.length - vaddr);
		int writeAmount = 0, base_vaddr = vaddr;
		while(base_vaddr < vaddr + length) {
			if(base_vaddr >= numPages * pageSize || base_vaddr >= memory.length)
				break;
			int vpn = Processor.pageFromAddress(base_vaddr), baseaddr_offset = Processor.offsetFromAddress(base_vaddr);
			int ppn = pageTable[vpn].ppn;
			int nextbase_vaddr = (vpn + 1) * pageSize;
			if(pageTable[vpn].valid == false || pageTable[vpn].readOnly) break;
			if(nextbase_vaddr < (vaddr + length) && nextbase_vaddr < numPages*pageSize) {
				amount = nextbase_vaddr  - base_vaddr;
			}
			else {
				amount = Math.min(vaddr + length, numPages * pageSize) - base_vaddr;
			}

			int memp_addr = ppn * pageSize + baseaddr_offset;
			System.arraycopy(data, offset, memory, memp_addr, amount);
			base_vaddr = nextbase_vaddr;
			offset += amount;
			writeAmount += amount;
		}
		return writeAmount;
	}
	

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		UserKernel.pagesLock.acquire();

		if(numPages > UserKernel.freePages.size()) {
		//if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		if(UserKernel.freePages.size() < numPages) {
			pageTable = null;
			return false;
		}

		//Allocate page table entry
		pageTable = new TranslationEntry[numPages];
		for(int i = 0; i < numPages; i++) {
			int getFreePhysicalPage = UserKernel.freePages.pollFirst();
			pagesUsed.addLast(getFreePhysicalPage);
			pageTable[i] = new TranslationEntry(i, getFreePhysicalPage, true, false, false, false);
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				pageTable[vpn].readOnly = section.isReadOnly();			
				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		UserKernel.pagesLock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.pagesLock.acquire();
		for(int i = 0; i < numPages; i++) {
			UserKernel.freePages.addLast(pageTable[i].ppn);
			pageTable[i].valid = false;
			pageTable[i].vpn = -1;
		}
		pageTable = null;
		UserKernel.pagesLock.release();
		return;
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if(PID != 0) {
			return -1;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");

		for(int fp = 2; fp < fileTable.length; fp++) {
			if(fileTable[fp] != null) {
				handleClose(fp);
			}
		}
		unloadSections();
		coff.close();

		for(Integer _pid: runningChilds.keySet()) {
			runningChilds.get(_pid).par = null;
		}

		if(par != null) {
			par.runningChilds.remove(PID);
			if(!abnormalTermination) {
				par.childStatuses.put(PID,status);
			}
			lock.acquire();
			cond.wake();
			lock.release();
		}

		// for now, unconditionally terminate with just one process
		UserKernel.PIDLock.acquire();
		if(UserKernel.totalProcesses == 1){
			UserKernel.totalProcesses--;
			UserKernel.PIDLock.release();
			Kernel.kernel.terminate();
			
		}
		else{
			UserKernel.totalProcesses--;
			UserKernel.PIDLock.release();
		}
		KThread.currentThread().finish();
		//Kernel.kernel.terminate();

		return 0;
	}

	private int handleExec(int memAddress, int argc, int argv) {
		if(argc < 0)
			return -1;
		String fileName = readVirtualMemoryString(memAddress, maxParamLen);
		if (fileName == null || fileName.length() == 0 || !fileName.endsWith(".coff")) {
			return -1;
		}
		String []args = new String[argc];
		byte[] buffer = new byte[4];
		for(int i = 0; i < argc; i++){  
            int readCount = readVirtualMemory(argv + i * 4, buffer);
			if(readCount < 4) {
				return -1;
			}
            int vaddr = Lib.bytesToInt(buffer, 0);
            args[i] = readVirtualMemoryString(vaddr, maxParamLen);
            if(args[i] == null){
              return -1;
            }
        }

        UserProcess childProcess = newUserProcess();
        boolean execStatus = childProcess.execute(fileName, args);
     

        if(execStatus){
        	childProcess.par = this;
            runningChilds.put(childProcess.PID, childProcess);
            childPID.add(childProcess.PID);
            return childProcess.PID;
        }
        else{
            UserKernel.PIDLock.acquire();
            UserKernel.totalProcesses--;
            UserKernel.PIDLock.release();
            return -1;
          }
	}

	private int handleJoin(int PID, int status) {
		if(childPID.contains(PID) == false) {
			return -1;
		}
		if(runningChilds.containsKey(PID) == false) {
			childPID.remove(PID);
			if(childStatuses.containsKey(PID) == false) {
				return 0;
			}
			else {
				int childStatus = childStatuses.get(PID);
             	byte[] buffer = Lib.bytesFromInt(childStatus);
              	writeVirtualMemory(status, buffer);
				return 1;
			}
		}

		runningChilds.get(PID).lock.acquire();
		UserProcess _child = runningChilds.get(PID);
		runningChilds.get(PID).cond.sleep();
		_child.lock.release();

		childPID.remove(PID);
		if(childStatuses.containsKey(PID) == false){ 
			return 0;
		}
		else {
			int childStatus = childStatuses.get(PID);
		  	byte[] buffer = Lib.bytesFromInt(childStatus);
		  	writeVirtualMemory(status, buffer);
		  	return 1;
		} 

	}

	private int handleCreat(int memAddress) {
		if(memAddress < 0) {
			return -1;
		}
		String fileName = readVirtualMemoryString(memAddress, 256);
		if(fileName == null) {
			return -1;
		}
		int openIndex = -1;
		for(int i = 2; i < 16; i++) {
			if(fileTable[i] == null) {
				openIndex = i;
				break;
			}
		}
		if(openIndex == -1) {
			return -1;
		}
		OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
		if(file == null) {
			return -1;
		}
		else {
			fileTable[openIndex] = file;
			return openIndex;
		}
	 }

	 private int handleOpen(int memAddress) {
		if(memAddress < 0) {
			return -1;
		}
		String fileName = readVirtualMemoryString(memAddress, 256);
		if(fileName == null) {
			return -1;
		}
		int openIndex = -1;
		for(int i = 2; i < 16; i++) {
			if(fileTable[i] == null) {
				openIndex = i;
				break;
			}
		}
		if(openIndex == -1) {
			return -1;
		}
		OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
		if(file == null) {
			return -1;
		}
		else {
			fileTable[openIndex] = file;
			return openIndex;
		}
	 }

	 private int handleRead(int index, int buffer, int count) {
		if(index < 0 || index >= 16 ||fileTable[index] == null || buffer < 0 || count < 0) {
			return -1;
		}
		OpenFile file = fileTable[index];
		byte[] readinbuff = new byte[pageSize];
		int bytesReadTotal = 0;
		int justReadBytes = 0;
		int unreadBytes = count;
		while(unreadBytes > pageSize) {
			justReadBytes = file.read(readinbuff, 0, pageSize);
			if(justReadBytes == -1) {
				return -1;
			}
			if(justReadBytes == 0) {
				return bytesReadTotal;
			}
			int helper = writeVirtualMemory(buffer, readinbuff, 0, justReadBytes);
			if(helper != justReadBytes) {
				return -1;
			}
			buffer = buffer + helper;
			bytesReadTotal = bytesReadTotal + helper;
			unreadBytes = unreadBytes - helper;
		}
		justReadBytes = file.read(readinbuff, 0, unreadBytes);
		if(justReadBytes == -1) {
			return -1;
		}
		int helper = writeVirtualMemory(buffer, readinbuff, 0, justReadBytes);

		if(justReadBytes != helper) {
			return -1;
		}
		bytesReadTotal = bytesReadTotal + helper;
		return bytesReadTotal;
	 }

	private int handleWrite(int index, int buffer, int count) {
		if(index < 1 || index >= 16 ||fileTable[index] == null || buffer < 0 || count < 0) {
			return -1;
		}
		OpenFile file = fileTable[index];
		byte[] readinbuff = new byte[pageSize];
		int bytesWrittenTotal = 0;
		int justWrittenBytes = 0;
		int unwrittenBytes = count;
		while(unwrittenBytes > pageSize) {
			justWrittenBytes = readVirtualMemory(buffer, readinbuff, 0, pageSize);
			if(justWrittenBytes < pageSize) {
				return -1;
			}
			int helper = file.write(readinbuff, 0, justWrittenBytes);
			if(justWrittenBytes != helper) {
				System.out.println("bytes unequal write in loop");
				return -1;
			}
			if(helper == -1) {
				System.out.println("helper -1 in loop");
				return -1;
			}
			if(helper == 0) {
				return bytesWrittenTotal;
			}
			buffer = buffer + helper;
			bytesWrittenTotal = bytesWrittenTotal + helper;
			unwrittenBytes = unwrittenBytes - helper;
		}
		justWrittenBytes = readVirtualMemory(buffer, readinbuff, 0, unwrittenBytes);
		int helper = file.write(readinbuff, 0, unwrittenBytes);
		if(helper == -1) {
			System.out.println("helper -1 outside loop");
			return -1;
		}
		if(helper != justWrittenBytes) {
			System.out.println("bytes unequal write outside loop");
			return -1;
		}

		bytesWrittenTotal = bytesWrittenTotal + helper;

		return bytesWrittenTotal;

		/*OpenFile file = fileTable[index];
		int bytesRead = -1;
		int bytesWritten = -1;
		byte[] readin = new byte[count];
		bytesRead = readVirtualMemory(buffer, readin, 0, count);
		if(bytesRead != count) {
			return -1;
		}
		bytesWritten = file.write(readin, 0, bytesRead);
		if(bytesWritten != bytesRead) {
			return -1;
		}
		return bytesWritten;
		*/
	}

	private int handleClose(int index) {
		if(index < 0 || index >= 16 || fileTable[index] == null) {
			return -1;
		}
		OpenFile file = fileTable[index];
		file.close();
		fileTable[index] = null;
		return 0;
	}

	private int handleUnlink(int memAddress) {
		if(memAddress < 0 ) {
			return -1;
		}
		String fileName = readVirtualMemoryString(memAddress, 256);
		if(fileName == null) {
			return -1;
		}
		if(ThreadedKernel.fileSystem.remove(fileName) == true) {
			return 0;
		}
		else {
			return -1;
		}

	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallCreate:
				return handleCreat(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			abnormalTermination = true;
			handleExit(-1);
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
    protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private LinkedList<Integer> pagesUsed;

	private int PID;
	private UserProcess par; 

	private OpenFile[] fileTable;
	private HashMap<Integer, UserProcess> runningChilds;
	private HashMap <Integer, Integer> childStatuses; 
	private HashSet <Integer> childPID;
	private int maxParamLen = 256;

	public Condition cond;
	public Lock lock;

	public boolean abnormalTermination;
}
