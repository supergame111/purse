package purse;

import javacard.framework.JCSystem;
import javacard.framework.Util;

public class EPFile {
	private KeyFile keyfile;
	
	//�ڲ�����Ԫ
	private byte[] EP_balance;         //����Ǯ�����
	private byte[] EP_offline;         //����Ǯ�������������
	private byte[] EP_online;          //����Ǯ���ѻ��������
	
	byte keyID;        //��Կ�汾��
	byte algID;        //�㷨��ʶ��
	
	//��ȫϵͳ���
	private Randgenerator RandData;          //���������
	private PenCipher EnCipher;              //���ݼӽ��ܷ�ʽʵ��
/**
 * ���������Ǽ���ʱ��Ҫ�õ�����ʱ��������	
 */
	//��ʱ��������
	//4���ֽڵ���ʱ��������
	private byte[] pTemp41;           
	private byte[] pTemp42;
	
	//8���ֽڵ���ʱ��������
	private byte[] pTemp81;
	private byte[] pTemp82;
	
	//32���ֽڵ���ʱ��������
	private byte[] pTemp16;
	private byte[] pTemp32;
	
	public EPFile(KeyFile keyfile){
		EP_balance = new byte[4];
		Util.arrayFillNonAtomic(EP_balance, (short)0, (short)4, (byte)0x00);
		
		EP_offline = new byte[2];
		Util.arrayFillNonAtomic(EP_offline, (short)0, (short)2, (byte)0x00);
		
		EP_online = new byte[2];
		Util.arrayFillNonAtomic(EP_online, (short)0, (short)2, (byte)0x00);
		
		this.keyfile = keyfile;
		
		pTemp41 = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);
		pTemp42 = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);
		
		pTemp81 = JCSystem.makeTransientByteArray((short)8, JCSystem.CLEAR_ON_DESELECT);
		pTemp82 = JCSystem.makeTransientByteArray((short)8, JCSystem.CLEAR_ON_DESELECT);
		
		pTemp16 = JCSystem.makeTransientByteArray((short)32, JCSystem.CLEAR_ON_DESELECT);
		pTemp32 = JCSystem.makeTransientByteArray((short)32, JCSystem.CLEAR_ON_DESELECT);
		
		RandData = new Randgenerator();
		EnCipher = new PenCipher();
	}
	
	/*
	 * ���ܣ�����Ǯ����������
	 * ������data �����ӵĽ��  
	 * 	   flag �Ƿ��������ӵ���Ǯ�����
	 * ���أ�Ȧ�������Ƿ񳬹�����޶�
	 */
	public final short increase(byte[] data, boolean flag){
		short i, t1, t2, ads;
		
		ads = (short)0;
		for(i = 3; i >= 0; i --){
			t1 = (short)(EP_balance[(short)i] & 0xFF);
			t2 = (short)(data[i] & 0xFF);
			
			t1 = (short)(t1 + t2 + ads);
			if(flag)
				EP_balance[(short)i] = (byte)(t1 % 256);
			ads = (short)(t1 / 256);
		}
		return ads;
	}
	
	/*
	 * ���ܣ�Ȧ���ʼ���������
	 * ������num ��Կ��¼�ţ� data ������е����ݶ�
	 * ���أ�0�� Ȧ���ʼ������ִ�гɹ�     2��Ȧ�泬������Ǯ������޶�
	 */
	public final short init4load(short num, byte[] data){
		short length,rc;
		
		Util.arrayCopyNonAtomic(data, (short)1, pTemp42, (short)0, (short)4);  //���׽��
		Util.arrayCopyNonAtomic(data, (short)5, pTemp81, (short)0, (short)6);  //�ն˻����
		
		//�ж��Ƿ񳬶�Ȧ��
		rc = increase(pTemp42, false);
		if(rc != (short)0)
			return (short)2;
		
		//��Կ��ȡ
		length = keyfile.readkey(num, pTemp32);
		keyID = pTemp32[3];
		algID = pTemp32[4];
		Util.arrayCopyNonAtomic(pTemp32, (short)5, pTemp16, (short)0, length);
		
		//���������
		RandData.GenerateSecureRnd();
		RandData.getRndValue(pTemp32, (short)0);
		
		//����������Կ
		Util.arrayCopyNonAtomic(EP_online, (short)0, pTemp32, (short)4, (short)2);
		pTemp32[6] = (byte)0x80;
		pTemp32[7] = (byte)0x00;
		
		EnCipher.gen_SESPK(pTemp16, pTemp32, (short)0, (short)8, pTemp82, (short)0); 
		
		//����MAC1
		Util.arrayCopyNonAtomic(EP_balance, (short)0, pTemp32, (short)0, (short)4);   //����Ǯ�����
		Util.arrayCopyNonAtomic(data, (short)1, pTemp32, (short)4, (short)4);         //���׽��
		pTemp32[8] = (byte)0x02;                                                      //�������ͱ�ʶ
		Util.arrayCopyNonAtomic(data, (short)5, pTemp32, (short)9, (short)6);         //�ն˻����
		Util.arrayCopyNonAtomic(pTemp32, (short)0, data, (short)0x00, (short)0x0F);
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x0F, pTemp41);
		
		//��Ӧ����
		Util.arrayCopyNonAtomic(EP_balance, (short)0, data, (short)0, (short)4);      //����Ǯ�����
		Util.arrayCopyNonAtomic(EP_online, (short)0, data,  (short)4, (short)2);      //����Ǯ�������������
		data[6] = keyID;                                                              //��Կ�汾��
		data[7] = algID;                                                              //�㷨��ʶ
		RandData.getRndValue(data, (short)8);                                         //�����
		Util.arrayCopyNonAtomic(pTemp41, (short)0, data, (short)12, (short)4);        //mac1
		
		return 0;
	}
	
	/*
	 * ���ܣ�Ȧ�湦�ܵ����
	 * ������data ������е����ݶ�
	 * ���أ�0 Ȧ������ִ�гɹ���1 MAC2У�����  2 Ȧ�泬������޶�; 3 ��Կδ�ҵ�
	 */
	public final short load(byte[] data){
		short rc;
		
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)0, (short)4);       //���׽��
		pTemp32[4] = (byte)0x02;                                                       //���ױ�ʶ
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)5, (short)6);       //�ն˻����
		Util.arrayCopyNonAtomic(data, (short)0, pTemp32, (short)11, (short)7);         //����������ʱ��
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x12, pTemp41);
		
		//����MAC2
		if(Util.arrayCompare(data, (short)7, pTemp41, (short)0, (short)4) != (byte)0x00)
			return (short)1;
		
		//����Ǯ����Ŀ����
		rc = increase(pTemp42, true);
		if(rc != (short)0)
			return 2;
		
		//TAC����
		Util.arrayCopyNonAtomic(EP_balance, (short)0, pTemp32, (short)0, (short)4);    //����Ǯ�����
		Util.arrayCopyNonAtomic(EP_online, (short)0, pTemp32, (short)4, (short)2);     //����Ǯ�������������
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)6, (short)4);       //���׽��
		pTemp32[10] = (byte)0x02;                                                      //��������
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)11, (short)6);      //�ն˻����
		Util.arrayCopyNonAtomic(data, (short)0, pTemp32, (short)17, (short)7);         //����������ʱ��
		
		//����������ż�1
		rc = Util.makeShort(EP_online[0], EP_online[1]);
		rc ++;
		if(rc > (short)256)
			rc = (short)1;
		Util.setShort(EP_online, (short)0, rc);
		
		//TAC�ļ���
		short length, num;
		num = keyfile.findKeyByType((byte)0x34);
		length = keyfile.readkey(num, pTemp16);
		
		if(length == 0)
			return (short)3;
		
		Util.arrayCopyNonAtomic(pTemp16, (short)5, pTemp82, (short)0, (short)8);
		
		EnCipher.xorblock8(pTemp82, pTemp16, (short)13);
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x18, data);
		
		return (short)0;
	}
		/*
	 * ���ܣ�����Ǯ��������
	 * ������data ���ѵĽ� flag �Ƿ������ۼ�����Ǯ�����
	 * ���أ� �����Ƿ񳬶�
	 */
	public final short decrease(byte[] data, boolean flag){
		return 0;
	}
		
	/*
	 * ���ܣ����ѳ�ʼ����������
	 * ������num ��Կ��¼�ţ� data ������е����ݶ�
	 * ���أ�0 ����ִ�гɹ���2 ���ѳ���
	 */
	public final short init4purchase(short num, byte[] data){
		return 0;
		
	}
	/*
	 * ���ܣ����������ʵ��
	 * ������data ������е����ݶ�
	 * ���أ�0 ����ִ�гɹ��� 1 MACУ����� 2 ���ѳ�� 3 ��Կδ�ҵ�
	 */
	public final short purchase(byte[] data){
		return 0;
	}
	/*
	 * ���ܣ�����Ǯ������ȡ
	 * ������data ����Ǯ�����Ļ�����
	 * ���أ� 0
	 */
	public final short get_balance(byte[] data){
		return 0;
	}
}
