package tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.TreeMap;

public class Assembler {
	//No operands
	static String Special[] = {"Jmp", "Fault"};
	static int Specialval[] = {8704, 9216};
	//out
	static String Result[] = {"Zero", "One", "NegOne", "Load"};
	static int Resultval[] = {0, 512, 1024, 6144};
	//one operand and out
	static String Unary[] = {"Mv", "BitFlip", "Neg", "Inc", "Dec", "ShR"};
	static int Unaryval[] = {1536, 2048, 2048, 3072, 3584, 9728};
	//two operands
	static String Flow[] = {"Br=", "Br<", "Br<="};
	static int Flowval[] = {7168, 7680, 8192};
	//two operands and out
	static String Full[] = {"Add", "Sub", "And", "Or"};
	static int Fullval[] = {4096, 4608, 5120, 5632};
	//special cases
	//store x
	//set [value]

	//registers
	static String Registers[] = {"PC", "IR", "SP", "Mem", "A", "B", "C", "D"};
	static int Registersval[] = {0, 1, 2, 3, 4, 5, 6, 7};

	//io
	static final String  in = "src/assemblies/test.jef";
	static final String romfile = "src/assemblies/rom.jef";
	static final String outfile = "a.out";
	/**
	 * The location in memory where "Userland" starts
	 */
	static final int USERBASE = 0X4000;

	public static TreeMap<String, Integer> symboltable = new TreeMap<String, Integer>();
	public static void main(String[] args) throws IOException {
		Scanner infile = new Scanner(new File(in));
		FileWriter out = new FileWriter(new File(outfile));
		
		preprocess(new Scanner(new File(romfile)), 0);
		
		String finalinfile = "";
		
		if(args != null && args.length > 0 && args[0].equalsIgnoreCase("rom")){
			System.out.println("romburn " + in);
			finalinfile = romfile;
		}else{
			System.out.println("userprgm " + in);
			finalinfile = in;
			preprocess(infile, USERBASE);
		}

		infile = new Scanner(new File(finalinfile));
		
		//extract the text to assemble
		String sourcetext = new String();
		while(infile.hasNextLine()){
			sourcetext += infile.nextLine() + "\n";
		}
		
		String assembly = new String();
		assembly = assemble(sourcetext, USERBASE);
		
		out.write(assembly);
		out.flush();
		out.close();
		infile.close();
		
	}
	
	/**
	 * Assembles a string of Jeffrey assembly into a binary, treating it as a ROM image
	 * @param romsource text to assemble
	 * @return the assembled ROM binary
	 */
	public static String assembleROM(String romsource){
		preprocess(new Scanner(romsource), 0);
		
		String romimage = assemble(romsource, 0);
		
		return romimage;
	}
	
	/**
	 * Assembles a string of Jeffrey assembly into a binary
	 * @param romsource text to interpret as the ROM to link against
	 * @param usersource text to treat as the source code
	 * @return the assembled userland image binary
	 */
	public static String assembleUser(String romsource, String usersource){
		preprocess(new Scanner(romsource), 0);
		
		preprocess(new Scanner(usersource), USERBASE);
		
		String userimage = assemble(usersource, USERBASE);
		
		return userimage;
		
	}
	
	/**
	 * Assembles the source file into a Jeffrey compatible binary
	 * @param source String containing the source text to be assembled
	 * @param offset int offset that the binary should start at ie 0X4000 for Userland and 0 for ROM
	 * @return a String representation of a Jeffrey RAM image
	 */
	private static String assemble(String source, int offset){

		Scanner infile = new Scanner(source);
		
		StringBuilder out = new StringBuilder();

		//this is the header defined in the logisim "ram format"
		//http://www.cburch.com/logisim/docs/2.7/en/html/guide/mem/menu.html
		//we do not take advantage of run length encoding at this time
		out.append("v2.0 raw\n");

		int line = 0;
		try{
			while(infile.hasNextLine()){
				Scanner lineparser = new Scanner(infile.nextLine());
				int instruction = 0;
				//skip empty lines
				if(!lineparser.hasNext()){
					line++;
					continue;
				}
				String temp = lineparser.next();
				//read past symbols
				//they were already parsed
				if(temp.charAt(0) == '#'){
					temp = lineparser.next();
				}

				//go through each instruction list until we find the one "temp" belongs to
				int location = contains(Special, temp);
				if(location >= 0){
					//no operands
					instruction += Specialval[location];
				}

				location = contains(Result, temp);
				if(location >= 0){
					//out
					instruction += Resultval[location];

					String reg = lineparser.next();
					instruction += Registersval[contains(Registers, reg)];
				}

				location = contains(Unary, temp);
				if(location >= 0){
					//one operand and out
					instruction += Unaryval[location];

					String operandreg = lineparser.next();
					instruction += 64 * Registersval[contains(Registers, operandreg)];

					//consume "to"
					lineparser.next();

					String outreg = lineparser.next();
					instruction += Registersval[contains(Registers, outreg)];
				}

				location = contains(Flow, temp);
				if(location >= 0){
					//two operands
					instruction += Flowval[location];

					String xreg = lineparser.next();
					instruction += 64 * Registersval[contains(Registers, xreg)];

					String yreg = lineparser.next();
					instruction += 8 * Registersval[contains(Registers, yreg)];

					instruction += 1;
				}

				location = contains(Full, temp);
				if(location >= 0){
					//two operands and out
					instruction += Fullval[location];

					String xreg = lineparser.next();
					instruction += 64 * Registersval[contains(Registers, xreg)];

					String yreg = lineparser.next();
					instruction += 8 * Registersval[contains(Registers, yreg)];

					//consume "to"
					lineparser.next();

					String outreg = lineparser.next();
					instruction += Registersval[contains(Registers, outreg)];
				}

				//set instruction is a special snowflake
				if(temp.equals("Set")){
					String val = lineparser.next();
					if(symboltable.containsKey(val.substring(1))){
						instruction = 32768 + symboltable.get(val.substring(1));
					}else{
						try{
							instruction = 32768 + Integer.parseInt(val);
						}catch (NumberFormatException e){
							System.err.println("Value " + val + " is not a defined symbol or a parsable number");
							System.exit(1);
						}
					}
				}

				//store also has to be handled specially, because of its unusual register encoding methods
				if(temp.equals("Store")){
					instruction = 6656;

					String srcreg = lineparser.next();
					instruction += 64 * Registersval[contains(Registers, srcreg)];

					instruction += Registersval[contains(Registers, srcreg)];
				}
				
				line++;
				
				//ignore comment lines
				if(temp.charAt(0) == '/'){
					continue;
				}
				//ignore empty lines
				if(temp.isEmpty()){
					continue;
				}
				//assembler pseudo-ops
				if(temp.charAt(0) == '.'){
					instruction = 0;
					//literal places the value into that cell in memory
					if(temp.substring(1).equals("Literal")){
						String val = lineparser.next();
						if(val.length() > 1 && val.charAt(1) == 'X'){
							instruction = (Integer.decode(val) & 0XFFFF);
						}else{
							instruction = (Integer.decode(val) & 0XFFFF);
						}

					}
					//define places a value into the symbol table
					if(temp.substring(1).equals("Define")){
						continue;
					}
				}


				//place our parsed instruction into the binary file in hex
				String outdata = String.format("%04X", instruction);
				out.append(outdata + "\n");
				lineparser.close();
			}
		}catch (ArrayIndexOutOfBoundsException e){
			System.err.println("Error at line: " + line + " - Instruction or register not correct");
			e.printStackTrace();
		}catch (NumberFormatException e){
			System.err.println("Error at line: " + line + " - Number format not recognized");
			e.printStackTrace();
		}

		symboltable.clear();
		infile.close();
		
		
		return out.toString();
	}
	
	
	
	
	/**
	 * Takes a first pass through the source file and makes note of references and other labels for later use
	 * @param infile Scanner containing the text to be processed
	 * @param offset int offset the file will start at. 0 for ROM code
	 */
	private static void preprocess(Scanner infile, int offset){
		//preprocess to fill symboltable
		int line = 0;
		while(infile.hasNextLine()){
			Scanner preprocessor = new Scanner(infile.nextLine());
			String symboltest = null;
			//skip empty lines
			if(preprocessor.hasNext()){
				symboltest = preprocessor.next();
			}else{
				continue;
			}
			//check for a symbol and add it if it is proper
			if(symboltest.charAt(0) == '#'){
				String key = symboltest.substring(1);
				if(symboltable.containsKey(key)){
					System.out.println("Error, Symbol: " + key + " near line: " + line + " already defined near line: " + (symboltable.get(key) - offset));
					System.exit(1);
				}else{
					symboltable.put(key, line + offset);
				}
			}
			//check for a constant definition and add it if it is proper
			if(symboltest.equals(".Define")){
				String name = preprocessor.next();
				int value = (Integer.decode(preprocessor.next()) & 0XFFFF);
				if(symboltable.containsKey(name)){
					System.out.println("Error, Constant: " + name  + " already defined at line: " + symboltable.get(name));
					System.exit(1);
				}else{
					symboltable.put(name, value);
					continue;
				}
			}
			if(symboltest.charAt(0) == '/'){
				//comments dont add to binary executable
				continue;
			}else{
				//otherwise we increment the value of "line" that we are working with
				//every symbol refers to the line it stands for
				line++;
			}
			preprocessor.close();
		}
		infile.close();
	}
	
	
	/**
	 * Checks if a string array contains a string and returns its position or -1 otherwise
	 * @param array the String array to check in
	 * @param val the string to look for
	 * @return the index of the string or -1 if not found
	 */
	private static int contains(String[] array, String val){
		if(val == null || array == null){
			return -1;
		}
		int i;
		for(i = 0; i <= array.length; i++){
			if(i == array.length){
				return -1;
			}
			if(val.equals(array[i])){
				break;
			}
		}
		return i;
	}

}
