package tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Emulator {

	static final int PC = 0;
	static final int IR = 1;
	static final int SP = 2;
	static final int Mem = 3;
	static final int A = 4;
	static final int B = 5;
	static final int C = 6;
	static final int D = 7;
	
	static int[] registers = new int[8];
	static int[] ram = new int[65536];
	
	static long t1;
	static long cycles = 0;
	static boolean faultflag = false;
	
	static final int USERBASE = 0X4000;
	static final int ROMBASE = 0X0000;
	static final int IOBASE = 0X2000;
	static final int KEYBOARD = IOBASE;
	static final int SCREEN = KEYBOARD + 1;
	
	//io
	static String in = "a.out";
	static String rom = "rom.out";

	public static void main(String[] args) throws FileNotFoundException {
		//shadows are for debugging
		int[] shadow = registers;
		int[] ramshadow = ram;
		//create a virtual cpu
		Emulator self = new Emulator();
		//boot it up
		self.init();

		//silly useless performance monitoring
		t1 = System.currentTimeMillis();
		//run until fault
		while(faultflag != true){
			//process a cycle
			self.cycle();
		}
		

	}
	
	/**
	 * Performs a single Fetch, Increment, Decode/Execute cycle on the virtual cpu instance
	 * <BR><BR>
	 * This modifies the registers and memory of the processor as required by the instructions
	 */
	public void cycle(){
		registers[IR] = ram[registers[PC]];
		
		registers[PC] += 1;
		execute(registers[IR]);
		cycles++;
		//in.nextLine();
	}
	
	/**
	 * Boots the virtual processor.
	 * <BR><BR>
	 * Sets up the processor function vector and fills ram from a.out
	 */
	public void init(){
		//fill our function vector with the correct functions
		//wooh extensibility!
		functions[0] = new zero();
		functions[1] = new one();
		functions[2] = new negone();
		functions[3] = new mv();
		functions[4] = new not();
		functions[5] = new negate();
		functions[6] = new inc();
		functions[7] = new dec();
		functions[8] = new add();
		functions[9] = new sub();
		functions[10] = new and();
		functions[11] = new or();
		functions[12] = new load();
		functions[13] = new store();
		functions[14] = new breq();
		functions[15] = new brlt();
		functions[16] = new brlteq();
		functions[17] = new jmp();
		functions[18] = new fault();
		functions[19] = new shiftright();
		
		
		Scanner src = null;
		try {
			src = new Scanner(new File(rom));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
		//ignore first line as it is the logisim specific header
		src.nextLine();
		int location = 0;
		
		//fill ram
		while(src.hasNextLine()){
			ram[location] = Integer.decode("0X" + src.nextLine());
			location++;
		}
		
		//repeat with user program
		try {
			src = new Scanner(new File(in));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		src.nextLine();
		location = USERBASE;
		while(src.hasNextLine()){
			ram[location] = Integer.decode("0X" + src.nextLine());
			location++;
		}
	}

	/**
	 * Decodes and performs the instruction pointed to by i.
	 * <BR><BR>
	 * This would not typically be called by an external function
	 * @param i the index in the function table of the instruction to perform
	 */
	public void execute(int i) {
		int instruction = (i & 15872) / 512;
		int setflag = (i & 32768) / 32768;
		int setdata = (i & 32767);
		int x = (i & 448) / 64;
		int y = (i & 56) / 8;
		int out = (i & 7);
		//System.out.println(registers[PC] + " - " + functions[instruction].getClass().getName().replaceFirst(".*\\$", "") + " x: " + x + " y: " + y + " out: " + out);
		
		//set is performed in a special hacky way
		if(setflag == 1){
			registers[Mem] = setdata;
			return;
		}
		
		functions[instruction].execute(x, y, out);

		
		
	}
	//function table
	static instruction[] functions = new instruction[20];

	
	//
	//Begin declaring fundamental instructions
	//see :http://stackoverflow.com/questions/2752192/array-of-function-pointers-in-java
	//
	//
	static class zero extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = 0;
		}
	}
	static class one extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = 1;
		}
	}
	static class negone extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = -1;
		}
	}
	static class mv extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x];
		}
	}
	static class not extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = ~registers[x] & 65535;
		}
	}
	static class negate extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = -registers[x];
		}
	}
	static class inc extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x] + 1;	
		}
	}
	static class dec extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x] - 1;	
		}
	}
	static class add extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x] + registers[y];
		}
	}
	static class sub extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x] - registers[y];
		}
	}
	static class and extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x] & registers[y];
		}
	}
	static class or extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x] | registers[y];
		}
	}
	static class load extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			if(registers[Mem] == SCREEN){
				registers[out] = 0;
			}
			
			registers[out] = ram[registers[Mem]];
			//clear if a keyboard read, used for the emulator. Actual implementation of the keyboard
			//works like a clocked buffer, emitting a single character when clocked
			if(registers[Mem] == KEYBOARD){
				ram[registers[Mem]] = 0;
			}
			
			
				
		}
	}
	static class store extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			if(registers[Mem] == KEYBOARD){
				//do nothing
				return;
			}
			
			//Terminal system is responsible for accepting characters and managing its buffer
			//a higher memory location could be used to signal ready state
			ram[registers[Mem]] = registers[x];	
		}
	}
	static class breq extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			if(registers[x] == registers[y]){
				registers[PC] = registers[Mem];
			}
		}
	}
	static class brlt extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			if(registers[x] < registers[y]){
				registers[PC] = registers[Mem];
			}
		}
	}
	static class brlteq extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			if(registers[x] <= registers[y]){
				registers[PC] = registers[Mem];
			}
		}
	}
	static class jmp extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[PC] = registers[Mem];	
		}
	}
	static class shiftright extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			registers[out] = registers[x] >>> 1;	
		}
	}
	/**
	 * Special instruction used to signal end of operation. Stops the cpu dead
	 * @author dboucher
	 *
	 */
	static class fault extends instruction{
		@Override
		public void execute(int x, int y, int out) {
			System.out.println("Fault");
			//System.out.println(ram[3]);
			long t2 = System.currentTimeMillis();
			System.out.println(t2-t1);
			System.out.println(cycles + " cycles");
			
			for(int i = 0; i < registers.length; i++){
				System.out.print(String.format("%04X ", registers[i]));
			}
			int oldmem1 = 0;
			int oldmem2 = 0;
			System.out.println("\n");
			for(int i = 0; i < ram.length; i += 4){
				String outdata = String.format("%04X:  %04X %04X %04X %04X", i, ram[i], ram[i+1], ram[i+2], ram[i+3]);
				System.out.println(outdata);
				int mem = ram[i] + ram[i+1] + ram[i+2] + ram[i+3];
				if((mem + oldmem1 + oldmem2) == 0){
					break;
				}
				oldmem2 = oldmem1;
				oldmem1 = mem;
			}
			
			
			faultflag = true;
		}
	}
	//end defining functions
	
	
}

//abstract functor class that is used by every fundemental instruction
abstract class instruction{
	public abstract void execute(int x, int y, int out);
}
