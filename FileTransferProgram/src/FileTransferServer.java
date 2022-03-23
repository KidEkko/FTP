import javax.swing.*;


import java.net.*;
import java.util.ArrayList;
import java.io.*;

public class FileTransferServer {
	
	private final String DEFAULT_LIST_LOCATION = "C:\\Stored Files";
	private final String DEFAULT_SAVE_LOCATION = "C:\\Received Files";
	private boolean newSaveLocationChosen = false;
	private String newSaveLocation;
	private final File baseFile = new File(DEFAULT_LIST_LOCATION);
	private ArrayList<File> storedFileList = new ArrayList<File>();
	/*
	 * in a world where this is an actual program/software for use by multiple people
	 * you would have some user system where each user has their own individual files (SQL for example)
	 * once again this program is not all you'd want for an FTP, and other languages are far better now that I've learned some of them
	 */
	private FileTransferServer server;
	private ConnectionHandler connection;
	private ServerSocket listener;
	private enum ConnectionState {CONNECTED, CLOSED};
	private enum SendingState {SENDING, RECEIVING};
	
	private final int DEFAULT_PORT = 1500;
	
	public static void main(String[] args) {
		FileTransferServer server = new FileTransferServer();
	}
	
	public FileTransferServer() {
		int port = DEFAULT_PORT;
		try {
			updateFileList(baseFile);
			
			while (true) {
				System.out.println("Opening Port " + port + ".");
				listener = new ServerSocket(port);
				Socket socket = listener.accept();
				port++;
				connection = new ConnectionHandler(socket);
			}
		} catch (BindException e) {
			port = lowestAvailablePort();
			server = new FileTransferServer();
		} catch (Throwable e) {
		
		} finally {
			System.out.println("Shutting down.");
			//would be a shutdown method here
		}
	}
	
	protected synchronized void updateFileList(File fileToAdd) {
		String[] list = fileToAdd.list();
		boolean parentAdded = false;
		if (list == null)
			storedFileList.add(fileToAdd);
		else {
			for (int i = 0; i < list.length; i++) {
				File currentFile = new File(fileToAdd, list[i]);
				if (currentFile.isDirectory()) 
					updateFileList(currentFile);
				
				else
					if(!parentAdded) {
						storedFileList.add(fileToAdd);
						parentAdded = true;
					}
					else
						storedFileList.add(currentFile);
			}
		}
	}
	
	private int lowestAvailablePort() { //was an idea I had, doesn't work, don't plan to update
		int lowest = DEFAULT_PORT;
		while (lowest < 65535) {
			try {
				listener = new ServerSocket(lowest);
				listener.close();
				return lowest;
			} catch (BindException e) {
				lowest++;
			} catch (IOException e) {
				System.out.println("RIP");
			}
		}
		return lowest;
	}
	
	private class ConnectionHandler extends Thread {
		private volatile ConnectionState status;
		private volatile SendingState send;
		private Socket socket;
		private ObjectOutputStream objectSender;
		private ObjectInputStream objectReader;
		
		ConnectionHandler(Socket socket) {
			System.out.println("Starting");
			this.socket = socket;
			start();
		}
		
		/*
		 * changes need to be made to this in order for threads to work properly
		 * run() should do minimal work, mostly just call separate synced methods
		 */
		
		public void run() {
			try {
				newConnection();
				sendFileList();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			try {
				while (status == ConnectionState.CONNECTED) {
					int received = (Integer)objectReader.readObject();
				
					switch(received) {
					case 0:
						//receive files
						while (true) {
							send = SendingState.RECEIVING;
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
							updateFileList(receivedFile);
						}
						sendFileList();
						break;
					case 1:
						//send files
						send = SendingState.SENDING;
						
						File sendingFile = (File)objectReader.readObject();
						sendFiles(sendingFile);
						break;
					case 2:
						newSaveLocation = (String)objectReader.readObject();
						newSaveLocationChosen = true;
						break;
					default:
						cleanup();
						return;
					}
					
				}
			} catch (IOException e) {
				//e.printStackTrace();
				//should print errors to a text file
				//should have some kind of notification to let devs know there was an error? idk
			} catch (ClassNotFoundException e) {
				System.out.println(e.getMessage());
			} 
			finally {
				cleanup();
			}
			
		}
		
		synchronized void newConnection() throws IOException {
			listener = null;
			objectSender = new ObjectOutputStream(socket.getOutputStream());
			objectReader = new ObjectInputStream(socket.getInputStream());
			status = ConnectionState.CONNECTED;
		}
		
		private void cleanup() {
			
	        status = ConnectionState.CLOSED;
	        if (socket != null && !socket.isClosed()) {
	              // Make sure that the socket, if any, is closed.
	           try {
	              socket.close();
	           }
	           catch (IOException e) {
	           }
	        }
            socket = null;
	        objectSender = null;
	        objectReader = null;
	        listener = null;
			
		}
		
		private void sendFileList() throws IOException {
			objectSender.writeInt(storedFileList.size());
			objectSender.flush();
			for(File f: storedFileList) {
				objectSender.writeObject(f);
				objectSender.flush();
			}
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
				sendFileData(null, 0);
			
				
		}
		
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
		
		public File newPath(File fileToChange, int option) {
			File brandNew;
			File temp = fileToChange;
			String newPath;
			if (send == SendingState.RECEIVING) 
				newPath = DEFAULT_LIST_LOCATION;
			else 
				if(newSaveLocationChosen)
					newPath = newSaveLocation;
				else
					newPath = DEFAULT_SAVE_LOCATION;
			
			
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
	
