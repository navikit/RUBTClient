
package client;

import java.util.*;

public class Bitfield implements Cloneable {
	/**
	 * The whole array is treated as a big-endianed sequence
	 * of bits where the highest bit corresponds to bit 0.
	 */
	public byte[] array;
	public final int numBits;

	protected static final BitwiseFunction AND = new And();
	protected static final BitwiseFunction OR = new Or();
	protected static final BitwiseFunction XOR = new Xor();
	protected static final BitwiseFunction NOT = new Not();

	/**
	 * Creates a new Bitfiled of numBits numBits (with all 0's)
	 * @param  numBits the number of bits that this bitfield will hold
	 */
	public Bitfield(int numBits) {
		if (numBits > 0) {
			int numBytes = numBits / 8;
			// add one if 1-7, 9-15,... bits
			if (numBits % 8 > 0)
				numBytes++;

			array = new byte[numBytes];
		} else {
			array = null;
		}
		this.numBits = numBits;
	}

	/**
	 * Decodes the Bitfield
	 * @param  array   initialized byte array describing the bitfield
	 * @param  numBits number of bits useful in array
	 */
	protected Bitfield(byte[] array, int numBits) {
		this.array = array;
		this.numBits = numBits;
	}

	public Boolean get(int bitNum) {
		Boolean value = null;
		if (bitNum < numBits) {
			int wantedByte = bitNum / 8;
			byte mask = (byte)(1 << (7 - (bitNum % 8)));
			if ((((array[wantedByte] & mask) >> (7 - (bitNum % 8))) & 1) == 1)
				value = true;
			else
				value = false;
		}
		return value;
	}

	/**
	 * sets the given bit to 1.
	 * @param bitNum the position of the bit to be flipped on
	 */
	public void set(int bitNum) {
		setValue(bitNum, true);
	}

	/**
	 * sets the given bit to 0.
	 * @param bitNum the position of the bit to be switched off
	 */
	public void unset(int bitNum) {
		setValue(bitNum, false);
	}

	/**
	 * Set given bit to 1 if value is true
	 * 		0 if value is false
	 * Only make the change if the bit within allowed range
	 * @param bit   bit to be changed
	 * @param value true if bit should be set to 1,
	 *              false if it should be set to 0
	 */
	protected void setValue(int bit, Boolean value) {
		if (bit < numBits) {
			int wantedByte = bit / 8;
			byte mask = (byte)(1 << (7 - bit % 8));
			if (value == true)
				array[wantedByte] = (byte)(array[wantedByte] | mask);
			else
				array[wantedByte] = (byte)(array[wantedByte] & (byte)(~mask));
		}
	}

	@Override
	public Bitfield clone() {
		return new Bitfield(Arrays.copyOf(array, array.length), numBits);
	}

	/**
	 * Prints BitField in a line
	 * 		Used to check for any errors
	 */
	public String toString() {
		StringBuilder str = new StringBuilder("Bitfield Length: " + numBits + "  Bits: ");
		for (int i = 0; i < array.length; i++) {
			for (int j = 7; j >= 0; j--) {
				if (((array[i] >> j) & 1) == 1)
					str.append("1");
				else
					str.append("0");
			}
			str.append(" ");
		}
		return str.toString();
	}

	private void printByte(byte b) {
		for (int j = 7; j >= 0; j--) {
			if (((b >> j) & 1) == 1)
				System.out.print("1");
			else
				System.out.print("0");
		}
		System.out.println();
	}

	/**
	 * Creates new Bitfield out of a given array 
	 * Where the number of bits bitfield will examine
	 * of bits that the bitfield will look at
	 * @param  array   the bitfield array
	 * @param  numBits the number of bits relevant in the bitfield
	 * @return         a Bitfield corresponding to the given array
	 */
	public static Bitfield decode(byte[] array, int numBits) {
		int numBytes = numBits / 8;
		if (numBits % 8 > 0)
			numBytes++;

		if (numBits > 0 && array.length == numBytes) {
			return new Bitfield(array, numBits);
		} else {
			return null;
		}
	}

	public Set<Integer> getSetBitIndexes() {
		Set<Integer> intSet = new HashSet<>();
		for (int i = 0; i < numBits; i++) {
			if (get(i) == true)
				intSet.add(i);
		}
		return intSet;
	}

	/**
	 * Will perform a bitwise AND with this Bitfield
	 */
	public Bitfield and(Bitfield otherBitfield) {
		return BitfieldAND(this, otherBitfield);
	}

	/**
	 * Will perform a bitwise OR with this Bitfield
	 */
	public Bitfield or(Bitfield otherBitfield) {
		return BitfieldOR(this, otherBitfield);
	}

	/**
	 * Will perform a bitwise XOR with this Bitfield
	 */
	public Bitfield xor(Bitfield otherBitfield) {
		return BitfieldXOR(this, otherBitfield);
	}

	/**
	 * Will perform a bitwise NOT with this Bitfield
	 */
	public Bitfield not() {
		return BitfieldNOT(this);
	}

	/**
	 * The next 4 static methods just allow for AND,
	 * NOT, OR, and XOR to be used as class methods.
	 * They all take in at least 1 bitfield and return a
	 * new Bitfield representing the result of the operation.
	 */

	public static Bitfield BitfieldAND(Bitfield b1, Bitfield b2) {
		return BitfieldOperation(b1, b2, AND);
	}

	public static Bitfield BitfieldOR(Bitfield b1, Bitfield b2) {
		return BitfieldOperation(b1, b2, OR);
	}

	public static Bitfield BitfieldXOR(Bitfield b1, Bitfield b2) {
		return BitfieldOperation(b1, b2, XOR);
	}

	public static Bitfield BitfieldNOT(Bitfield b) {
		return BitfieldOperation(b, b, NOT);
	}

	/**
	 * Takes in two bitfields and will execute the given
	 * BitwiseFunction on each byte of the two bitfields.
	 * b1 and b2 must be the same length, otherwise will return null
	 * @param  b1   A bitfield that will be used for computing the result
	 * @param  b2   Another bitfield that will be used for computing the result
	 * @param  func the BitwiseFunction to be executed on each byte
	 * @return      the result as a Bitfield
	 */
	protected static Bitfield BitfieldOperation(Bitfield b1, Bitfield b2, BitwiseFunction func) {
		Bitfield resultBitfield = null;
		if (b1.numBits == b2.numBits) {
			resultBitfield = new Bitfield(b1.numBits);

			for (int i = 0; i < b1.array.length; i++) {
				resultBitfield.array[i] = func.execute(b1.array[i], b2.array[i]);
			}
		}
		return resultBitfield;
	}

	/**
	 * It provides assurance that an execute(byte, byte)
	 * will be implemented by any class that implements
	 * this interface.
	 * Serves as a command interface following
	 * the command pattern in OOP.
	 */
	protected interface BitwiseFunction {
		/**
		 * Executes some function that takes in two
		 * bytes and returns a byte
		 * @param  a first byte passed
		 * @param  b sencond byte passed
		 * @return   resulting byte
		 */
		public byte execute(byte a, byte b);
	}

	/**
	 * The next four classes just will serve to implement
	 * the BitwiseFunctions that can be used.
	 * (Bitfield.AND, Bitfield.OR, Bitfield.XOR, Bitfield.NOT)
	 */
	
	private static final class And implements BitwiseFunction {
		public byte execute(byte a, byte b) {
			return (byte)(a & b);
		}
	}

	private static final class Or implements BitwiseFunction {
		public byte execute(byte a, byte b) {
			return (byte)(a | b);
		}
	}

	private static final class Xor implements BitwiseFunction {
		public byte execute(byte a, byte b) {
			return (byte)(a ^ b);
		}
	}

	private static final class Not implements BitwiseFunction {
		public byte execute(byte a, byte b) {
			return (byte)(~a);
		}
	}
}