package purse;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

public class Purse extends Applet {
	//APDU Object
	private Papdu papdu;
	
	//文件系统
	private KeyFile keyfile;            //密钥文件
	private BinaryFile cardfile;       //应用基本文件
	private BinaryFile personfile;     //持卡人基本文件
	private EPFile EPfile;              //电子钱包文件
	
	public byte[] responseBuffer;      //返回的buffer
	
	public Purse(byte[] bArray, short bOffset, byte bLength){
		papdu = new Papdu();
		
		byte aidLen = bArray[bOffset];
		if(aidLen == (byte)0x00)
			register();
		else
			register(bArray, (short)(bOffset + 1), aidLen);
	}
	
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		new Purse(bArray, bOffset, bLength);
	}

	public void process(APDU apdu) {
		if (selectingApplet()) {
			return;
		}		
		//步骤1:取APDU缓冲区数组引用并将之赋给新建数组
		byte[] buffer = apdu.getBuffer();
		short lc = apdu.setIncomingAndReceive();
		
		//步骤2：取APDU缓冲区中数据放到变量papdu
		papdu.cla = buffer[ISO7816.OFFSET_CLA];
		papdu.ins = buffer[ISO7816.OFFSET_INS];
		papdu.p1  = buffer[ISO7816.OFFSET_P1];
		papdu.p2  = buffer[ISO7816.OFFSET_P2];
		papdu.lc  = buffer[ISO7816.OFFSET_LC];
		
		//步骤3：判断命令APDU是否包含数据段，有数据则获取数据长度，并对le赋值，否则，即不需要lc和data，则获取缓冲区原本lc实际上是le
		if(lc > (short)0){
			papdu.pdata = JCSystem.makeTransientByteArray(lc, JCSystem.CLEAR_ON_DESELECT);
			for( byte i = 0 ; i < lc ; i++){
				papdu.pdata[i] = buffer[ISO7816.OFFSET_CDATA+i];
			}
			papdu.le = buffer[ISO7816.OFFSET_CDATA+lc];
		}else{
			papdu.le = papdu.lc;
			papdu.lc = 0x0000 ;
		}
		boolean rc = handleEvent();
		
		//步骤4:判断是否需要返回数据，并设置apdu缓冲区	, rc为否时返回，responseBuffer装载数据
		if(!rc){
			//返回数据
			for(byte i = 0 ; i < responseBuffer.length; i++){
				buffer[i] = responseBuffer[i];
			}
			apdu.setOutgoingAndSend((short)0, (short)responseBuffer.length);
		}
		
	}

	/*
	 * 功能：对命令的分析和处理
	 * 参数：无
	 * 返回：是否成功处理了命令
	 */
	private boolean handleEvent(){
		switch(papdu.ins){
			case condef.INS_CREATE_FILE:       return create_file();  //创建文件
			case condef.INS_WRITE_KEY:         return write_key();    //写入key
			case condef.INS_WRITE_BIN:         return write_bin();       //写入二进制文件（card, person）
			case condef.INS_READ_BIN:          return read_bin();        //读取二进制文件,注意返回数据
			case condef.INS_NIIT_TRANS:        return init_trans();      //初始化交易
			case condef.INS_LOAD:              return load();            //圈存
		}	
		ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		return false;
	}
	
	/*
	 * 功能：初始化交易
	 */
	private boolean init_trans(){
		
		switch(papdu.p1){
			case 0x00:                         return init_load();       //初始化圈存
			case 0x01:                         return init_purchase();   //初始化交易
		}
		ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		return true; //
	}
	
	
	/*
	 * 功能：创建文件
	 */
	private boolean create_file() {
		switch(papdu.pdata[0]){             
		case condef.EP_FILE:           return EP_file();          //电子钱包文件
		case condef.CARD_FILE:         return Card_file();        //应用基本文件
		case condef.KEY_FILE:          return Key_file();         //密钥文件
		case condef.PERSON_FILE:       return Person_file();      //持卡人基本文件
		//todo:完成创建密钥文件，持卡人基本文件和应用基本文件
		default: 
			ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
		}
		return true;
	}
	/*
	 * 功能：创建电子钱包文件
	 */
	private boolean EP_file() {
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.lc != (byte)0x07)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		if(EPfile != null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		
		EPfile = new EPFile(keyfile);
		
		return true;
	}
	
	/*
	 * 功能：创建应用基本文件
	 */
	private boolean Card_file(){
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.lc != (byte)0x07)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		if(cardfile != null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		
		cardfile = new BinaryFile(papdu.pdata);
		
		return true;
	} 
	
	/*
	 * 功能：创建密钥文件
	 */
	private boolean Key_file(){
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		if(papdu.lc != (byte)0x07)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		if(keyfile != null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		
		keyfile = new KeyFile();
		
		return true;
	} 
	
	/*
	 * 功能：创建持卡人基本文件
	 */
	private boolean Person_file(){
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.lc != (byte)0x07)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		if(personfile != null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		
		personfile = new BinaryFile(papdu.pdata);
		
		return true;
	} 
	
	
	/*
	 *功能：写入密钥 
	 */
	private boolean write_key(){
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(keyfile == null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		
		if(papdu.lc!=papdu.pdata.length)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		
		keyfile.addkey(papdu.p2, papdu.lc, papdu.pdata);
		
		return true;
	}
	
	/*
	 * 功能：写入二进制文件
	 */
	private boolean write_bin(){
		if(papdu.p1 == 0x17){
			//持卡人基本文件
			personfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);
			
		}else if(papdu.p1 == 0x16){
			//应用基本文件
			cardfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);
			
		}else{
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		}
		
		return true;
	}
	
	/*
	 * 功能：读取二进制文件
	 */
	private boolean read_bin(){
		if(papdu.p1 == 0x17){
			//持卡人基本文件
			responseBuffer = JCSystem.makeTransientByteArray(papdu.le, JCSystem.CLEAR_ON_DESELECT);
			personfile.read_binary(papdu.p2, papdu.le, responseBuffer);
			
		}else if(papdu.p1 == 0x16){
			//应用基本文件
			responseBuffer = JCSystem.makeTransientByteArray(papdu.le, JCSystem.CLEAR_ON_DESELECT);
			cardfile.read_binary(papdu.p2, papdu.le, responseBuffer);
			
		}else{
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		}
		
		return false;
	}
	
	
	/*
	 * 功能：圈存命令的实现
	 */
	private boolean load() {
		short rc;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x00)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		responseBuffer = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);
		
		rc = EPfile.load(papdu.pdata);
		
		for(byte i = 0 ; i < 4; i++ ){
			responseBuffer[i] = papdu.pdata[i];
		}
		
		if(rc == 1)
			ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
		else if(rc == 2)
			ISOException.throwIt(condef.SW_LOAD_FULL);
		else if(rc == 3)
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		papdu.le = (short)4;
		
		return true;
	}

	/*
	 * 功能：圈存初始化命令的实现
	 */
	private boolean init_load() {
		short num,rc;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x02)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		num = keyfile.findkey(papdu.pdata[0]);
		
		if(num == 0x00)
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		
		responseBuffer = JCSystem.makeTransientByteArray((short)16, JCSystem.CLEAR_ON_DESELECT);
		for(byte i=0; i < papdu.pdata.length;i++){
			responseBuffer[i] = papdu.pdata[i];
		}
		
		rc = EPfile.init4load(num, responseBuffer); 
		//超额
		if(rc == 2)
			ISOException.throwIt((condef.SW_LOAD_FULL));
		
		
		papdu.le = (short)0x10;
		
		return false;
	}
		/*
	 * 功能：消费命令的实现
	 */
	private boolean purchase(){
		return true;
	}
	/*
	 * 功能：余额查询功能的实现
	 */
	private boolean get_balance(){
		return true;
	}
	
	/*
	 * 功能：消费初始化的实现
	 */
	private boolean init_purchase(){
		return true;
	}
}
