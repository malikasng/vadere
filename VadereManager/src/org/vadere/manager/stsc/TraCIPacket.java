package org.vadere.manager.stsc;

import org.vadere.manager.stsc.commands.TraCICmd;

import java.nio.ByteBuffer;

public class TraCIPacket extends Packet {

	private TraCIWriterImpl data;
	private boolean containsLengthField;


	public static TraCIPacket createDynamicPacket(){
		return new TraCIPacket().setLength(-1);
	}

	public static TraCIPacket createFixedPacket(int len){
		return new TraCIPacket().setLength(len);
	}

	private TraCIPacket() {
		data = new TraCIWriterImpl();
	}



	public TraCIPacket reset(){
		data.rest();
		return this;
	}

	private TraCIPacket setLength(int len){
		data.writeInt(len);
		containsLengthField = len != -1;
		return this;
	}

	public boolean isContainsLengthField() {
		return containsLengthField;
	}

	@Override
	public byte[] send() {

		if (containsLengthField)
			return data.asByteArray();


		ByteBuffer packet = data.asByteBuffer();
		// write final message length field at start of packet.
		packet.putInt(packet.capacity());

		return packet.array();
	}

	public byte[] extractCommandsOnly(){
		byte[] ret = new byte[data.size()-4];
		ByteBuffer buffer = data.asByteBuffer();
		buffer.position(4);
		buffer.get(ret, 0, ret.length);
		return ret;
	}

	public TraCIPacket addCommand(ByteBuffer buf){
		data.writeBytes(buf);
		return this;
	}

	public TraCIPacket addCommand(byte[] buf){
		data.writeBytes(buf);
		return this;
	}

	public TraCIPacket add_Err_StatusResponse(int cmdIdentifier, String description){
		return addStatusResponse(cmdIdentifier, TraCIStatusResponse.ERR, description);
	}

	public TraCIPacket add_OK_StatusResponse(TraCICmd traCICmd){
		return add_OK_StatusResponse(traCICmd.id);
	}

	public TraCIPacket add_OK_StatusResponse(int cmdIdentifier){
		// simple OK Status without description.
		data.writeUnsignedByte(7);
		data.writeUnsignedByte(cmdIdentifier);
		data.writeUnsignedByte(TraCIStatusResponse.OK.code);
		data.writeInt(0);
		return this;
	}

	public TraCIPacket addStatusResponse(int cmdIdentifier, TraCIStatusResponse response, String description){
		// expect single byte cmdLenField.
		// cmdLenField + cmdIdentifier + cmdResult + strLen + str
		// 1 + 1 + 1 + 4 + len(strBytes)
		int cmdLen = 7 + data.stringByteCount(description);

		data.writeCommandLength(cmdLen); // 1b
		data.writeUnsignedByte(cmdIdentifier); // 1b
		data.writeUnsignedByte(response.code); // 4b
		data.writeString(description); // 4b + X

		return this;
	}

	public TraCIWriter getWriter(){
		return (TraCIWriter)data;
	}

	public int size(){
		return data.size();
	}

}