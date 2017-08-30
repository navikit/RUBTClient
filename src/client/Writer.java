package client;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.*;
import static java.nio.file.StandardOpenOption.*;


public class Writer{
	private int pSize;//The piece size for the current torrent. Used by the message method.
	FileChannel fileWriter;

	/**
	*Creates a Writer object with a corresponding filename and piecesize
	*
	*@param filename = the name you want the file to be
	*@param pieceSize = Size of a Piece in this torrent
	*/
	public Writer(String filename, int pieceSize) throws IOException {
		pSize = pieceSize;
		//Get current directory
		Path file = Paths.get("");
		//Get directory path and append with the file name
		file = FileSystems.getDefault().getPath(file.toAbsolutePath().toString(),filename);
		try{	
			File temp = new File(file.toAbsolutePath().toString());
			temp.createNewFile();
			fileWriter = FileChannel.open(file,READ,WRITE);
		} catch (Exception e) {
			System.err.println("Error with opening the file. Please try a different file name.");
			throw e;
		}
	}
	
	/**
	*Writes a byte to a file at a given position
	*
	*@param position = the position in the file this byte is to be written to [bytes]
	*@param data = the byte to be written to the file
	*/
	public void writeData(int position, Byte data){//Write one byte at the specified location in the file. Will expand the file if necessary
		try{
			byte[] temp = new byte[1];
			temp[0]=data; //Catch the byte, and wrap it up to be written by the filewriter
			ByteBuffer s = ByteBuffer.wrap(temp);
			
			long pos = Long.valueOf(position);//Don't you just love java datatype conversion?
			fileWriter.write(s,pos);//Write the byte to the specified position
		} catch(Exception e) {
			System.err.println("ERROR WRITING TO FILE");
		}
	}
	
	/**
	*Writes an entire bytebuffer to a file at a given position
	*
	*@param position = the position in the file this byte is to be written to [bytes]
	*@param writeall = the bytebuffer that is to be written to the file
	*/
	public void writeData(int position, ByteBuffer writeall){//Write all of the buffer to the specified location in the file. Will expand the file if necessary
		try{
			long pos = Long.valueOf(position);//Same as above but the data is already formatted for the FileChannel
			fileWriter.write(writeall,pos);
		} catch(Exception e) {
			System.err.println("ERROR WRITING TO FILE");
		}
	}

	/**
	*Write a subset of the buffer beginning @ offset and ending at offset+numbertoprint to the file. Will expand the file if necessary.  
	*@param position = the position in the file this byte is to be written to [bytes]
	*@param offset = the beginning index of the elements you want to write from the bytebuffer
	*@param numberToPrint = The number of bytes you want to print from the bytebuffer
	*@param writeFromHere = the bytebuffer that is to be written to the file
	*
	*/
	public void writeData(int position, int offset, int numberToPrint, ByteBuffer writeFromHere){
		long pos = Long.valueOf(position);
		try{
			byte[] temp = new byte[numberToPrint];//Create a byte array to hold the subset of the bytebuffer you want to read from
			
			int i;
			//Walk across the bytebuffer for the specified length and copy to temp
			for(i=0; i<numberToPrint; i++){
				temp[i] = (writeFromHere.get(i+offset));
			}
			
			ByteBuffer s = ByteBuffer.wrap(temp);//wrap temp up and send it to the fileWriter.
			fileWriter.write(s,pos);
		} catch(Exception e) {
			System.err.println("ERROR WRITING TO FILE");
		}
	}
	
	/**
	*Write a given message to the file. Has already been checked for validity. Only usable when we get a message=piecesize
	*    
	*@param array = the message to be written to the disc [contains all the data needed for position, as well as the data itself]
	*/
	public void writeMessage(byte[] array){
		int msgLength = ByteBuffer.wrap(array).getInt();		//Get the length of the data to be written.
		byte[] block = new byte[msgLength-9];					//Create a byte array to hold that data.
		System.arraycopy(array, 13, block, 0, msgLength-9);		//copy the data into this block.
		int pieceIndex = ByteBuffer.wrap(array,5,4).getInt();	//Get the piece number that this data is from.
		int beginIndex = ByteBuffer.wrap(array,9,4).getInt();	//Get the offset within that piece.
		int position = ((pSize*pieceIndex) + beginIndex);		//Calculate the final position in the file from the above data
		
		writeData(position,ByteBuffer.wrap(block));				//Write the data to the file
	}

   


}
