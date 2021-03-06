package purse;

import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

public class EPFile {
	private KeyFile keyfile;
	
	//内部数据元
	private byte[] EP_balance;         //电子钱包余额
	private byte[] EP_offline;         //电子钱包联机交易序号
	private byte[] EP_online;          //电子钱包脱机交易序号
	
	byte keyID;        //密钥版本号
	byte algID;        //算法标识符
	
	//安全系统设计
	private Randgenerator RandData;          //随机数产生
	private PenCipher EnCipher;              //数据加解密方式实现
/**
 * 下面数据是计算时需要用到的临时过程数据	
 */
	//临时计算数据
	//4个字节的临时计算数据
	private byte[] pTemp41;           
	private byte[] pTemp42;
	
	//8个字节的临时计算数据
	private byte[] pTemp81;
	private byte[] pTemp82;
	
	//32个字节的临时计算数据
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
	 * 功能：电子钱包金额的增加
	 * 参数：data 所增加的金额  
	 * 	   flag 是否真正增加电子钱包余额
	 * 返回：圈存后，余额是否超过最大限额
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
	 * 功能：圈存初始化功能完成
	 * 参数：num 密钥记录号， data 命令报文中的数据段
	 * 返回：0： 圈存初始化命令执行成功     2：圈存超过电子钱包最大限额
	 */
	public final short init4load(short num, byte[] data){
		short length,rc;
		
		Util.arrayCopyNonAtomic(data, (short)1, pTemp42, (short)0, (short)4);  //交易金额
		Util.arrayCopyNonAtomic(data, (short)5, pTemp81, (short)0, (short)6);  //终端机编号
		
		//判断是否超额圈存
		rc = increase(pTemp42, false);
		if(rc != (short)0)
			return (short)2;
		
		
		//密钥获取
		length = keyfile.readkey(num, pTemp32);
		keyID = pTemp32[3];
		algID = pTemp32[4];
		Util.arrayCopyNonAtomic(pTemp32, (short)5, pTemp16, (short)0, length);
		
		//产生随机数
		RandData.GenerateSecureRnd();
		RandData.getRndValue(pTemp32, (short)0);
		
		
		//产生过程密钥
		Util.arrayCopyNonAtomic(EP_online, (short)0, pTemp32, (short)4, (short)2);
		pTemp32[6] = (byte)0x80;
		pTemp32[7] = (byte)0x00;
		
		EnCipher.gen_SESPK(pTemp16, pTemp32, (short)0, (short)8, pTemp82, (short)0);
		
		//产生MAC1
		Util.arrayCopyNonAtomic(EP_balance, (short)0, pTemp32, (short)0, (short)4);   //电子钱包余额
		Util.arrayCopyNonAtomic(data, (short)1, pTemp32, (short)4, (short)4);         //交易金额
		pTemp32[8] = (byte)0x02;                                                      //交易类型标识
		Util.arrayCopyNonAtomic(data, (short)5, pTemp32, (short)9, (short)6);         //终端机编号
		Util.arrayCopyNonAtomic(pTemp32, (short)0, data, (short)0x00, (short)0x0F);
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x0F, pTemp41);
		
		//响应数据
		Util.arrayCopyNonAtomic(EP_balance, (short)0, data, (short)0, (short)4);      //电子钱包余额
		Util.arrayCopyNonAtomic(EP_online, (short)0, data,  (short)4, (short)2);      //电子钱包联机交易序号
		data[6] = keyID;                                                              //密钥版本号
		data[7] = algID;                                                              //算法标识
		RandData.getRndValue(data, (short)8);                                         //随机数
		Util.arrayCopyNonAtomic(pTemp41, (short)0, data, (short)12, (short)4);        //mac1
		
		return 0;
	}
	
	/*
	 * 功能：圈存功能的完成
	 * 参数：data 命令报文中的数据段
	 * 返回：0 圈存命令执行成功；1 MAC2校验错误；  2 圈存超过最大限额; 3 密钥未找到
	 */
	public final short load(byte[] data){
		short rc;
		
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)0, (short)4);       //交易金额
		pTemp32[4] = (byte)0x02;                                                       //交易标识
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)5, (short)6);       //终端机编号
		Util.arrayCopyNonAtomic(data, (short)0, pTemp32, (short)11, (short)7);         //交易日期与时间
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x12, pTemp41);
		
		//检验MAC2
		if(Util.arrayCompare(data, (short)7, pTemp41, (short)0, (short)4) != (byte)0x00)
			return (short)1;
		
		//电子钱包数目增加
		rc = increase(pTemp42, true);
		if(rc != (short)0)
			return 2;
		
		//TAC数据
		Util.arrayCopyNonAtomic(EP_balance, (short)0, pTemp32, (short)0, (short)4);    //电子钱包余额
		Util.arrayCopyNonAtomic(EP_online, (short)0, pTemp32, (short)4, (short)2);     //电子钱包联机交易序号
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)6, (short)4);       //交易金额
		pTemp32[10] = (byte)0x02;                                                      //交易类型
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)11, (short)6);      //终端机编号
		Util.arrayCopyNonAtomic(data, (short)0, pTemp32, (short)17, (short)7);         //交易日期与时间
		
		//联机交易序号加1
		rc = Util.makeShort(EP_online[0], EP_online[1]);
		rc ++;
		if(rc > (short)256)
			rc = (short)1;
		Util.setShort(EP_online, (short)0, rc);
		
		//TAC的计算
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
	 * 功能：电子钱包金额减少
	 * 参数：data 消费的金额； flag 是否真正扣减电子钱包余额
	 * 返回： 消费是否超额
	 */
	public final short decrease(byte[] data, boolean flag){
		short i, t1, t2, ads;
		
		ads = (short)0;
		for(i = 3; i >= 0; i --){
			t1 = (short)(EP_balance[(short)i] & 0xFF);
			t2 = (short)(data[i] & 0xFF);
			if(flag){
				if(t1 > t2){
					EP_balance[i] = (byte)(t1 - ads - t2);
					ads = 0 ;
				}else if(t1 == t2 && ads == 0){
					EP_balance[i] = 0 ;
				}else if(t1 == t2 && ads != 0){
					EP_balance[i] = (byte)255 ;
				}else if(t1 < t2){
					EP_balance[i] = (byte)(t1 + 255 - t2 - ads);
					ads = 1 ;
				}
			}else{
				if(t1 > t2){
					ads = 0 ;
				}else if(t1 < t2){
					ads = 1 ;
				}
			}
		}
		return ads;
	}
		
	/*
	 * 功能：消费初始化命令的完成
	 * 参数：num 密钥记录号； data 命令报文中的数据段
	 * 返回：0 命令执行成功；2 消费超额
	 */
	public final short init4purchase(short num, byte[] data){
		short length,rc;
		
		Util.arrayCopyNonAtomic(data, (short)1, pTemp42, (short)0, (short)4);  //交易金额
		Util.arrayCopyNonAtomic(data, (short)5, pTemp81, (short)0, (short)6);  //终端机编号
		
		//判断是否超额圈存
		rc = decrease(pTemp42, false);
		if(rc != (short)0)
			return (short)2;
		
		//获取密钥信息
		length = keyfile.readkey(num, pTemp32);
		keyID = pTemp32[3];
		algID = pTemp32[4];
		Util.arrayCopyNonAtomic(pTemp32, (short)5, pTemp16, (short)0, length);
		//产生随机数
		RandData.GenerateSecureRnd();
		
		//响应数据
		Util.arrayCopyNonAtomic(EP_balance, (short)0, data, (short)0, (short)4);      //电子钱包余额
		Util.arrayCopyNonAtomic(EP_online, (short)0, data,  (short)4, (short)2);      //电子钱包联机交易序号
		
		data[6] = 0x00;
		data[7] = 0x00;
	    data[8] = 0x00;   //透支限额
	    
		data[9] = keyID;                                                              //密钥版本号
		data[10] = algID;                                                              //算法标识
		RandData.getRndValue(data, (short)11);                                         //随机数
		return 0;
		
		
	}
	/*
	 * 功能：消费命令的实现
	 * 参数：data 命令报文中的数据段
	 * 返回：0 命令执行成功； 1 MAC校验错误 2 消费超额； 3 密钥未找到
	 */
	public final short purchase(byte[] data){
		short rc;
		
		RandData.getRndValue(pTemp32, (short)0);
		
		//产生过程密钥
		Util.arrayCopyNonAtomic(EP_online, (short)0, pTemp32, (short)4, (short)2);
		pTemp32[6] = (byte)data[2];
		pTemp32[7] = (byte)data[3];
		EnCipher.gen_SESPK(pTemp16, pTemp32, (short)0, (short)8, pTemp82, (short)0); 
		
		
		//pTemp82过程密钥，pTemp32数据，pTemp41MAC1
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)0, (short)4);       //交易金额
		pTemp32[4] = (byte)0x06;                                                       //交易标识
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)5, (short)6);       //终端机编号
		Util.arrayCopyNonAtomic(data, (short)4, pTemp32, (short)11, (short)7);         //交易日期与时间
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x0F, pTemp41);
		
		
		//检验MAC1
		if(Util.arrayCompare(data, (short)11, pTemp41, (short)0, (short)4) != (byte)0x00)
			return (short)1;
		
		//电子钱包数目减少
		
		rc = decrease(pTemp42, true);
		if(rc != (short)0)
			return 2;
		
		//脱机交易序号加1
		rc = Util.makeShort(EP_offline[0], EP_offline[1]);
		rc ++;
		if(rc > (short)256)
			rc = (short)1;
		Util.setShort(EP_offline, (short)0, rc);
				
		//TAC数据
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)0, (short)4);       //交易金额
		pTemp32[4] = (byte)0x06;                                                       //交易类型
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)5, (short)6);       //终端机编号
		Util.arrayCopyNonAtomic(data, (short)0, pTemp32, (short)11, (short)11);         //终端交易序号，交易日期与时间
				
		//生成MAC2
		//由于pTemp42填充会溢出，将交易金额放在更大的数组中
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp81, (short)0, (short)4);
		EnCipher.gmac4(pTemp82, pTemp81, (short)4, pTemp41);
		Util.arrayCopyNonAtomic(pTemp41, (short)0, data, (short)4, (short)4);
				
		//TAC的计算
		short length, num;
		num = keyfile.findKeyByType((byte)0x34);
		length = keyfile.readkey(num, pTemp16);
				
		if(length == 0)
			return (short)3;
				
		Util.arrayCopyNonAtomic(pTemp16, (short)13, pTemp82, (short)0, (short)8);
		Util.arrayCopyNonAtomic(pTemp16, (short)5, pTemp81, (short)0, (short)8);
				
		EnCipher.xorblock8(pTemp82, pTemp81, (short)0);
				
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x16, data);
				
		return (short)0;
	}
	/*
	 * 功能：电子钱包余额获取
	 * 参数：data 电子钱包余额的缓冲区
	 * 返回： 0
	 */
	public final short get_balance(byte[] data){
		for(byte i = 0 ; i < 4 ; i++){
			data[i] = EP_balance[i];
		}
		return 0;
	}
}
