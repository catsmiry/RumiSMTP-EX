package com.rumisystem.rumi_smtp.SMTP;

import static com.rumisystem.rumi_java_lib.LOG_PRINT.Main.LOG;
import static com.rumisystem.rumi_smtp.Main.CONFIG_DATA;

import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.*;

import com.rumisystem.rumi_java_lib.LOG_PRINT.LOG_TYPE;
import com.rumisystem.rumi_smtp.MODULE.BW_WRITEER;
import com.rumisystem.rumi_smtp.MODULE.MAILBOX;
import com.rumisystem.rumi_smtp.SMTP.COMMAND.EHLO;
import com.rumisystem.rumi_smtp.SMTP.COMMAND.NOOP;
import com.rumisystem.rumi_smtp.SMTP.COMMAND.VRFY;

public class SUBMISSION_SERVER {
	private static int PORT = 25;
	private static int MAX_SIZE = 0;

	public static void Main() throws Exception {
		PORT = CONFIG_DATA.get("SUBMISSION").asInt("PORT");
		MAX_SIZE = CONFIG_DATA.get("SMTP").asInt("MAX_SIZE");
		
		ServerSocket SS = new ServerSocket(PORT);

		List<String> CONNECTERE_IP = new ArrayList<String>();
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					while(true) {
						Socket SOCKET = SS.accept();
						String IP = SOCKET.getInetAddress().getHostAddress();
						LOG(LOG_TYPE.INFO, "SUBMISSION New SESSION! IP:" + IP);
						
						//既にそのIPで接続しているか？
						if (!CONNECTERE_IP.contains(IP)) {
							//接続していないので、そのまま追加
							CONNECTERE_IP.add(IP);

							//そのまま通信開始
							new Thread(new Runnable() {
								@Override
								public void run() {
									try {
										Socket SESSION = SOCKET;
										SSLSocket SSL_SESSION = null;
										BufferedReader BR = new BufferedReader(new InputStreamReader(SESSION.getInputStream()));
										PrintWriter BW = new PrintWriter(SESSION.getOutputStream(), true);
										BW_WRITEER BWW = new BW_WRITEER(BW, IP, "S");
										
										//最初のメッセージ
										BWW.SEND("220 rumiserver.com ESMTP RumiSMTP joukoso!");

										MAILBOX MB = null;
										String REMOTE_DOMAIN = null;
										String MAIL_FROM = null;
										List<String> MAIL_TO = new ArrayList<String>();
										String MAIL_TEXT = null;

										String LINE = "";
										while((LINE = BR.readLine()) != null) {
											String[] CMD = LINE.split(" ");

											LOG(LOG_TYPE.INFO, "S<-" + IP + "|" + LINE);

											switch(CMD[0]) {
												case "HELO":{
													if (CMD[1] != null) {
														REMOTE_DOMAIN = CMD[1];

														BWW.SEND("250 rumiserver.com kon nichi wa");
													} else {
														BWW.SEND("500 Domain ga naizo!");
													}
													break;
												}

												case "EHLO":{
													if (CMD[1] != null) {
														REMOTE_DOMAIN = CMD[1];

														EHLO.Main(BWW, REMOTE_DOMAIN, MAX_SIZE);
													} else {
														BWW.SEND("500 Domain ga naizo!");
													}
													break;
												}

												case "MAIL":{
													if (CMD[1].split(":")[1] != null) {
														String FROM = CMD[1].split(":")[1];
														
														//<>で囲われていない場合が有るらしいので
														if (FROM.startsWith("<") && FROM.endsWith(">")) {
															FROM = FROM.replace("<", "");
															FROM = FROM.replace(">", "");
															
															MAIL_FROM = FROM;
														} else {
															MAIL_FROM = FROM;
														}

														//OK
														BWW.SEND("250 OK!");
														break;
													} else {
														BWW.SEND("800 meeru adoresu ga okashii");
													}
												}

												case "RCPT":{
													String TO = CMD[1].split(":")[1];

													//<>で囲われていない場合が有るらしいので
													if (TO.startsWith("<") && TO.endsWith(">")) {
														Matcher MATCH = Pattern.compile("<[^>]+>(.*?)</[^>]+>").matcher(TO);
														TO = TO.replace("<", "");
														TO = TO.replace(">", "");

														MAIL_TO.add(TO);
													} else {
														MAIL_TO.add(TO);
													}

													//自分のドメインならメアドが有るかチェックしない
													if (!CONFIG_DATA.get("SMTP").asString("DOMAIN").contains(TO)) {
														//メアドがあるか？
														if (MAILBOX.VRFY(TO)) {
															//OK
															BWW.SEND("250 OK!");
														} else {
															BWW.SEND("550 meeru adoresu ga cukaenai");
														}
													} else {
														BWW.SEND("250 OK!");
													}
													break;
												}

												case "DATA":{
													if (MAIL_FROM != null && MAIL_TO.size() >= 0) {
														BWW.SEND("354 OK! meeru deeta wo okutte! owari wa <CRLF>.<CRLF> dajo!");
														StringBuilder SB = new StringBuilder();
														String LT = "";
														while((LT = BR.readLine()) != null) {
															//.だけなら終了
															if (!LT.equals(".")) {
																if (SB.length() >= MAX_SIZE) {
																	BWW.SEND("552 MAIL SIZE GA DEKAI! MAX HA" + MAX_SIZE + "DAJO!");
																	break;
																}

																SB.append(LT + "\n");
															} else {
																//データ内容を全てMAIL_TEXTに入れる
																MAIL_TEXT = SB.toString();
																BWW.SEND("250 OK! Okuttajo!");
																break;
															}
														}

														//メールのID
														String ID = UUID.randomUUID().toString();

														for(String TO:MAIL_TO) {
															try {
																//トレース情報
																String TREES_DATA = "Received:\n"
																		+ "FROM <" + REMOTE_DOMAIN + "> (<" + SESSION.getInetAddress().getHostAddress() + ">)\n"
																		+ "VIA TCP\n"
																		+ "WITH ESMTP\n"
																		+ "ID <" + ID + ">\n"
																		+ "FOR <" + TO + ">";

																//自分のドメインならメールボックスを開いてメールを保存する
																if (CONFIG_DATA.get("SMTP").asString("DOMAIN").contains(TO.split("@")[1])) {
																	//メールボックスを開く
																	MB = new MAILBOX(TO);
																	MB.MAIL_SAVE(ID, TREES_DATA + "\n" + MAIL_TEXT);
																} else {
																	//外部のメアド
																}
															} catch (Exception EX) {
																EX.printStackTrace();
															}
														}
													} else {
														BWW.SEND("500 MAIL ka RCPT wo tobashitana?");
														break;
													}
													break;
												}
												
												case "RSET":{
													MAIL_FROM = null;
													MAIL_TO.clear();
													MAIL_TEXT = null;
													
													BWW.SEND("250 OK! Zenkeshi");
													break;
												}

												case "VRFY":{
													VRFY.Main(BWW);
													break;
												}

												case "NOOP":{
													NOOP.Main(BWW);
													break;
												}

												case "QUIT":{
													BWW.SEND("221 Sajonara!");
													
													CONNECTERE_IP.remove(IP);

													if(SSL_SESSION == null) {
														SESSION.close();
													} else {
														SSL_SESSION.close();
													}
													return;
												}

												default:{
													BWW.SEND("502 sono komando nai!");
													break;
												}
											}
										}
									}catch (Exception EX) {
										EX.printStackTrace();
									}
								}
							}).start();
						} else {
							SOCKET.close();
						}
					}
				} catch (Exception EX) {
					EX.printStackTrace();
				}
			}
		}).start();
	}
}
