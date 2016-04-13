# purse
A JavaCard Applet, course project that electronic purse
Purse.java is the applet class and the process() function will be callback when  APDUs come.
The APDU is instrument constructed like 
   
   ||  CLA  || INS ||  P1  ||  P2  ||  LC || DATA || LE  ||
 
 that CLA, INS, P1, P2 are necessary part and others are optional.
 
 CLA, the instrument class define by developer;
 INS, the certain instrument.
 P1, P2, parameters.
 LC, coming data length.
 DATA, data the terminal sends.
 LE, response data length.
 
 
 EPFile.java,  the purse data class, which store EP.
 
 Randgenerator.java,  generate random data.
 
 Binary.java, bin files, the card, owner, key information
