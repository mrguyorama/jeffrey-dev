package tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Scanner;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public class IDE {

	private static JFrame window;

	private static JPanel controls;
	private static JPanel info;

	private static JButton run;
	private static JButton step;

	private static JTable assembly;

	private static JScrollPane scroll;

	private static JTextField errors;

	private static ArrayList<String> asm = new ArrayList<String>();
	private static ArrayList<String> bin = new ArrayList<String>();
	private static JPanel registers;
	private static JPanel internals;
	private static JPanel registersgen;
	private static JPanel registerssys;
	private static JTextField pcreg;
	private static JTextField irreg;
	private static JTextField spreg;
	private static JTextField memreg;
	private static JTextField areg;
	private static JTextField breg;
	private static JTextField creg;
	private static JTextField dreg;

	private static Emulator cpu;

	private static JTable memtable;
	private static JScrollPane memoryviewer;

	private static JButton reset;
	private static JButton pause;
	private static JTextField speed;

	private static int hz = 10;
	private static long t1 = 0;
	private static boolean stopflag = false;
	private static boolean isrunning = false;
	private static ArrayList<Integer> linenumber = new ArrayList<Integer>();

	private static JFrame terminal;

	private static JTextArea screen;

	private static JTextField keybuf;

	public static void main(String[] args) {
		//create and congifure the gui
		setupGUI();
		//creates an emulator, connects to the virtual cpu and boots it up
		init();

		//we are ready to go, show the world!
		window.setVisible(true);
		


		terminal.pack();
		terminal.setVisible(true);
	}


	/**
	 * sends a single cycle to the virtual cpu and updates the gui
	 */
	 @SuppressWarnings("static-access")
	 public static void tick(){
		 //dont do anything if the cpu has raised a fault
		 if(cpu.faultflag == true){
			 return;
		 }
		 //clear the "current instruction" pointer
		 if(cpu.registers[cpu.PC] >= cpu.USERBASE){
			 assembly.getModel().setValueAt("", linenumber.get(cpu.registers[cpu.PC] - cpu.USERBASE), 0);
		 }
		 cpu.cycle();
		 updateram(cpu.registers[cpu.Mem]);
		 updatescreen();


		 if(hz < 1000){
			 updateregisters();
		 }
		 //after last instruction, PC points off the end of the array, so don't try to write there
		 if(cpu.registers[cpu.PC] >= cpu.USERBASE){
			 try{
				 assembly.getModel().setValueAt("--->", linenumber.get(cpu.registers[cpu.PC] - cpu.USERBASE), 0);
			 }catch(ArrayIndexOutOfBoundsException e){
				 assembly.getModel().setValueAt("--->", linenumber.get(cpu.registers[cpu.PC] - 1 - cpu.USERBASE), 0);
			 } 
		 }


	 }

	 /**
	  * Continually cycles the virtual cpu and manages the running state. Internally calls tick()
	  * <BR><BR>
	  * This method is responsible for controlling the clock rate of the virtual cpu and 
	  * breaking on breakpoints
	  */
	 @SuppressWarnings("static-access")
	public static void run(){
		 //dont mess up if the clock is already cycling
		 if(isrunning){
			 return;
		 }
		 isrunning = true;
		 //another faultflag check
		 while(cpu.faultflag != true){
			 //used for the "stop" button
			 if(stopflag){
				 stopflag = false;
				 isrunning = false;
				 return;
			 }
			 tick();
			 //timing code to allow variable speed
			 if(hz < 1000){
				 try {
					 Thread.sleep((1000/hz));
				 } catch (InterruptedException e) {
					 e.printStackTrace();
				 }
			 }

			 long t2 = System.nanoTime();
			 long diff = t2 - t1;
			 double cpuhz = (1.0e9)/diff;
			 errors.setText("" + (diff) + "    " + cpuhz);
			 t1 = t2;
			 //stop executing if the current value of PC points to a breakpoint
			 if(cpu.registers[cpu.PC] >= cpu.USERBASE){
				 //this line is awful but neccessary because of the disconnect between program listing and actual location in memory
				 //this disconnect is caused by my seperation of user space and kernal space
				 //everything is offset by cpu.USERBASE and also indirected through linenumber
				 //because linenumber only stores executing code, ie code that cpu.PC will reach
				 if(assembly.getModel().getValueAt(linenumber.get(cpu.registers[cpu.PC] - cpu.USERBASE), 1) != null && assembly.getModel().getValueAt(linenumber.get(cpu.registers[cpu.PC] - cpu.USERBASE), 1).equals("*")){
					 isrunning = false;
					 break;
				 } 
			 }

		 }
		 updateregisters();
	 }

	 @SuppressWarnings("static-access")
	private static void updatescreen() {
		 if(cpu.ram[cpu.SCREEN] == 0){
			 //do nothing, nothing written
			 return;
		 }
		 char c = (char) cpu.ram[cpu.SCREEN];
		 String text = screen.getText();
		 if(c == 0X0008 && text.length() > 0){
			 text = text.substring(0, text.length() - 1);
		 }else if(c == 0X000A){
			 text += '\n';
		 }else{
			 text += c;
		 }

		 screen.setText(text);
		 screen.setCaretPosition(text.length());
		 if(!keybuf.getText().isEmpty()){
			 keybuf.transferFocus();
			 keybuf.grabFocus();
		 }
		 cpu.ram[cpu.SCREEN] = 0;

	 }


	 /**
	  * Reboots the virtual cpu, clearing its internal state and reinitializing ram
	  */
	 @SuppressWarnings("static-access")
	 public static void reset(){
		 stopflag = true;
		 isrunning = false;
		 cpu.registers[cpu.PC] = 0;
		 cpu.registers[cpu.IR] = 0;
		 cpu.registers[cpu.SP] = 0;
		 cpu.registers[cpu.Mem]= 0;
		 cpu.registers[cpu.A] = 0;
		 cpu.registers[cpu.B] = 0;
		 cpu.registers[cpu.C] = 0;
		 cpu.registers[cpu.D] = 0;
		 cpu.faultflag = false;
		 updateregisters();
		 cpu.init();
		 loadram();

		 //a reboot scales with the amount of code loaded. This could be removed to speed up the
		 //reset and allow the breakpoints to carry over
		 //its really just gui sugar and locks the gui during execution
		 for(int i = 0; i < assembly.getModel().getRowCount(); i++){
			 assembly.getModel().setValueAt("", i, 0);
			 assembly.getModel().setValueAt("", i, 1);
		 }
		 stopflag = false;
	 }


	 /**
	  * Designs the GUI, setting up asthetics and is responsible for laying everything out and 
	  * setting up actionlisteners
	  */
	 private static void setupGUI() {
		 //set look and feel
		 try {
			 UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		 } catch (Exception e) {
			 e.printStackTrace();
		 }
		 //init window
		 window = new JFrame("JefferyDebug");
		 window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		 window.getContentPane().setLayout(new BorderLayout());
		 window.getContentPane().setPreferredSize(new Dimension(1024, 768));

		 terminal = new JFrame("Terminal");
		 terminal.getContentPane().setLayout(new BorderLayout());
		 terminal.getContentPane().setPreferredSize(new Dimension(320,300));

		 screen = new JTextArea();
		 screen.setBackground(Color.black);
		 screen.setForeground(Color.green);
		 screen.setCaretColor(Color.GREEN);
		 screen.setEditable(false);

		 terminal.add(new JScrollPane(screen), BorderLayout.CENTER);

		 keybuf = new JTextField();
		 keybuf.setColumns(34);
		 keybuf.addFocusListener(new FocusAdapter(){
			 public void focusLost(FocusEvent e){
				 if(cpu.ram[cpu.KEYBOARD] != 0){
					 //dont do anything if cpu hasnt read buffered character
					 return;
				 }
				 //else buffer is available for input
				 String text = keybuf.getText();
				 if(text.isEmpty()){
					 return;
				 }

				 int c = text.charAt(0);
				 text = text.substring(1);
				 keybuf.setText(text);
				 c = c & 0X7FFF;

				 cpu.ram[cpu.KEYBOARD] = c;
				 updateram(cpu.KEYBOARD);
			 }
		 });

		 keybuf.addActionListener(new ActionListener(){
			 public void actionPerformed(ActionEvent e){
				 if(cpu.ram[cpu.KEYBOARD] == 0){
					 cpu.ram[cpu.KEYBOARD] = 0X000A;
				 }
			 }
		 });
		 keybuf.addKeyListener(new KeyListener(){
			 @Override
			 public void keyPressed(KeyEvent key) {
				 if(key.getKeyCode() == 8){
					 if(cpu.ram[cpu.KEYBOARD] == 0){
						 cpu.ram[cpu.KEYBOARD] = 8;
					 }
				 }
			 }
			 @Override
			 public void keyReleased(KeyEvent arg0) {
			 }
			 @Override
			 public void keyTyped(KeyEvent arg0) {	
			 }
		 });

		 keybuf.getDocument().addDocumentListener(new DocumentListener(){
			 @Override
			 public void changedUpdate(DocumentEvent arg0) {
				 keybuf.transferFocus();
				 keybuf.grabFocus();
			 }
			 @Override
			 public void insertUpdate(DocumentEvent arg0) {
				 changedUpdate(arg0);
			 }
			 @Override
			 public void removeUpdate(DocumentEvent arg0) {
				 changedUpdate(arg0);
			 }
		 });

		 terminal.add(keybuf, BorderLayout.PAGE_END);

		 //controls
		 controls = new JPanel();
		 run = new JButton("Run >");
		 step = new JButton("Step |>");
		 reset = new JButton("Reset");
		 pause = new JButton("Pause ||");
		 speed = new JTextField("10");

		 step.addActionListener(new ActionListener() {
			 public void actionPerformed(ActionEvent e) {
				 tick();
				 updateregisters();
			 }
		 });


		 run.addActionListener(new ActionListener() {
			 public void actionPerformed(ActionEvent e){
				 //run the "run" method in the background so it doesn't hang the dispatch thread
				 SwingWorker runner = new SwingWorker(){
					 @Override
					 protected Object doInBackground() throws Exception {
						 IDE.run();
						 return null;
					 }
				 };
				 runner.execute();
			 }

		 });

		 pause.addActionListener(new ActionListener() {
			 public void actionPerformed(ActionEvent e){
				 if(isrunning == false){
					 return;
				 }
				 stopflag = true;
				 updateregisters();
				 run.setText("Resume >");
			 }
		 });

		 reset.addActionListener(new ActionListener() {
			 public void actionPerformed(ActionEvent e){
				 reset();
			 }
		 });

		 speed.addFocusListener(new FocusAdapter() {
			 public void focusLost(FocusEvent e){
				 int value = 0;
				 //parse speed value, dont trust the user, ever
				 try{
					 value = Integer.parseInt(speed.getText());
				 }catch(Exception error){
					 speed.setText(hz + "");
					 return;
				 }
				 if(value <= 0){
					 value = 1;
				 }
				 hz = value;
				 speed.setText(hz + "");

			 }
		 });
		 speed.addActionListener(new ActionListener() {
			 @Override
			 public void actionPerformed(ActionEvent arg0) {
				 //hack to call the focus event handler because im too lazy to turn it into a method to call
				 speed.setFocusable(false);
				 speed.setFocusable(true);
			 }
		 });
		 speed.setColumns(4);

		 controls.add(run);
		 controls.add(pause);
		 controls.add(step);
		 controls.add(reset);
		 controls.add(new JLabel("Speed:"));
		 controls.add(speed);
		 controls.add(new JLabel("Hz"));


		 //assembly
		 assembly = new JTable();
		 assembly.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		 assembly.setFillsViewportHeight(true);
		 assembly.setPreferredSize(new Dimension(400,400));

		 //registers and memory
		 internals = new JPanel(new BorderLayout());
		 registers = new JPanel(new GridLayout(2,1));

		 //system registers
		 registerssys = new JPanel();
		 registerssys.add(new JLabel("PC"));
		 pcreg = new JTextField("0000");
		 registerssys.add(pcreg);

		 registerssys.add(new JLabel("IR"));
		 irreg = new JTextField("0000");
		 registerssys.add(irreg);

		 registerssys.add(new JLabel("SP"));
		 spreg = new JTextField("0000");
		 registerssys.add(spreg);

		 registerssys.add(new JLabel("Mem"));
		 memreg = new JTextField("0000");
		 registerssys.add(memreg);

		 registers.add(registerssys);

		 //general registers
		 registersgen = new JPanel();
		 registersgen.add(new JLabel("A"));
		 areg = new JTextField("0000");
		 registersgen.add(areg);

		 registersgen.add(new JLabel("B"));
		 breg = new JTextField("0000");
		 registersgen.add(breg);

		 registersgen.add(new JLabel("C"));
		 creg = new JTextField("0000");
		 registersgen.add(creg);

		 registersgen.add(new JLabel("D"));
		 dreg = new JTextField("0000");
		 registersgen.add(dreg);

		 registers.add(registersgen);

		 internals.add(registers, BorderLayout.PAGE_START);


		 //info
		 info = new JPanel();
		 errors = new JTextField();
		 errors.setEditable(false);
		 errors.setColumns(100);
		 info.add(errors);




		 //add three content panes
		 window.getContentPane().add(controls, BorderLayout.PAGE_START);
		 window.getContentPane().add(internals, BorderLayout.CENTER);
		 window.getContentPane().add(info, BorderLayout.PAGE_END);

		 //final setup
		 window.pack();

	 }

	 /**
	  * Fills the code table, creates and boots up a virtual cpu, and populates the memory table, 
	  * adding them to the view.
	  */
	 @SuppressWarnings("static-access")
	 private static void init() {
		 //Assemble the current rom
		 try{
			 String[] temp = new String[1];
			 temp[0] = "rom";
			 Assembler.main(temp);
			 //delete old file
			 File oldromfile = new File("rom.out");
			 if(oldromfile.exists()){
				 Boolean diddelete = oldromfile.delete();
				 if(diddelete == false){
					 System.err.println("Couldn't delete old romfile");
				 }
			 }
		 }catch (IOException e){
			 System.err.println("Failed to find rom file");
			 e.printStackTrace();
			 System.exit(1);
		 }
		 //rename output file
		 File romfile = new File(Assembler.outfile);
		 String romname = romfile.getAbsolutePath().replaceAll("a.out", "rom.out");
		 File newromfile = new File(romname);
		 Boolean didrename = romfile.renameTo(newromfile);
		 
		 if(didrename == false){
			 System.err.println("Couldn't rename romfile to " + romname);
			 System.exit(1);
		 }
		 
		 //call the assembler to run the file
		 try {
			 Assembler.main(null);
		 } catch (IOException e) {
			 e.printStackTrace();
			 System.exit(1);
		 }
		 Scanner readasm = null;
		 Scanner readbin = null;
		 try {
			 readasm = new Scanner(new File(Assembler.in));
			 readbin = new Scanner(new File(Emulator.in));
			 //consume header
			 readbin.nextLine();
		 } catch (FileNotFoundException e) {
			 e.printStackTrace();
			 System.exit(1);
		 }

		 int tableindex = -1;
		 //read in every line of the asm, and plop it into the arraylist for later
		 while(readasm.hasNextLine()){
			 tableindex++;
			 String line = readasm.nextLine();
			 asm.add(line);
			 String token = null;
			 //catch empty lines
			 try{
				 token = new Scanner(line).next();
			 }catch(NoSuchElementException e){
				 bin.add("");
				 continue;
			 }
			 //comments arent added to binary file so skip them
			 if(token.charAt(0) == '/' || token.equals(".Define")){
				 bin.add("");
			 }else{
				 bin.add(readbin.nextLine());
				 linenumber.add(tableindex);
			 }
		 }

		 //setup the code table in the ugliest way possible
		 String[] columns = {" " ,"*", "Line", "ASM", "BIN"};
		 assembly = new JTable(new table(columns, asm.size()));
		 assembly.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		 assembly.getColumnModel().getColumn(0).setPreferredWidth(8);
		 assembly.getColumnModel().getColumn(1).setPreferredWidth(8);
		 assembly.getColumnModel().getColumn(2).setPreferredWidth(48);
		 assembly.getColumnModel().getColumn(3).setPreferredWidth(200);
		 assembly.getColumnModel().getColumn(4).setPreferredWidth(24);

		 for(int i = 0; i < asm.size(); i++){
			 //fill it one row at a time like a scrub
			 assembly.getModel().setValueAt(i, i, 2);
			 assembly.getModel().setValueAt(asm.get(i), i, 3);
			 assembly.getModel().setValueAt(bin.get(i), i, 4);
		 }
		 //capture mouse clicks and route them to the breakpoint toggle method
		 assembly.addMouseListener(new MouseAdapter() {
			 public void mousePressed(MouseEvent e){
				 if(e.getClickCount() >= 2){
					 JTable target = (JTable)e.getSource();
					 int row = target.getSelectedRow();
					 int column = target.getSelectedColumn();

					 //System.out.println(row + "  " + column);
					 if(column == 1){
						 IDE.togglebreakpoint(row);
					 }
				 }

			 }
		 });

		 assembly.repaint();
		 scroll = new JScrollPane(assembly);
		 window.getContentPane().add(scroll, BorderLayout.WEST);

		 //instantiate an emulator object and paint its memory
		 cpu = new Emulator();
		 cpu.init();

		 String[] arr = {"Loc", "Val"};
		 memtable = new JTable(new table(arr, cpu.ram.length));
		 loadram();
		 updateregisters();
		 memtable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

		 memoryviewer = new JScrollPane(memtable);
		 internals.add(memoryviewer, BorderLayout.CENTER);

		 //finalize shape of GUI
		 window.pack();
		 window.setLocationRelativeTo(null);



	 }

	 /**
	  * Toggles a breakpoint in the code table
	  * @param row The row in the code table to set or unset a breakpoint
	  */
	 protected static void togglebreakpoint(int row) {
		 if(assembly.getModel().getValueAt(row, 1) == null || assembly.getModel().getValueAt(row, 1).equals("*")){
			 assembly.getModel().setValueAt("", row, 1);
		 }else{
			 assembly.getModel().setValueAt("*", row, 1);
		 }

	 }

	 /**
	  * Called every tick to keep the gui components in sync with the virtual cpu state
	  */
	 @SuppressWarnings("static-access")
	 private static void updateregisters() {
		 String regvalue = String.format("%04X", cpu.registers[cpu.PC]);
		 pcreg.setText(regvalue);
		 regvalue = String.format("%04X", cpu.registers[cpu.IR]);
		 irreg.setText(regvalue);
		 regvalue = String.format("%04X", cpu.registers[cpu.SP]);
		 spreg.setText(regvalue);
		 regvalue = String.format("%04X", cpu.registers[cpu.Mem]);
		 memreg.setText(regvalue);
		 regvalue = String.format("%04X", cpu.registers[cpu.A]);
		 areg.setText(regvalue);
		 regvalue = String.format("%04X", cpu.registers[cpu.B]);
		 breg.setText(regvalue);
		 regvalue = String.format("%04X", cpu.registers[cpu.C]);
		 creg.setText(regvalue);
		 regvalue = String.format("%04X", cpu.registers[cpu.D]);
		 dreg.setText(regvalue);

	 }


	 /**
	  * fills the ram table with the values from the virtual cpu's ram
	  */
	 @SuppressWarnings("static-access")
	 private static void loadram(){
		 for(int i = 0; i < cpu.ram.length; i++){
			 String loc = String.format("%04X", i);
			 memtable.getModel().setValueAt(loc, i, 0);
			 String val = String.format("%04X", cpu.ram[i]);
			 memtable.getModel().setValueAt(val, i, 1);
		 }
	 }

	 /**
	  * Updates the gui ramtable at the specified row
	  * @param i the row of the ramtable to update
	  */
	 @SuppressWarnings("static-access")
	 private static void updateram(int i){
		 String val = String.format("%04X", cpu.ram[i]);
		 memtable.getModel().setValueAt(val, i, 1);
	 }

	 /**
	  * Extends the default table model, allowing for easy JTable creation 
	  * while disallowing cell editing
	  */
	 static class table extends DefaultTableModel{
		 private static final long serialVersionUID = 1L;
		 public table(String[] columns, int size) {
			 super.setColumnIdentifiers(columns);
			 super.setRowCount(size);
		 }
		 public boolean isCellEditable(int row, int col){
			 return false;
		 }

	 }
}
