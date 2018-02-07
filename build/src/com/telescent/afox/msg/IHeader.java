package com.telescent.afox.msg;

public interface IHeader {

	public int getHeader();
	
	byte[] ToFlatSer();
	//     public static bool FromFlatSer(UnflatSerialize uf, out AFOXCmdMsg ci)
}
