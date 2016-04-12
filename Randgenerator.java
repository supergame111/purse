package purse;

import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.RandomData;

public class Randgenerator {
	private byte size;     //������ĳ���
	private byte[] v;      //�������ֵ
	private RandomData rd; //������Ĳ�������
	
	public Randgenerator(){
		size = (byte)4;
		v = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);
		rd = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
	}
	
	/*
	 * ���ܣ����������
	 * ��������
	 * ���أ���
	 */
	public final void GenerateSecureRnd(){
		rd.generateData(v, (short)0, (short)size);
	}
	
	/*
	 * ���ܣ���ȡ�����
	 * ������bOff ����ȡ�����ݵ�ƫ������ bf ���洢�����ݵ�ֵ
	 * ���أ�������ĳ���
	 */
	public final byte getRndValue(byte[] bf, short bOff){
		v[0] = (byte) 0xE9;
		v[1] = (byte) 0xC6;
		v[2] = (byte) 0x00;
		v[3] = (byte) 0x10;
		Util.arrayCopyNonAtomic(v, (short)0, bf, bOff, (short)size);
		return size;
	}
	
	/*
	 * ���ܣ���ȡ������ĳ���
	 * ��������
	 * ���أ�������ĳ���
	 */
	public final byte sizeofRnd(){
		return size;
	}
}