/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.program.model.data;

import static ghidra.program.model.pcode.AttributeId.*;
import static ghidra.program.model.pcode.ElementId.*;

import java.io.IOException;
import java.util.*;

import ghidra.program.model.lang.Language;
import ghidra.program.model.pcode.Encoder;
import ghidra.util.exception.NoValueException;
import ghidra.util.xml.SpecXmlUtils;
import ghidra.xml.XmlElement;
import ghidra.xml.XmlPullParser;

/**
 * DataOrganization provides a single place for determining size and alignment information 
 * for data types within an archive or a program.
 */
public class DataOrganizationImpl implements DataOrganization {

	private int absoluteMaxAlignment = NO_MAXIMUM_ALIGNMENT;
	private int machineAlignment = 8;
	private int defaultAlignment = 1;
	private int defaultPointerAlignment = 4;

	// Default sizes for primitive data types.
	private int pointerShift = 0;
	private int pointerSize = 4;
	private int charSize = 1;
	private int wideCharSize = 2;
	private int shortSize = 2;
	private int integerSize = 4;
	private int longSize = 4;
	private int longLongSize = 8;
	private int floatSize = 4;
	private int doubleSize = 8;
	private int longDoubleSize = 8;

	private boolean bigEndian = false;
	private boolean isSignedChar = true;

	private BitFieldPackingImpl bitFieldPacking = new BitFieldPackingImpl();

	/*
	 * Map for determining the alignment of a data type based upon its size.
	 */
	private final HashMap<Integer, Integer> sizeAlignmentMap = new HashMap<>();

	/**
	 * Creates a new default DataOrganization. This has a mapping which defines the alignment
	 * of a data type based on its size. The map defines pairs for data types that are
	 * 1, 2, 4, and 8 bytes in length.
	 * @return a new default DataOrganization.
	 */
	public static DataOrganization getDefaultOrganization() {
		return getDefaultOrganization(null);
	}

	/**
	 * Creates a new default DataOrganization. This has a mapping which defines the alignment
	 * of a data type based on its size. The map defines pairs for data types that are
	 * 1, 2, 4, and 8 bytes in length.
	 * @param language optional language used to initialize defaults (pointer size, endianess, etc.) (may be null)
	 * @return a new default DataOrganization.
	 */
	public static DataOrganizationImpl getDefaultOrganization(Language language) {
		DataOrganizationImpl dataOrganization = new DataOrganizationImpl();
		dataOrganization.setSizeAlignment(1, 1);
		dataOrganization.setSizeAlignment(2, 2);
		dataOrganization.setSizeAlignment(4, 4);
		dataOrganization.setSizeAlignment(8, 4);
		if (language != null) {
			dataOrganization.setPointerSize(language.getDefaultSpace().getPointerSize());
			dataOrganization.setBigEndian(language.isBigEndian());
		}
		return dataOrganization;
	}

	/**
	 * Creates a new default DataOrganization with an empty size to alignment mapping.
	 */
	private DataOrganizationImpl() {
	}

	@Override
	public boolean isBigEndian() {
		return bigEndian;
	}

	@Override
	public int getPointerSize() {
		return pointerSize;
	}

	@Override
	public int getPointerShift() {
		return pointerShift;
	}

	@Override
	public boolean isSignedChar() {
		return isSignedChar;
	}

	@Override
	public int getCharSize() {
		return charSize;
	}

	@Override
	public int getWideCharSize() {
		return wideCharSize;
	}

	@Override
	public int getShortSize() {
		return shortSize;
	}

	@Override
	public int getIntegerSize() {
		return integerSize;
	}

	@Override
	public int getLongSize() {
		return longSize;
	}

	@Override
	public int getLongLongSize() {
		return longLongSize;
	}

	@Override
	public int getFloatSize() {
		return floatSize;
	}

	@Override
	public int getDoubleSize() {
		return doubleSize;
	}

	@Override
	public int getLongDoubleSize() {
		return longDoubleSize;
	}

	@Override
	public BitFieldPacking getBitFieldPacking() {
		return bitFieldPacking;
	}

	/**
	 * Set data endianess
	 * @param bigEndian true if big-endian, false if little-endian
	 */
	public void setBigEndian(boolean bigEndian) {
		this.bigEndian = bigEndian;
	}

	/**
	 * Defines the size of a pointer data type.
	 * @param pointerSize the size of a pointer.
	 */
	public void setPointerSize(int pointerSize) {
		this.pointerSize = pointerSize;
	}

	/**
	 * Defines the left shift amount for a shifted pointer data type.
	 * Shift amount affects interpretation of in-memory pointer values only
	 * and will also be reflected within instruction pcode.
	 * @param pointerShift left shift amount for in-memory pointer values
	 */
	public void setPointerShift(int pointerShift) {
		this.pointerShift = pointerShift;
	}

	/**
	 * Defines the signed-ness of the "char" data type
	 * @param signed true if "char" type is signed
	 */
	public void setCharIsSigned(boolean signed) {
		this.isSignedChar = signed;
	}

	/**
	 * Defines the size of a char (char) data type.
	 * @param charSize the size of a char (char).
	 */
	public void setCharSize(int charSize) {
		this.charSize = charSize;
	}

	/**
	 * Defines the size of a wide-char (wchar_t) data type.
	 * @param wideCharSize the size of a wide-char (wchar_t).
	 */
	public void setWideCharSize(int wideCharSize) {
		this.wideCharSize = wideCharSize;
	}

	/**
	 * Defines the size of a short primitive data type.
	 * @param shortSize the size of a short.
	 */
	public void setShortSize(int shortSize) {
		this.shortSize = shortSize;
		if (integerSize < shortSize) {
			setIntegerSize(shortSize);
		}
	}

	/**
	 * Defines the size of an int primitive data type.
	 * @param integerSize the size of an int.
	 */
	public void setIntegerSize(int integerSize) {
		this.integerSize = integerSize;
		if (longSize < integerSize) {
			setLongSize(integerSize);
		}
		if (shortSize > integerSize) {
			setShortSize(integerSize);
		}
	}

	/**
	 * Defines the size of a long primitive data type.
	 * @param longSize the size of a long.
	 */
	public void setLongSize(int longSize) {
		this.longSize = longSize;
		if (longLongSize < longSize) {
			setLongLongSize(longSize);
		}
		if (integerSize > longSize) {
			setIntegerSize(longSize);
		}
	}

	/**
	 * Defines the size of a long long primitive data type.
	 * @param longLongSize the size of a long long.
	 */
	public void setLongLongSize(int longLongSize) {
		this.longLongSize = longLongSize;
		if (longSize > longLongSize) {
			setLongSize(longLongSize);
		}
	}

	/**
	 * Defines the size of a float primitive data type.
	 * @param floatSize the size of a float.
	 */
	public void setFloatSize(int floatSize) {
		this.floatSize = floatSize;
		if (doubleSize < floatSize) {
			setDoubleSize(floatSize);
		}
	}

	/**
	 * Defines the size of a double primitive data type.
	 * @param doubleSize the size of a double.
	 */
	public void setDoubleSize(int doubleSize) {
		this.doubleSize = doubleSize;
		if (longDoubleSize < doubleSize) {
			setLongDoubleSize(doubleSize);
		}
		if (floatSize > doubleSize) {
			setFloatSize(doubleSize);
		}
	}

	/**
	 * Defines the size of a long double primitive data type.
	 * @param longDoubleSize the size of a long double.
	 */
	public void setLongDoubleSize(int longDoubleSize) {
		this.longDoubleSize = longDoubleSize;
		if (doubleSize > longDoubleSize) {
			setDoubleSize(longDoubleSize);
		}
	}

	// DEFAULT ALIGNMENTS

	/**
	 * Gets the maximum alignment value that is allowed by this data organization. When getting
	 * an alignment for any data type it will not exceed this value. If NO_MAXIMUM_ALIGNMENT
	 * is returned, the data organization isn't specifically limited.
	 * @return the absolute maximum alignment or NO_MAXIMUM_ALIGNMENT
	 */
	@Override
	public int getAbsoluteMaxAlignment() {
		return absoluteMaxAlignment;
	}

	/**
	 * Gets the maximum useful alignment for the target machine
	 * @return the machine alignment
	 */
	@Override
	public int getMachineAlignment() {
		return machineAlignment;
	}

	/**
	 * Gets the default alignment to be used for any data type that isn't a 
	 * structure, union, array, pointer, type definition, and whose size isn't in the 
	 * size/alignment map.
	 * @return the default alignment to be used if no other alignment can be 
	 * determined for a data type.
	 */
	@Override
	public int getDefaultAlignment() {
		return defaultAlignment;
	}

	/**
	 * Gets the default alignment to be used for a pointer that doesn't have size.
	 * @return the default alignment for a pointer
	 */
	@Override
	public int getDefaultPointerAlignment() {
		return defaultPointerAlignment;
	}

	/**
	 * Sets the maximum alignment value that is allowed by this data organization. When getting
	 * an alignment for any data type it will not exceed this value. If NO_MAXIMUM_ALIGNMENT
	 * is returned, the data organization isn't specifically limited.
	 * @param absoluteMaxAlignment the absolute maximum alignment or NO_MAXIMUM_ALIGNMENT
	 */
	public void setAbsoluteMaxAlignment(int absoluteMaxAlignment) {
		this.absoluteMaxAlignment = absoluteMaxAlignment;
	}

	/**
	 * Sets the maximum useful alignment for the target machine
	 * @param machineAlignment the machine alignment
	 */
	public void setMachineAlignment(int machineAlignment) {
		this.machineAlignment = machineAlignment;
	}

	/**
	 * Sets the default alignment to be used for any data type that isn't a 
	 * structure, union, array, pointer, type definition, and whose size isn't in the 
	 * size/alignment map.
	 * @param defaultAlignment the default alignment to be used if no other alignment can be 
	 * determined for a data type.
	 */
	public void setDefaultAlignment(int defaultAlignment) {
		this.defaultAlignment = defaultAlignment;
	}

	/**
	 * Sets the default alignment to be used for a pointer that doesn't have size.
	 * @param defaultPointerAlignment the default alignment for a pointer
	 */
	public void setDefaultPointerAlignment(int defaultPointerAlignment) {
		this.defaultPointerAlignment = defaultPointerAlignment;
	}

	/**
	 * Gets the alignment that is defined for a data type of the indicated size if one is defined.
	 * @param size the size of the data type
	 * @return the alignment of the data type.
	 * @throws NoValueException if there isn't an alignment defined for the indicated size.
	 */
	@Override
	public int getSizeAlignment(int size) throws NoValueException {
		return sizeAlignmentMap.get(size);
	}

	/**
	 * Sets the alignment that is defined for a data type of the indicated size if one is defined.
	 * @param size the size of the data type
	 * @param alignment the alignment of the data type.
	 */
	public void setSizeAlignment(int size, int alignment) {
		sizeAlignmentMap.put(size, alignment);
	}

	/**
	 * Set the bitfield packing information associated with this data organization.
	 * @param bitFieldPacking bitfield packing information
	 */
	public void setBitFieldPacking(BitFieldPackingImpl bitFieldPacking) {
		this.bitFieldPacking = bitFieldPacking;
	}

	/**
	 * Remove all entries from the size alignment map
	 */
	public void clearSizeAlignmentMap() {
		sizeAlignmentMap.clear();
	}

	/**
	 * Gets the number of sizes that have an alignment specified.
	 * @return the number of sizes with an alignment mapped to them.
	 */
	@Override
	public int getSizeAlignmentCount() {
		return sizeAlignmentMap.size();
	}

	/**
	 * Gets the sizes that have an alignment specified.
	 * @return the sizes with alignments mapped to them.
	 */
	@Override
	public int[] getSizes() {
		Set<Integer> keySet = sizeAlignmentMap.keySet();
		int[] keys = new int[keySet.size()];
		int index = 0;
		for (Integer k : keySet) {
			keys[index++] = k;
		}
		Arrays.sort(keys);
		return keys;
	}

	/**
	 * Returns the best fitting integer C-type whose size is less-than-or-equal
	 * to the specified size.  "long long" will be returned for any size larger
	 * than "long long";
	 * @param size integer size
	 * @param signed if false the unsigned modifier will be prepended.
	 * @return the best fitting
	 */
	@Override
	public String getIntegerCTypeApproximation(int size, boolean signed) {
		String ctype = "long long";
		if (size <= 1) {
			ctype = "char";
		}
		else if (size <= getShortSize() && (getShortSize() != getIntegerSize())) {
			ctype = "short";
		}
		else if (size <= getIntegerSize()) {
			ctype = "int";
		}
		else if (size <= getLongSize()) {
			ctype = "long";
		}
		if (!signed) {
			ctype = "unsigned " + ctype;
		}
		return ctype;
	}

	@Override
	public int getAlignment(DataType dataType) {
		int dtSize = dataType.getLength();
		if (dataType instanceof Dynamic || dataType instanceof FactoryDataType || dtSize <= 0) {
			return 1;
		}
		// Typedef is aligned the same as its underlying data type is aligned.
		if (dataType instanceof TypeDef) {
			return getAlignment(((TypeDef) dataType).getBaseDataType());
		}
		// Array alignment is the alignment of its element data type.
		if (dataType instanceof Array) {
			DataType elementDt = ((Array) dataType).getDataType();
			return getAlignment(elementDt);
		}
		// Structure's or Union's alignment is a multiple of the least common multiple of
		// the components. It can also be adjusted by packing and alignment attributes.
		if (dataType instanceof Composite) {
			// IMPORTANT: composites are now responsible for computing their own alignment !!
			return ((Composite) dataType).getAlignment();
		}
		// Bit field alignment must be determined within the context of the containing structure.
		// See AlignedStructurePacker.
		if (dataType instanceof BitFieldDataType) {
			BitFieldDataType bitfieldDt = (BitFieldDataType) dataType;
			return getAlignment(bitfieldDt.getBaseDataType());
		}
		// Otherwise get the alignment based on the size.
		if (sizeAlignmentMap.containsKey(dtSize)) {
			int sizeAlignment = sizeAlignmentMap.get(dtSize);
			return ((absoluteMaxAlignment == 0) || (sizeAlignment < absoluteMaxAlignment))
					? sizeAlignment
					: absoluteMaxAlignment;
		}
		if (dataType instanceof Pointer) {
			return getDefaultPointerAlignment();
		}
		// Otherwise just assume the default alignment.
		return getDefaultAlignment();
	}

	/**
	 * Determines the first offset that is equal to or greater than the minimum offset which 
	 * has the specified alignment.  If a non-positive alignment is specified the origina
	 * minimumOffset will be return.
	 * @param alignment the desired alignment (positive value)
	 * @param minimumOffset the minimum offset
	 * @return the aligned offset
	 */
	public static int getAlignedOffset(int alignment, int minimumOffset) {
		if (alignment <= 0) {
			return minimumOffset;
		}
		if ((alignment & 1) == 0) {
			// handle alignment which is a power-of-2
			return alignment + ((minimumOffset - 1) & ~(alignment - 1));
		}
		int offcut = (minimumOffset % alignment);
		int adj = (offcut != 0) ? (alignment - offcut) : 0;
		return minimumOffset + adj;
	}

	/**
	 * Determines the least (lowest) common multiple of two numbers.
	 * @param value1 the first number
	 * @param value2 the second number
	 * @return the least common multiple
	 */
	public static int getLeastCommonMultiple(int value1, int value2) {
		int gcd = getGreatestCommonDenominator(value1, value2);
		return (gcd != 0) ? ((value1 / gcd) * value2) : 0;
	}

	/**
	 * Determines the greatest common denominator of two numbers.
	 * @param value1 the first number
	 * @param value2 the second number
	 * @return the greatest common denominator
	 */
	public static int getGreatestCommonDenominator(int value1, int value2) {
		return (value2 != 0) ? getGreatestCommonDenominator(value2, value1 % value2) : value1;
	}

	public void encode(Encoder encoder) throws IOException {
		encoder.openElement(ELEM_DATA_ORGANIZATION);
		if (absoluteMaxAlignment != NO_MAXIMUM_ALIGNMENT) {
			encoder.openElement(ELEM_ABSOLUTE_MAX_ALIGNMENT);
			encoder.writeSignedInteger(ATTRIB_VALUE, absoluteMaxAlignment);
			encoder.closeElement(ELEM_ABSOLUTE_MAX_ALIGNMENT);
		}
		if (machineAlignment != 8) {
			encoder.openElement(ELEM_MACHINE_ALIGNMENT);
			encoder.writeSignedInteger(ATTRIB_VALUE, machineAlignment);
			encoder.closeElement(ELEM_MACHINE_ALIGNMENT);
		}
		if (defaultAlignment != 1) {
			encoder.openElement(ELEM_DEFAULT_ALIGNMENT);
			encoder.writeSignedInteger(ATTRIB_VALUE, defaultAlignment);
			encoder.closeElement(ELEM_DEFAULT_ALIGNMENT);
		}
		if (defaultPointerAlignment != 4) {
			encoder.openElement(ELEM_DEFAULT_POINTER_ALIGNMENT);
			encoder.writeSignedInteger(ATTRIB_VALUE, defaultPointerAlignment);
			encoder.closeElement(ELEM_DEFAULT_POINTER_ALIGNMENT);
		}
		if (pointerSize != 0) {
			encoder.openElement(ELEM_POINTER_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, pointerSize);
			encoder.closeElement(ELEM_POINTER_SIZE);
		}
		if (pointerShift != 0) {
			encoder.openElement(ELEM_POINTER_SHIFT);
			encoder.writeSignedInteger(ATTRIB_VALUE, pointerShift);
			encoder.closeElement(ELEM_POINTER_SHIFT);
		}
		if (!isSignedChar) {
			encoder.openElement(ELEM_CHAR_TYPE);
			encoder.writeBool(ATTRIB_SIGNED, false);
			encoder.closeElement(ELEM_CHAR_TYPE);
		}
		if (charSize != 1) {
			encoder.openElement(ELEM_CHAR_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, charSize);
			encoder.closeElement(ELEM_CHAR_SIZE);
		}
		if (wideCharSize != 2) {
			encoder.openElement(ELEM_WCHAR_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, wideCharSize);
			encoder.closeElement(ELEM_WCHAR_SIZE);
		}
		if (shortSize != 2) {
			encoder.openElement(ELEM_SHORT_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, shortSize);
			encoder.closeElement(ELEM_SHORT_SIZE);
		}
		if (integerSize != 4) {
			encoder.openElement(ELEM_INTEGER_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, integerSize);
			encoder.closeElement(ELEM_INTEGER_SIZE);
		}
		if (longSize != 4) {
			encoder.openElement(ELEM_LONG_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, longSize);
			encoder.closeElement(ELEM_LONG_SIZE);
		}
		if (longLongSize != 8) {
			encoder.openElement(ELEM_LONG_LONG_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, longLongSize);
			encoder.closeElement(ELEM_LONG_LONG_SIZE);
		}
		if (floatSize != 4) {
			encoder.openElement(ELEM_FLOAT_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, floatSize);
			encoder.closeElement(ELEM_FLOAT_SIZE);
		}
		if (doubleSize != 8) {
			encoder.openElement(ELEM_DOUBLE_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, doubleSize);
			encoder.closeElement(ELEM_DOUBLE_SIZE);
		}
		if (longDoubleSize != 8) {
			encoder.openElement(ELEM_LONG_DOUBLE_SIZE);
			encoder.writeSignedInteger(ATTRIB_VALUE, longDoubleSize);
			encoder.closeElement(ELEM_LONG_DOUBLE_SIZE);
		}
		if (sizeAlignmentMap.size() != 0) {
			encoder.openElement(ELEM_SIZE_ALIGNMENT_MAP);
			for (int key : sizeAlignmentMap.keySet()) {
				encoder.openElement(ELEM_ENTRY);
				int value = sizeAlignmentMap.get(key);
				encoder.writeSignedInteger(ATTRIB_SIZE, key);
				encoder.writeSignedInteger(ATTRIB_ALIGNMENT, value);
				encoder.closeElement(ELEM_ENTRY);
			}
			encoder.closeElement(ELEM_SIZE_ALIGNMENT_MAP);
		}
		bitFieldPacking.encode(encoder);
		encoder.closeElement(ELEM_DATA_ORGANIZATION);
	}

	/**
	 * Restore settings from an XML stream. This expects to see a \<data_organization> tag.
	 * The XML is designed to override existing default settings. So this object needs to
	 * be pre-populated with defaults, typically via getDefaultOrganization().
	 * @param parser is the XML stream
	 */
	public void restoreXml(XmlPullParser parser) {
		parser.start();
		while (parser.peek().isStart()) {
			String name = parser.peek().getName();

			if (name.equals("char_type")) {
				XmlElement subel = parser.start();
				String boolStr = subel.getAttribute("signed");
				isSignedChar = SpecXmlUtils.decodeBoolean(boolStr);
				parser.end(subel);
				continue;
			}
			else if (name.equals("bitfield_packing")) {
				bitFieldPacking.restoreXml(parser);
				continue;
			}
			else if (name.equals("size_alignment_map")) {
				XmlElement subel = parser.start();
				while (parser.peek().isStart()) {
					XmlElement subsubel = parser.start();
					int size = SpecXmlUtils.decodeInt(subsubel.getAttribute("size"));
					int alignment = SpecXmlUtils.decodeInt(subsubel.getAttribute("alignment"));
					sizeAlignmentMap.put(size, alignment);
					parser.end(subsubel);
				}
				parser.end(subel);
				continue;
			}

			XmlElement subel = parser.start();
			String value = subel.getAttribute("value");

			if (name.equals("absolute_max_alignment")) {
				absoluteMaxAlignment = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("machine_alignment")) {
				machineAlignment = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("default_alignment")) {
				defaultAlignment = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("default_pointer_alignment")) {
				defaultPointerAlignment = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("pointer_size")) {
				pointerSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("pointer_shift")) {
				pointerShift = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("char_size")) {
				charSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("wchar_size")) {
				wideCharSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("short_size")) {
				shortSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("integer_size")) {
				integerSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("long_size")) {
				longSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("long_long_size")) {
				longLongSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("float_size")) {
				floatSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("double_size")) {
				doubleSize = SpecXmlUtils.decodeInt(value);
			}
			else if (name.equals("long_double_size")) {
				longDoubleSize = SpecXmlUtils.decodeInt(value);
			}
			parser.end(subel);
		}

		parser.end();

	}
}
