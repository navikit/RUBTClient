
package client;

import java.nio.*;
import java.util.*;

public enum Message {

   
	KEEPALIVE      (new byte[]{0, 0, 0, 0}),
	CHOKE          (new byte[]{0, 0, 0, 1, 0}),
	UNCHOKE        (new byte[]{0, 0, 0, 1, 1}),
	INTERESTED     (new byte[]{0, 0, 0, 1, 2}),
	NOTINTERESTED  (new byte[]{0, 0, 0, 1, 3}),
	HAVE           (new byte[]{0, 0, 0, 5, 4}),
	BITFIELD       (new byte[]{0, 0, 0, 1, 5}),   
	REQUEST        (new byte[]{0, 0, 0, 13, 6}),
	PIECE          (new byte[]{0, 0, 0, 9, 7}),
	CANCEL         (new byte[]{0, 0, 0, 13, 8});

	private final byte[] messageHead;

	Message (byte[] messageHead){
	  this.messageHead = messageHead;
	}

	/**
	* Returns what type of Message is being handled, also 
	* checks for basic syntax errors.
	*
	* @param recMessage    Any peer wire message, received as a byte array 
	*/
	public byte[] getMessageHead(){
	  byte[] copy = new byte[this.messageHead.length];
	  System.arraycopy(this.messageHead, 0, copy, 0, copy.length);
	  return copy;
	}

	public static Message getType(byte[] recMessage) throws IllegalArgumentException{
	  //First five are easy to verify
	  for (int i = 0; i < 5; i++){
		 if (Arrays.equals(recMessage, Message.values()[i].messageHead))
			return Message.values()[i];
	  }

	  //Make sure lengths and IDs are valid   
	  if (recMessage.length < 5 || recMessage[4] < 0 || recMessage[4] > 9)
		 throw new IllegalArgumentException("Bad message; invalid message ID of " + recMessage[4]);
	  else if (recMessage.length != ByteBuffer.wrap(recMessage).getInt() + 4){
		 throw new IllegalArgumentException("Bad message; prefix length = " + ByteBuffer.wrap(recMessage).getInt() + ", actual length = " + (recMessage.length - 4));
	  }
	  
	 return Message.values()[recMessage[4] + 1];
	}

	public static byte[] encodeMessage(Message type) throws RuntimeException {
		return encodeMessage(type, null);
	}

	public static byte[] encodeMessage(Message type, byte[] messageTail) throws RuntimeException{
	  byte[] outMessage = null;
	  
	  switch (type) {
		 case KEEPALIVE: case CHOKE: case UNCHOKE: case INTERESTED: case NOTINTERESTED:
			return type.messageHead;
		 
		 case HAVE:
			outMessage = new byte[4 + 5];
			System.arraycopy(type.messageHead, 0, outMessage, 0, 5);
			System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
			return outMessage;
		 
		 case REQUEST: case CANCEL:
			outMessage = new byte[4 + 13];
			System.arraycopy(type.messageHead, 0, outMessage, 0, 5);
			System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
			return outMessage;

		 case BITFIELD:
			outMessage = new byte[(4 + 1) + messageTail.length];
			System.arraycopy(type.messageHead, 0, outMessage, 0, 5);
			System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
			return outMessage;

		 case PIECE:
			outMessage = new byte[(4 + 1) + messageTail.length];
			byte[] lengthPrefix = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt( 1 + messageTail.length).array();
			System.arraycopy(lengthPrefix, 0, outMessage, 0, 4);
			System.arraycopy(type.messageHead, 4, outMessage, 4, 1);
			System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
			return outMessage;
		 default:
			throw new RuntimeException("Type not recognized: " + type.toString());
	  }
	}

	public static byte[] buildHaveTail(int index){
	  return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array();
	}

	public static byte[] buildRCTail(int index, int begin, int length){
		byte[] messageTail = new byte[12];
		byte[] indexArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array();
		byte[] beginArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(begin).array();
		byte[] lengthArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array();
		System.arraycopy(indexArray, 0, messageTail, 0, 4);
		System.arraycopy(beginArray, 0, messageTail, 4, 4);
		System.arraycopy(lengthArray, 0, messageTail, 8, 4);
		return messageTail;
	}

	public static byte[] buildPieceTail(int index, int begin, byte[] block){
		byte[] messageTail = new byte[8 + block.length];
		byte[] indexArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array();
		byte[] beginArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(begin).array();
		System.arraycopy(indexArray, 0, messageTail, 0, 4);  
		System.arraycopy(beginArray, 0, messageTail, 4, 4);
		System.arraycopy(block, 0, messageTail, 8, block.length);
		return messageTail;
	}
}


