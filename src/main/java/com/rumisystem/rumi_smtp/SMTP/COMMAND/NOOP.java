package com.rumisystem.rumi_smtp.SMTP.COMMAND;

import com.rumisystem.rumi_smtp.MODULE.BW_WRITEER;

public class NOOP {
	public static void Main(BW_WRITEER BWW) {
		BWW.SEND("250 NOOOOOOOOOOP");
	}
}
