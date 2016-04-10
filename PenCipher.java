package purse;

import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.DESKey;
import javacard.security.Key;
import javacard.security.KeyBuilder;
import javacardx.crypto.Cipher;

public class PenCipher {
	private Cipher desEngine;
	private Key deskey;
	
	public PenCipher(){
		desEngine = Cipher.getInstance(Cipher.ALG_DES_CBC_NOPAD, false);
		deskey = KeyBuilder.buildKey(KeyBuilder.TYPE_DES, KeyBuilder.LENGTH_DES, false);
	}
	
	/*
	 * ���ܣ�DES����
	 * ������key ��Կ; kOff ��Կ��ƫ����; data ��Ҫ���мӽ��ܵ�����; dOff ����ƫ������ dLen ���ݵĳ���; r �ӽ��ܺ�����ݻ������� rOff �������ƫ������ mode ���ܻ��������ģʽ
	 * ���أ���
	 */
	public final void cdes(byte[] akey, short kOff, byte[] data, short dOff, short dLen, byte[] r, short rOff, byte mode){
		//���ãģţ���Կ
		((DESKey)deskey).setKey(akey, kOff);
		//��ʼ����Կ������ģʽ
		desEngine.init(deskey, mode);
		//����
		desEngine.doFinal(data, dOff, dLen, r, rOff);
	}
	
	/*
	 * ���ܣ����ɹ�����Կ
	 * ������key ��Կ�� data ��Ҫ���ܵ����ݣ� dOff �����ܵ�����ƫ������ dLen �����ܵ����ݳ��ȣ� r ���ܺ�����ݣ� rOff ���ܺ�����ݴ洢ƫ����
	 * ���أ���
	 */
	public final void gen_SESPK(byte[] key, byte[]data, short dOff, short dLen, byte[] r, short rOff){
		//3DES
		byte[] r1 = JCSystem.makeTransientByteArray(dLen, JCSystem.CLEAR_ON_DESELECT);
		byte[] r2 = JCSystem.makeTransientByteArray(dLen, JCSystem.CLEAR_ON_DESELECT);
		cdes(key,(short)0,data,dOff,dLen,r1,rOff,Cipher.MODE_ENCRYPT);
		cdes(key,(short)8,r1,dOff,dLen,r2,rOff,Cipher.MODE_DECRYPT);
		cdes(key,(short)0,r2,dOff,dLen,r,rOff,Cipher.MODE_ENCRYPT);
		
	}
	
	/*
	 * ���ܣ�8���ֽڵ�������
	 * ������d1 ����������������1 d2:����������������2 d2_off:����2��ƫ����
	 * ���أ���
	 */
	public final void xorblock8(byte[] d1, byte[] d2, short d2_off){
		//todo: �������ݿ�������������������ݿ�d1��
		for(short i = 0 ; i < 8 ; i ++){
			d1[i] = (byte)(d1[i]^d2[d2_off+i]);
		}
	}
	
	/*
	 * ���ܣ��ֽ����
	 * ������data ��Ҫ�������ݣ� len ���ݵĳ���
	 * ���أ�������ֽڳ���
	 */
	public final short pbocpadding(byte[] data, short len){
		//todo: ����ַ�����8�ı���
		short new_len = (short)(len + 1);
		while(new_len%8==0){
			new_len ++ ;
		}
		byte[] new_data = JCSystem.makeTransientByteArray(new_len, JCSystem.CLEAR_ON_DESELECT);
		for(short i = 0 ; i < len ; i++){
			new_data[i] = data[i];
		};
		new_data[len] = (byte)0x80;
		
		for(short i = (short)(len + 1) ; i < new_len ; i++){
			new_data[i] = (byte)0x00;
		}
		
		data = new_data ;
		len = new_len ;
		return len;
	}
	
	/*
	 * ���ܣ�MAC��TAC������
	 * ������key ��Կ; data ��Ҫ���ܵ�����; dl ��Ҫ���ܵ����ݳ��ȣ� mac ������õ���MAC��TAC��
	 * ���أ���
	 */
	public final void gmac4(byte[] key, byte[] data, short dl, byte[] mac){
		//todo
		//����䣬������
		pbocpadding(data, dl);
		byte[] vi = JCSystem.makeTransientByteArray((short)8, JCSystem.CLEAR_ON_DESELECT);
		for(byte i = 0 ; i < 8 ; i++){
			//��ʼֵ
			vi[i] = 0x00;
		}
		byte[] vi1 = JCSystem.makeTransientByteArray((short)8, JCSystem.CLEAR_ON_DESELECT);
		xorblock8(vi, data, (short)0);
		for(short i = 8 ; i < dl ; i = (short)(i + 8)){
			//des����
			cdes(vi,(short)0, data, i, (short)8, vi1, (short)0, Cipher.MODE_ENCRYPT);
			//copy
			Util.arrayCopyNonAtomic(vi1, (short)0, vi, (short)0, (short)8);
			//���
			xorblock8(vi, data, i);
		}
		Util.arrayCopyNonAtomic(vi, (short)0, mac, (short)0, (short)8);
	}
}
