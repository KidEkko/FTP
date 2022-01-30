import javax.swing.*;
import javax.swing.event.*;
import javax.swing.SwingWorker.*;
import javax.swing.filechooser.FileFilter;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.*;
import java.io.*;
public class ClientSideFileTransfer {
	
	private enum ConnectionState {CONNECTING, CONNECTED, CLOSED};
	private enum SendingState {SENDING, RECEIVING};
	
	private static final String[] SERVERS = {"Lappy", "DrewGod", "Cody"};
	private static final String[] SERVERIPS = {"192.168.1.138", "192.168.1.138", "192.168.1.142"};
	private static final String DEFAULT_LIST_LOCATION = "C:\\Stored Files";
	private static final String DEFAULT_SAVE_LOCATION = "C:\\Received Files";
	private static String saveLocation = DEFAULT_SAVE_LOCATION;
	private static boolean saveLocationChosen = false;
	private static final File LIST_FILE = new File(DEFAULT_LIST_LOCATION); // would be the software's location if this was an actual program
	private static final int DEFAULT_PORT = 1500;
	private static int connectIndex = 0;
	
	private static final String DEFAULT_SERVER_HOST = "localhost";
	private static final String DEFAULT_TEXT = "This is a File Transfer Program.\n\n"
			+ "First, select a server from the Server List.\n\n"
			+ "Keep in mind that disconnecting from the Server early can cause file data to be incomplete.\n\n"
			+ "Then, you may select \"Server Files\" to browse the selected Server's Files\n\n"
			+ "Received Files will be written to: " + saveLocation + "\n\n"
			+ "You can change this with the \"Save Location\" Button.\n\n"
			+ "Finally, you may select \"Your Files\" to select your own file to transfer.\n\n";
	private static JFileChooser choose = new JFileChooser("C:\\");
	private static JFileChooser serverFiles;
	private static FileFilter sFilter = new ServerFilter();
	private static Socket connect;
	private static ObjectOutputStream objectSender;
	private static ObjectInputStream objectReader;

	public static void main(String[] args) {
		new FTWindow();
		/*
		 * ClientSideFileTransfer window = new ClientSideFileTransfer();
		 * window.setVisible(true);
		 * eliminate ftwindow class, have CSFT extend JFrame
		 * eliminate the static parts of the classes and variables
		 */
	}
	
	/*
	 * 1/2
	 * technically this program is finished as an FTP
	 * technically meaning that it functions
	 * this program feels very incomplete
	 * but the things I know should be added are projects in themselves
	 * and it would be easier to do them in a seperate program that isn't already as long as this one
	 * the things I want to do are:
	 * a connect feature that port scans before connecting to a server, not too hard but still
	 * progress bar for download/upload, requires creating a swingworker class that interacts with property change
	 * doesn't seem horrible for downloading since that's simple enough to put into doinbackground, uploads on the other hand seem like a lot
	 * ping function that checks if servers are online. would probably be a good combination with the port scanner tbh
	 * 
	 * the above things are things I feel are do-able, but not worth as much as getting experience doing something else
	 * the below things are things I feel would take a lot of unnecessary time:
	 * client list feature where users that connect to the server are given unique identifiers
	 * the unique identifiers would have the server filter stored files to each user. think of it as an actual FTP program for download
	 * 
	 * actually thats it for complex features, but that's a lot of work I feel, if I were adding to this instead of making it in a new program.
	 * 
	 * I guess that's all for this code...
	 */

	private static class FTWindow extends JFrame {
		private static ConnectionState status;
		private static JTextArea info;
		private static JButton serverButton;
		private static JButton disconnect;
		private static JPanel top = new ConnectionPanel();
		
		FTWindow() {
			super("File Transfers For The Fortunate");
			JPanel content = new JPanel();
			JPanel bottom = new ButtonPanel();
			info = new JTextArea(5, 20);
			info.setMargin(new Insets(5, 5, 5, 5));
			info.setEditable(false);
			info.setText(DEFAULT_TEXT);
			JScrollPane infoScroll = new JScrollPane(info);
			
			content.setLayout(new BorderLayout(5, 5));
			
			
			content.add(top, BorderLayout.NORTH);
			content.add(infoScroll, BorderLayout.CENTER);
			content.add(bottom, BorderLayout.SOUTH);

			setContentPane(content);
	        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

	        addWindowListener(new CleanupOnClose(this));
			setVisible(true);
		    setSize(600,400);
		    Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();
		    setLocation((screensize.width - getWidth())/2, (screensize.height - getHeight())/2);
		}
		
		private static class ConnectionPanel extends JPanel {
			private JLabel serverName;
			private JLabel serverStatus;
			private JLabel conStatus;
			private Color goodGreen = new Color(0, 200, 0);

			ConnectionPanel() {
				JMenuBar bar = new JMenuBar();
				JMenu menu = new JMenu("Servers");
				JMenuItem item;
				ActionListener mListen = new MenuListener();
				
				for (int i = 0; i < SERVERS.length; i++) {
					item = new JMenuItem(SERVERS[i]);
					item.setName(SERVERS[i]);
					item.addActionListener(mListen);
					menu.add(item);
				}
				
				setLayout(new GridLayout(1, 4, 30, 0));
				
				bar.add(menu);
				
				serverName = new JLabel(DEFAULT_SERVER_HOST);
				add(serverName);
				serverStatus = new JLabel("Online");
				serverStatus.setForeground(goodGreen);
				add(serverStatus);
				add(bar);
				JLabel label = new JLabel("Connection:");
				add(label);
				conStatus = new JLabel("Not Connected");
				conStatus.setForeground(Color.orange);
				add(conStatus);
				bar.grabFocus();
			}

			private static class MenuListener implements ActionListener {
				@Override
				public void actionPerformed(ActionEvent e) {
					Component source = (Component)e.getSource();
					if (status == ConnectionState.CONNECTED) 
						cleanup();
					
					int portToTry = DEFAULT_PORT; 
					
					for (int i = 0; i < SERVERS.length; i++) 
						if (source.getName().equals(SERVERS[i])) 
							connectIndex = i;
					
					status = ConnectionState.CONNECTING;

					while (status == ConnectionState.CONNECTING) {
						try {
							//problem is that this line doesn't generate errors, it simply tries to connect. 
							//that being the case, a solution is to instead invoke a connect method that checks 
							//available ports on the server and then connects
							connect = new Socket(InetAddress.getByName(SERVERIPS[connectIndex]), portToTry); 
							newConnection();
							receiveList();
							if (saveLocationChosen) {
								objectSender.writeObject((Integer) 2);
								objectSender.writeObject(saveLocation);
							}
						} catch (IOException ioExcept) {
								JOptionPane.showMessageDialog(null, "Unable to connect to any " + SERVERS[connectIndex] + " server ports.\n"
										+ "Please try again, or at a different time.");
								cleanup();
							
						} catch (ClassNotFoundException cNFE) {
							JOptionPane.showMessageDialog(null, "FileChooser not received properly.");
						}
					}
					
					top.repaint(); //meant to update everything, might eventually add a ping function to actually check the server
				}
			}
			
			
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (connect != null) {
					serverName.setText(SERVERS[connectIndex]);  //c? less important aspect of the program tbh
					if (connect.isConnected()) {				//can look at it later
						if (status == ConnectionState.CONNECTED) {
							conStatus.setText("Connected");
							conStatus.setForeground(goodGreen);
						}
						else {
							conStatus.setText("Not Connected");
							conStatus.setForeground(Color.red);
						}
						serverStatus.setText("Online");
						serverStatus.setForeground(goodGreen);
					}
					else {
						serverStatus.setText("Offline");
						serverStatus.setForeground(Color.red);
					}
				}
				else {
					conStatus.setText("Not Connected");
					conStatus.setForeground(Color.orange);
					
				}
			}
			
			
		}
		
		private static class ButtonPanel extends JPanel {
			private static SendingState send;
			private static JProgressBar download;
			
			ButtonPanel() {
				JPanel bottom = new JPanel();
				download = new JProgressBar();
				JButton button;
				ButtonListener bListen = new ButtonListener();
				bottom.setLayout(new GridLayout(1, 4, 10, 5));
				setLayout(new GridLayout(2, 1, 10, 5));
				
				button = new JButton("Your Files");
				button.setName("CurrentFiles");
				button.addActionListener(bListen);
				bottom.add(button);
				
				serverButton = new JButton("Server Files");
				serverButton.setName("ServerFiles");
				serverButton.addActionListener(bListen);
				serverButton.setEnabled(false);
				bottom.add(serverButton);
				
				button = new JButton("Save Location");
				button.setName("Save");
				button.addActionListener(bListen);
				bottom.add(button);
				
				disconnect = new JButton("Disconnect");
				disconnect.setName("Disconnect");
				disconnect.addActionListener(bListen);
				disconnect.setEnabled(false);
				bottom.add(disconnect);
				
				download.setValue(0);
				download.setStringPainted(true);
				download.addPropertyChangeListener(new ProgressListener());
				
				add(download);
				add(bottom);
				
			}
			/*
			 * things to do to get a progress bar to work here:
			 * 1. create a task class extending swingworker 
			 * this class has its own doinbackground and done methods
			 * doinbackground would check sendingState, then either send/recieve files
			 * download progress seems easy, just put receiveFiles() method into the task and add progress updates into the code
			 * uploads on the other hand seem like a lot more work, since I'd either have to move sendFiles/FileData into the task class
			 * in order to update, or I'd have to somehow add progress updates to the methods themselves
			 * done method would be pretty simple
			 * 2. have the server/your files methods each create a new task on approve option instead of doing it themselves
			 */
			
			private static class ProgressListener implements PropertyChangeListener {

				@Override
				public void propertyChange(PropertyChangeEvent evt) {

					
				}
				
			}
			
			private static class ButtonListener implements ActionListener {
				@Override
				public void actionPerformed(ActionEvent e) {
					Component source = (Component)e.getSource();
					
					if (source.getName().equals("CurrentFiles")) {
						choose.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
						send = SendingState.SENDING;
						int returnVal = choose.showDialog(null, "Transfer");
						
						if (returnVal == JFileChooser.APPROVE_OPTION) {
							File chosenFile = choose.getSelectedFile();
							try { 
								objectSender.writeObject((Integer) 0);
								objectSender.flush();
								
								sendFiles(chosenFile);
								receiveList();
							} catch (IOException ioExcept) {
								JOptionPane.showMessageDialog(null, ioExcept.getMessage());
							} catch (ClassNotFoundException classExcept) {
								JOptionPane.showMessageDialog(null, classExcept.getMessage());
							}
						}
						
					}
					else if (source.getName().equals("ServerFiles")) {
						serverFiles.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
						send = SendingState.RECEIVING;
						int returnVal = serverFiles.showDialog(null, "Transfer");

						if (returnVal == JFileChooser.APPROVE_OPTION) { 
							File chosenFile = serverFiles.getSelectedFile();
							try {
								objectSender.writeObject((Integer) 1);
								objectSender.writeObject(chosenFile);
								
								receiveFiles();
							} catch (IOException ioExcept) { //wouldn't do it this way in a real program probably
								JOptionPane.showMessageDialog(null, ioExcept.getMessage());
							} catch (ClassNotFoundException classExcept) {
								JOptionPane.showMessageDialog(null, classExcept.getMessage());
							}
							
						}
					}
					else if (source.getName().equals("Save")) {
						choose.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
						int returnVal = choose.showDialog(null, "Choose");

						if (returnVal == JFileChooser.APPROVE_OPTION) {
							saveLocation = choose.getSelectedFile().getAbsolutePath();
							saveLocationChosen = true;
							if (status == ConnectionState.CONNECTED) {
								try {
									objectSender.writeObject((Integer) 2);
									objectSender.writeObject(saveLocation);
									objectSender.flush();
								} catch (IOException ioExcept) { 
									JOptionPane.showMessageDialog(null, ioExcept.getMessage());
								}
							}
							updateJTA();
						}
							
						
					}
					else if (source.getName().equals("Disconnect")) 
						cleanup();
					
				}
				
				private boolean topDirectorySet = false;
				private File topDirectory;
				public void sendFiles(File sendingFile) throws IOException { 
					boolean lastFileSent = false;
					String[] fileList = sendingFile.list();
					if (fileList == null) {
						sendFileData(sendingFile, 1);
						lastFileSent = true;
					}
					else 
						for (int i = 0; i < fileList.length; i++) {
							File currentFile = new File(sendingFile, fileList[i]);
							if (!topDirectorySet) {
								topDirectory = newPath(sendingFile, 1);
								lastFileSent = true;
								topDirectorySet = true;
							}
							
							if (currentFile.isDirectory()) 
								sendFiles(currentFile);
							
							else 
								sendFileData(currentFile, 2);
							
						
						}
					if (lastFileSent)
						sendFileData(null, 1);
					
						
				}
				//might need to add some sort of progress bar so that doesn't happen, some sort of error prevention
				
				public void sendFileData(File fileToSend, int option) throws IOException {

					byte[] contents;
					long fileLength;
					long current = 0;
					
					if (fileToSend != null) {
						fileLength = fileToSend.length();
						FileInputStream fis = new FileInputStream(fileToSend);
						BufferedInputStream bis = new BufferedInputStream(fis);
						
						File temp = newPath(fileToSend, option);
						objectSender.writeObject(temp);
						objectSender.flush();
						
						//send the current file over the stream
						//still not fully sure what the while loop does
						//like I get it but I don't

						while(current != fileLength) {
							int size = 10000;
							if(fileLength - current >= size)
								current += size;
							else{
								size = (int)(fileLength - current);
								current = fileLength;
							}
							
							contents = new byte[size];
							bis.read(contents, 0, size);
							objectSender.write(contents);
						}
						objectSender.flush();
						bis.close();
					}
					else 
						objectSender.writeObject(fileToSend);
					
				}
				
				public void receiveFiles() throws IOException, ClassNotFoundException { 
					while (true) {
						//receive files
						byte[] contents = new byte[10000];
						
						File receivedFile = (File)objectReader.readObject();
						if (receivedFile == null)
							break;
						
						if (!receivedFile.getParentFile().exists()) 
							receivedFile.getParentFile().mkdirs();
					
						FileOutputStream fos = new FileOutputStream(receivedFile);
						BufferedOutputStream bos = new BufferedOutputStream(fos);
						int bytesRead = 0;
					
						while((bytesRead = objectReader.read(contents))!=-1)
							bos.write(contents, 0, bytesRead);
						bos.flush();
					}
							
				}

				public File newPath(File fileToChange, int option) {
					File brandNew;
					File temp = fileToChange;
					String newPath;
					if (send == SendingState.RECEIVING) 
						newPath = DEFAULT_SAVE_LOCATION;
					else 
						newPath = DEFAULT_LIST_LOCATION;
					
					int counter = 0;
					
					
					if (option == 1) {
						newPath = newPath.concat("\\" + fileToChange.getName());

						brandNew = new File(newPath);
						return brandNew;
					}
					else if (option == 2) {
						//if the files being sent are from a large directory
						//this option will set their new path to a correct one
						//for the server to save to
						do {
							counter++;
						} while ( !( (fileToChange = fileToChange.getParentFile() ).getName().equals(topDirectory.getName())) );
						
						String[] pathFiles = new String[counter];
						//add each file name to a string array

						for (int i = 0; i < counter; i++) {
							pathFiles[i] = temp.getName(); 
							temp = temp.getParentFile();
						}
						
						newPath = newPath.concat("\\" + topDirectory.getName());

						//read in reverse to create the new string
						for (int i = counter - 1; i >= 0; i--) 
							newPath = newPath.concat("\\" + pathFiles[i]);

						//and a new File was born
						brandNew = new File(newPath);

						return brandNew;
					}
					else
						return fileToChange;
					
					
				}
			}
		}
		public static void receiveList() throws IOException, ClassNotFoundException{
			int filesReceived = 0;
			int filesToReceive = objectReader.readInt();
			
			while (filesReceived < filesToReceive) {
				File currentFile = (File)objectReader.readObject();
				
				currentFile.mkdirs();
				
				filesReceived++;
			}
			serverFiles = new JFileChooser(DEFAULT_LIST_LOCATION);
			serverFiles.setFileFilter(sFilter);
			serverButton.setEnabled(true);
		}
		
		public static void newConnection() throws IOException {
			status = ConnectionState.CONNECTED;
			disconnect.setEnabled(true);
			objectReader = new ObjectInputStream(connect.getInputStream());
			objectSender = new ObjectOutputStream(connect.getOutputStream());
			
		}
		public static void cleanup() {
			status = ConnectionState.CLOSED;
	        if (connect != null && !connect.isClosed()) {
	              // Make sure that the socket, if any, is closed.
	           try {
	              connect.close();
	           }
	           catch (IOException e) {
	           }
	        }
	        connect = null;
			objectReader = null;
			objectSender = null;
			disconnect.setEnabled(false);
			serverButton.setEnabled(false);
			// could update the JTA as well here, probably should, might be dependent on how cleanup is called though?
			deleteFile(LIST_FILE);
		}
		
		private static void updateJTA() {
			info.setText("This is a File Transfer Program.\n\n"
					+ "First, select a server from the Server List.\n\n"
					+ "Keep in mind that disconnecting from the Server early can cause file data to be incomplete.\n\n"
					+ "Then, you may select \"Server Files\" to browse the selected Server's Files\n\n"
					+ "Received Files will be written to:\n\n" + saveLocation + "\n\n"
					+ "You can change this with the \"Save Location\" Button.\n\n"
					+ "Finally, you may select \"Your Files\" to select your own file to transfer.\n\n");
			
		}
		
		public static void deleteFile(File fileToDelete) { 
			String[] list = fileToDelete.list();
			if (list == null || list.length == 0) 
				fileToDelete.delete();
			else
				for(int i = 0; i < list.length; i++) {
					File currentFile = new File(fileToDelete, list[i]);
					if(currentFile.isDirectory())
						deleteFile(currentFile);
					else
						fileToDelete.delete();
				}

			fileToDelete.delete();
			
		}
		
	    class CleanupOnClose extends WindowAdapter {
	    	 
	    	FTWindow window = null;
	 
	    	CleanupOnClose(FTWindow window) {
	            this.window = window;
	        }
	 
	        public void windowClosing(WindowEvent e) {
	        	//closes everything up nice and neat
	        	cleanup();
	            System.exit(0);
	        }
	    }
	}
	public static class ServerFilter extends FileFilter {

		public boolean accept(File pathname) {
			if (pathname.getAbsolutePath().contains("C:\\Stored Files"))
				return true;

			return false;
		}

		@Override
		public String getDescription() {
			return "Stored Files";
		}
	}
		
}

