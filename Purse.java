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
	
	//�ļ�ϵͳ
	private KeyFile keyfile;            //��Կ�ļ�
	private BinaryFile cardfile;       //Ӧ�û����ļ�
	private BinaryFile personfile;     //�ֿ��˻����ļ�
	private EPFile EPfile;              //����Ǯ���ļ�
	
	public byte[] responseBuffer;      //���ص�buffer
	
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
		//����1:ȡAPDU�������������ò���֮�����½�����
		byte[] buffer = apdu.getBuffer();
		short lc = apdu.setIncomingAndReceive();
		
		//����2��ȡAPDU�����������ݷŵ�����papdu
		papdu.cla = buffer[ISO7816.OFFSET_CLA];
		papdu.ins = buffer[ISO7816.OFFSET_INS];
		papdu.p1  = buffer[ISO7816.OFFSET_P1];
		papdu.p2  = buffer[ISO7816.OFFSET_P2];
		papdu.lc  = buffer[ISO7816.OFFSET_LC];
		
		//����3���ж�����APDU�Ƿ�������ݶΣ����������ȡ���ݳ��ȣ�����le��ֵ�����򣬼�����Ҫlc��data�����ȡ������ԭ��lcʵ������le
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
		
		//����4:�ж��Ƿ���Ҫ�������ݣ�������apdu������	, rcΪ��ʱ���أ�responseBufferװ������
		if(!rc){
			//��������
			for(byte i = 0 ; i < responseBuffer.length; i++){
				buffer[i] = responseBuffer[i];
			}
			apdu.setOutgoingAndSend((short)0, (short)responseBuffer.length);
		}
		
	}

	/*
	 * ���ܣ�������ķ����ʹ���
	 * ��������
	 * ���أ��Ƿ�ɹ�����������
	 */
	private boolean handleEvent(){
		switch(papdu.ins){
			case condef.INS_CREATE_FILE:       return create_file();  //�����ļ�
			case condef.INS_WRITE_KEY:         return write_key();    //д��key
			case condef.INS_WRITE_BIN:         return write_bin();       //д��������ļ���card, person��
			case condef.INS_READ_BIN:          return read_bin();        //��ȡ�������ļ�,ע�ⷵ������
			case condef.INS_NIIT_TRANS:        return init_trans();      //��ʼ������
			case condef.INS_LOAD:              return load();            //Ȧ��
		}	
		ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		return false;
	}
	
	/*
	 * ���ܣ���ʼ������
	 */
	private boolean init_trans(){
		
		switch(papdu.p1){
			case 0x00:                         return init_load();       //��ʼ��Ȧ��
			case 0x01:                         return init_purchase();   //��ʼ������
		}
		ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		return true; //
	}
	
	
	/*
	 * ���ܣ������ļ�
	 */
	private boolean create_file() {
		switch(papdu.pdata[0]){             
		case condef.EP_FILE:           return EP_file();          //����Ǯ���ļ�
		case condef.CARD_FILE:         return Card_file();        //Ӧ�û����ļ�
		case condef.KEY_FILE:          return Key_file();         //��Կ�ļ�
		case condef.PERSON_FILE:       return Person_file();      //�ֿ��˻����ļ�
		//todo:��ɴ�����Կ�ļ����ֿ��˻����ļ���Ӧ�û����ļ�
		default: 
			ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
		}
		return true;
	}
	/*
	 * ���ܣ���������Ǯ���ļ�
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
	 * ���ܣ�����Ӧ�û����ļ�
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
	 * ���ܣ�������Կ�ļ�
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
	 * ���ܣ������ֿ��˻����ļ�
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
	 *���ܣ�д����Կ 
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
	 * ���ܣ�д��������ļ�
	 */
	private boolean write_bin(){
		if(papdu.p1 == 0x17){
			//�ֿ��˻����ļ�
			personfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);
			
		}else if(papdu.p1 == 0x16){
			//Ӧ�û����ļ�
			cardfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);
			
		}else{
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		}
		
		return true;
	}
	
	/*
	 * ���ܣ���ȡ�������ļ�
	 */
	private boolean read_bin(){
		if(papdu.p1 == 0x17){
			//�ֿ��˻����ļ�
			responseBuffer = JCSystem.makeTransientByteArray(papdu.le, JCSystem.CLEAR_ON_DESELECT);
			personfile.read_binary(papdu.p2, papdu.le, responseBuffer);
			
		}else if(papdu.p1 == 0x16){
			//Ӧ�û����ļ�
			responseBuffer = JCSystem.makeTransientByteArray(papdu.le, JCSystem.CLEAR_ON_DESELECT);
			cardfile.read_binary(papdu.p2, papdu.le, responseBuffer);
			
		}else{
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		}
		
		return false;
	}
	
	
	/*
	 * ���ܣ�Ȧ�������ʵ��
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
	 * ���ܣ�Ȧ���ʼ�������ʵ��
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
		//����
		if(rc == 2)
			ISOException.throwIt((condef.SW_LOAD_FULL));
		
		
		papdu.le = (short)0x10;
		
		return false;
	}
		/*
	 * ���ܣ����������ʵ��
	 */
	private boolean purchase(){
		return true;
	}
	/*
	 * ���ܣ�����ѯ���ܵ�ʵ��
	 */
	private boolean get_balance(){
		return true;
	}
	
	/*
	 * ���ܣ����ѳ�ʼ����ʵ��
	 */
	private boolean init_purchase(){
		return true;
	}
}
