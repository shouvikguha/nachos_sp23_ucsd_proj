package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		//return super.loadSections();
		pageTable = new TranslationEntry[numPages];
		for(int i = 0; i < numPages; i++) {
			//int getFreePhysicalPage = UserKernel.freePages.pollFirst();
			// no need to give a physical page
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		VMKernel.VMLock.acquire();
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= pageSize * numPages || vaddr >= memory.length ) {
			VMKernel.VMLock.release();
			return 0;
		}
		int vpn = Processor.pageFromAddress(vaddr);
		if(vpn >= pageTable.length || vpn < 0){
			VMKernel.VMLock.release();
			return 0;
		}
		//if(pageTable == null) return 0;

		int amount = Math.min(length, memory.length - vaddr);
		int readAmount = 0, base_vaddr = vaddr;
		while(base_vaddr < vaddr + length) {
			if(base_vaddr >= numPages * pageSize || base_vaddr >= memory.length)
				break;
			vpn = Processor.pageFromAddress(base_vaddr);

			if(vpn >= pageTable.length || vpn < 0){
				VMKernel.VMLock.release();
				return readAmount;
			}

			int baseaddr_offset = Processor.offsetFromAddress(base_vaddr);
			//int ppn = pageTable[vpn].ppn;
			//if(pageTable[vpn].valid == false) break;
			if(pageTable[vpn].valid == true) {
				VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = true;
				VMKernel.totalpins++;
				pageTable[vpn].used = true;
			}
			else {
				int get_vaddr = Processor.makeAddress(vpn, 0);
				HandlePageFault(get_vaddr);
				if(pageTable[vpn].valid){  
					VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = true;
					VMKernel.totalpins++;
					pageTable[vpn].used = true;
				  }
				  else{
					VMKernel.VMLock.release();
					return readAmount;
				  }
			}
			int checkAddr = pageTable[vpn].ppn * pageSize + baseaddr_offset ;
			if( checkAddr< 0 || checkAddr >= memory.length){
				VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = false;
				VMKernel.totalpins--;
				VMKernel.CV.wake();
				VMKernel.VMLock.release();
				return readAmount;
			  }

			int nextbase_vaddr = (vpn + 1) * pageSize;
			if(nextbase_vaddr < vaddr + length && nextbase_vaddr < numPages*pageSize) {
				amount = nextbase_vaddr   - base_vaddr;
			}
			else {
				amount = Math.min(vaddr + length, numPages * pageSize) - base_vaddr;
			}

			int memp_addr = pageTable[vpn].ppn * pageSize + baseaddr_offset;
			System.arraycopy(memory, memp_addr, data, offset, amount);
			VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = false;
			VMKernel.totalpins--;
			VMKernel.CV.wake();
			base_vaddr = nextbase_vaddr;
			offset += amount;
			readAmount += amount;
		}
		VMKernel.VMLock.release();
		return readAmount;
	}


	//write VM
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		VMKernel.VMLock.acquire();
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= pageSize * numPages || vaddr >= memory.length ) {
			VMKernel.VMLock.release();
			return 0;
		}
		int vpn = Processor.pageFromAddress(vaddr);
		if(vpn >= pageTable.length || vpn < 0){
			VMKernel.VMLock.release();
			return 0;
		}

		int amount = Math.min(length, memory.length - vaddr);
		int writeAmount = 0, base_vaddr = vaddr;
		while(base_vaddr < vaddr + length) {
			if(base_vaddr >= numPages * pageSize || base_vaddr >= memory.length)
				break;

			vpn = Processor.pageFromAddress(base_vaddr);

			if(vpn >= pageTable.length || vpn < 0){
				VMKernel.VMLock.release();
				return writeAmount;
			}

			int baseaddr_offset = Processor.offsetFromAddress(base_vaddr);
			//int ppn = pageTable[vpn].ppn;
			int nextbase_vaddr = (vpn + 1) * pageSize;
			//if(pageTable[vpn].valid == false || pageTable[vpn].readOnly) break;

			if(pageTable[vpn].valid == true) {
				if(pageTable[vpn].readOnly == false){
					VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = true;
					VMKernel.totalpins++;
					pageTable[vpn].used = true; 
				  }
				  else{
					VMKernel.VMLock.release();
					return writeAmount;
				  }
			}
			else {
				int get_vaddr =  Processor.makeAddress(vpn, 0);
				HandlePageFault(get_vaddr);
				if(pageTable[vpn].valid){
					if(pageTable[vpn].readOnly == false){
					  VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = true;
					  VMKernel.totalpins++;
					  pageTable[vpn].used = true; 
					}
					else{
					  VMKernel.VMLock.release();
					  return writeAmount;
					}
				  }
				  else{
					VMKernel.VMLock.release();
					return writeAmount;
				  }
			}
			int memp_addr =  pageTable[vpn].ppn * pageSize + baseaddr_offset;
			if(memp_addr < 0 || memp_addr >= memory.length) {
				VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = false;
				VMKernel.totalpins--;
				VMKernel.CV.wake();
				VMKernel.VMLock.release();
				return writeAmount;
			}

			if(nextbase_vaddr < (vaddr + length) && nextbase_vaddr < numPages*pageSize) {
				amount = nextbase_vaddr  - base_vaddr;
			}
			else {
				amount = Math.min(vaddr + length, numPages * pageSize) - base_vaddr;
			}

			System.arraycopy(data, offset, memory, memp_addr, amount);
			if(amount > 0) {
				pageTable[vpn].dirty = true;
			}
			VMKernel.InvertedPageTable[pageTable[vpn].ppn].isPinned = false;
            VMKernel.totalpins--;
            VMKernel.CV.wake();
			base_vaddr = nextbase_vaddr;
			offset += amount;
			writeAmount += amount;
		}
		VMKernel.VMLock.release();
		return writeAmount;
	}

	public int LoadSection() {
		int ppn = 0;
		int numPhysicalPages = Machine.processor().getNumPhysPages();
		if(UserKernel.freePages.isEmpty()){
			while(true) {
				if(VMKernel.InvertedPageTable[VMKernel.toEvict].isPinned == true){
					if(VMKernel.totalpins == numPhysicalPages){
						VMKernel.CV.sleep();
					}
					VMKernel.toEvict = VMKernel.toEvict++;
					if(VMKernel.toEvict  >= numPhysicalPages)
						VMKernel.toEvict -= numPhysicalPages;
					continue;
				}
				if(VMKernel.InvertedPageTable[VMKernel.toEvict].entry.used == false){
					break;
				}
				VMKernel.InvertedPageTable[VMKernel.toEvict].entry.used = false;
				VMKernel.toEvict = VMKernel.toEvict++;
				if(VMKernel.toEvict  >= numPhysicalPages)
					VMKernel.toEvict -= numPhysicalPages;
			}
			int toEvict = VMKernel.toEvict;
			VMKernel.toEvict++;
			if(VMKernel.toEvict  >= numPhysicalPages)
				VMKernel.toEvict -= numPhysicalPages;

			if(VMKernel.InvertedPageTable[toEvict].entry.dirty){
				int swapPN = 0;
				if(!VMKernel.freeSwapPages.isEmpty()){
					swapPN = VMKernel.freeSwapPages.removeLast();
				}
				else{
					swapPN = VMKernel.currSwapPointer;
					VMKernel.currSwapPointer++;
				}
				VMKernel.swapFile.write(swapPN * Processor.pageSize, Machine.processor().getMemory(), Processor.makeAddress(VMKernel.InvertedPageTable[toEvict].entry.ppn, 0), Processor.pageSize);
				
				VMKernel.InvertedPageTable[toEvict].entry.vpn = swapPN;
			}
			int ev = VMKernel.InvertedPageTable[toEvict].entry.ppn;
			VMKernel.InvertedPageTable[toEvict].process.pageTable[ev].valid = false;
			VMKernel.InvertedPageTable[toEvict].entry.valid = false;
			ppn = VMKernel.InvertedPageTable[toEvict].entry.ppn;
		}
		else {				// if there are free pages
			ppn = UserKernel.freePages.removeLast();
		}
		return ppn;
	}
	public void HandlePageFault(int BadVAddr) {
		UserKernel.pagesLock.acquire();
		int numPhysicalPages = Machine.processor().getNumPhysPages();
		int getPageVPN = Processor.pageFromAddress(BadVAddr);
		int startVPN = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				startVPN = vpn;
				if(vpn != getPageVPN) continue;
				//if there is no free page
				int ppn = LoadSection();
				if(!pageTable[vpn].dirty){
					section.loadPage(i, ppn); 
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), true, false);
				}
				else {
					int _ppn = pageTable[vpn].vpn;
					VMKernel.swapFile.read(_ppn * Processor.pageSize, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), Processor.pageSize);
                    VMKernel.freeSwapPages.add(_ppn);
                	pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, true);
				}
				VMKernel.InvertedPageTable[ppn].entry = pageTable[vpn];
				VMKernel.InvertedPageTable[ppn].process = this;     
			}              
		}
		for(int i = startVPN + 1; i < numPages; i++) {
			int vpn = i;
			if(vpn != getPageVPN) continue;
			int ppn = LoadSection();
			if(!pageTable[vpn].dirty){
				byte[] data = new byte[Processor.pageSize];
				System.arraycopy(data, 0, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), Processor.pageSize);
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, false);
			  }
			  else{
				VMKernel.swapFile.read(pageTable[vpn].vpn * Processor.pageSize, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), Processor.pageSize);
				VMKernel.freeSwapPages.add(pageTable[vpn].vpn);
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, true);
			  }
			  VMKernel.InvertedPageTable[ppn].process = this;
			  VMKernel.InvertedPageTable[ppn].entry = pageTable[vpn];	
		}
		UserKernel.pagesLock.release();
		return;
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
		case Processor.exceptionPageFault:
			int ex = processor.readRegister(Processor.regBadVAddr);
			HandlePageFault(ex);
			break;
		default:
			super.handleException(cause);
			break;
		}
	}


	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';


}