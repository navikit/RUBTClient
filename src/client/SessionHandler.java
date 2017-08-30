package client;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.*;
import static java.nio.file.StandardOpenOption.*;

public class SessionHandler{
    FileChannel file_writer_info;
    File Info,Actual;
    int piece_size;
    /**
    *Creates a SessionHandler object with a corresponding filename and piece_size
    */
    public SessionHandler(String fileName, int pSize) throws IOException {
        piece_size=pSize;
        //Gets the directory position
        Path file = Paths.get("");
        Path new_path = Paths.get("");
        	new_path = FileSystems.getDefault().getPath(new_path.toAbsolutePath().toString(),fileName);
            //Helps get the path for new file and apeend together
            file = FileSystems.getDefault().getPath(file.toAbsolutePath().toString(),fileName+".info");
        try{   
            Info = new File(file.toAbsolutePath().toString());
            Actual = new File(new_path.toAbsolutePath().toString());
            if(!Info.exists()){
            	//creates a new file with name in directory
            	//Can also input /folder/filename to put file that folder
                Info.createNewFile();
            }
            //Create file writer object for the file
            file_writer_info = FileChannel.open(file,READ,WRITE);
        } catch (Exception e) {
            System.err.println("Error with opening the file. Please try a different filename.");
            throw e;
        }
        
        
    }
   
    /**
    *Loads the session from the file into memory and returns it
    *		This helps with resuming on with the file that is already written
    */
    public byte[] loadSession() throws IOException{
        file_writer_info.position(0);
    int size = (int)file_writer_info.size();
    byte[] x = new byte[size];
    ByteBuffer s = ByteBuffer.allocate(size);
    file_writer_info.read(s);
    x=s.array();
        return x;
    }
   
    /**
    *Loads the session from the file into memory AS A BYTEBUFFER and returns it
    *		This helps with resuming on with the file that is already written
    */
    public ByteBuffer loadSessionBuff() throws IOException{
        file_writer_info.position(0);
    ByteBuffer result = ByteBuffer.allocate((int)file_writer_info.size());
        file_writer_info.read(result);
        return result;
    }
   
    /**
    *Writes the session for this torrent to a file 
    *	True if successful, false otherwise
    *@param current_byte - The byte array of the current data we have
    **/
    public boolean writeSession(byte[] current_byte) throws IOException {
        boolean session_status = false;
        session_status = Info.delete();
        if(session_status){
        	//Remake the file in the original image
            Info.createNewFile();
            file_writer_info = FileChannel.open(FileSystems.getDefault().getPath(Info.getAbsolutePath(),""),READ,WRITE);//Create a file writer for this file
        }
        try{
            ByteBuffer updated_byte = ByteBuffer.wrap(current_byte);
            file_writer_info.write(updated_byte,0);
        }catch(Exception e){
            System.err.println("Error with the writing session!");
            throw e;
        }
       
        return Info.exists() && session_status;
    }

    /**
    *Reads all of the previously downloaded info into memory
    *
    *@param piece_size - Need the size of the pieces so this can read in correct sized blocks
    *returns: a byte[][] that contains messages that prepackage the pieces already downloaded in a prev session
    **/
    public byte[][] getPrevSessionMessages() throws IOException{
        byte[][] result;
        int numOn=0;
        int size = (int)file_writer_info.size();
        ByteBuffer s = ByteBuffer.allocate(size);
        file_writer_info.read(s);
        byte[] x = new byte[size];
        x=s.array();
       
        for(int i=0; i<size;i++){
            for(int j=0;j<8;j++){
                if(((x[i]>>j)&1)==1){
                    //Byte i, offset j is on! (Have to do this so I know how many arrays to allocate)
                    numOn++;
                }
               
            }
        }
       
    x=s.array();
        result = new byte[numOn][13+piece_size];
        int test=0;
        byte[] data = new byte[piece_size];
    byte[] tail = new byte[piece_size+8];
    RandomAccessFile f = new RandomAccessFile(Actual,"r");
    Message m = Message.PIECE;
        
    for(int i=0; i<size;i++){
            for(int j=0;j<8;j++){
                if(((x[i]>>j)&1)==1){
                    //Byte i, offset j is on!
                    //read(8i+jth piece from file);
                    int pos = piece_size*((8*i)+j);//THIS IS WHERE YOU WOULD CHANGE IF YOU MESSED UP BIG ENDIAN STUFF
                    f.read(data,pos,piece_size);
                    tail = m.buildPieceTail(pos,0,data);
                    result[test] = m.encodeMessage(Message.PIECE,tail);
                    test++;
                }
               
            }
        }
       f.close();
        return result;
    }
   
    /**
    * This method loads up the data from the previous session into a byte array that holds piece data
    *
    * @param piece_size The size of a piece for this file.
    * returns a byte[][] completed with all of the data we have so far. 
    */   
    public byte[][] getPrevSessionData() throws IOException{
        file_writer_info.position(0);
        int size = (int)file_writer_info.size();
        byte[] x = new byte[size];
        ByteBuffer s = ByteBuffer.allocate(size);
        file_writer_info.read(s);
        byte[][] result = new byte[(8*size)][piece_size]; 
        //Size is # of bytes in bitfield file, multiply by 8 to get # of bits
            x=s.array();
        RandomAccessFile f = new RandomAccessFile(Actual,"r");
        //System.out.println("IN SESSIONDATA: "+Arrays.toString(x));
        for(int i=0; i<size;i++){
            for(int j=0;j<8;j++){
                if(((x[i]>>j)&1)==1){
                        //System.out.println("Byte "+i+", offset "+(7-j)+" is on!!!");
                    int pos = piece_size*((8*i)+(7-j));//THIS IS WHERE YOU WOULD CHANGE IF YOU MESSED UP BIG ENDIAN STUFF
            //System.out.println("Getting byte:"+pos);
            f.seek((long)pos);
            f.read(result[(8*i)+(7-j)],0,piece_size);
                }
               
            }
        }
        f.close();
        return result;
    }
   
   /**
   * This method gets a piece of the file to send out to a peer
   * 
   * @param pieceIndex - The index of the piece to be returned
   * @offset_in_piece - The <begin> offset specified by the peer
   * request_size - The size of the request being made
   * returns the data specified by the above.
   */
   public byte[] getPiece(int pieceIndex, int offset_in_piece, int request_size) throws IOException{
       RandomAccessFile random_file = new RandomAccessFile(Actual,"r");
       byte[] session_status = new byte[request_size];
       int position = (piece_size * pieceIndex) + offset_in_piece;
       random_file.read(session_status,position,request_size);
       random_file.close();
       return session_status;
   }
   
   
   /**
   * This method gets a piece of the file to send out to a peer, and packages it as a message
   * 
   * @param pieceIndex - The index of the piece to be returned
   * offset_in_piece - The <begin> offset specified by the peer
   * @sizeOfRequest - The size of the request being made
   * returns the message containing the data specified by the above.
   */
   public byte[] getPieceMessage(int pieceIndex, int offset_in_piece, int request_size) throws IOException{
       byte[] data = new byte[request_size];
       byte[] message = new byte[13 + request_size];
       Message m = Message.PIECE;
       data = getPiece(pieceIndex, offset_in_piece, request_size);
       message = m.encodeMessage(Message.PIECE,m.buildPieceTail(pieceIndex, request_size,data));
       return message;
   }
   
   
   
}
