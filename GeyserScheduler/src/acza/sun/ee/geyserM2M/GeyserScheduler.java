/* --------------------------------------------------------------------------------------------------------
 * DATE:	06 Jul 2015
 * AUTHOR:	Cloete A.H
 * PROJECT:	M-Eng, Inteligent geyser M2M system.	
 * ---------------------------------------------------------------------------------------------------------
 * DESCRIPTION: 
 * ---------------------------------------------------------------------------------------------------------
 * PURPOSE: 
 * ---------------------------------------------------------------------------------------------------------
 */

package acza.sun.ee.geyserM2M;


import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.om2m.commons.utils.XmlMapper;


public class GeyserScheduler {

	private static final Logger logger = LogManager.getLogger(GeyserScheduler.class);
	private static SCLapi nscl;
	//private static Map<Long, int[]> geyser_schedule_map = new ConcurrentHashMap<Long, int[]>();//<geyser_ID, setpiont> 
	
	private static String app_ID = "Scheduler";
	
	private static long[] list_of_geysers = {1, 112};
	
	private static int[] DEFAULT_SCHEDULE = new int[96]; //96*15 minute intervals = 24 hours
	
	
	public static void main(String[] args) {
		
		// ---------------------- Sanity checking of command line arguments -------------------------------------------
		if( args.length != 1)
		{
			System.out.println( "Usage: <NSCL IP address>" ) ;
			return;
		}

		final String NSCL_IP_ADD = args[0];//"52.10.236.177";//"localhost";//
		if(!ipAddressValidator(NSCL_IP_ADD)){
			System.out.println( "IPv4 address invalid." ) ;
			return;
		}
		//-------------------------------------------------------------------------------------------------------
		
		logger.info("GeyserScheduler usage: <NSCL IP address>");
		logger.info("GeyserScheduler started with parameters: " + args[0]);
		
		
		//Initialise the default schedule
		Arrays.fill(DEFAULT_SCHEDULE, 35); //Set default temperature to 35.
		DEFAULT_SCHEDULE[20] = DEFAULT_SCHEDULE[21] = DEFAULT_SCHEDULE[22] = DEFAULT_SCHEDULE[23] = 60; //5h-6h = 60 degrees
		DEFAULT_SCHEDULE[48] = DEFAULT_SCHEDULE[49] = 45; //12h-12h30 = 45 degrees.
		DEFAULT_SCHEDULE[64] = DEFAULT_SCHEDULE[65] = DEFAULT_SCHEDULE[66] = DEFAULT_SCHEDULE[67] = 55; //16h-17h = 50 degrees
		
		nscl = new SCLapi("nscl", NSCL_IP_ADD, "8080", "admin:admin");
		
		//If application not yet registered at NSCL, then register it.
		if(!nscl.applicationExists(app_ID)){
			//Register application
			nscl.registerApplication(app_ID);
		
			//Register schedule settings containers, and populate with initial content
			for (long geyser_id:list_of_geysers){
				nscl.createContainer(app_ID, "SCHEDULE_"+geyser_id);
				nscl.createContentInstance(app_ID, "SCHEDULE_"+geyser_id, String.valueOf(scheduleArraytoString(DEFAULT_SCHEDULE)));
			}
		}
		
		//Calculate current time
		Calendar rightNow = Calendar.getInstance();
		int hour = rightNow.get(Calendar.HOUR_OF_DAY);
		int minute = rightNow.get(Calendar.MINUTE);
		int current_timeslot = ((hour*60)+minute)/15;
		
		System.out.println("Current time: " + hour + ":" + minute + " = slot " + current_timeslot);
		
		
		for (long geyser_id:list_of_geysers){
			String schedule_str = nscl.retrieveLatestContent(app_ID, "SCHEDULE_"+geyser_id);
			
			try {
				int[] schedule_arr = scheduleStringtoArray(schedule_str);
				
				//Post new setpoint to NSCL
				nscl.createContentInstance("Setpointcontroller", "SETPOINT_"+geyser_id, String.valueOf(schedule_arr[current_timeslot]));
				logger.info("Setting geyser " + geyser_id + " to setpoint: " + schedule_arr[current_timeslot]);
				
			} catch (ParseException e) {
				logger.error("Corrupt SCHEDULE format for Geyser: " + geyser_id, e);
			}
		}
	}

	
	private static String scheduleArraytoString(int[] array){
		
		StringBuilder schedule_srt = new StringBuilder();
		
		for(int temperature : array){
			schedule_srt.append(temperature + ",");
		}
		schedule_srt.deleteCharAt(schedule_srt.length()-1);
		
		return schedule_srt.toString();
	}
	
	private static int[] scheduleStringtoArray(String schedule_str) throws ParseException{
		
		int[] array = new int[96];
		
		String[] schedule_str_arr = schedule_str.split(",");
		
		if(schedule_str_arr.length != 96)
			throw new ParseException("SCHEDULE length not 96", schedule_str_arr.length);
		
		for(int i=0; i < schedule_str_arr.length; i++){
			array[i] = Integer.parseInt(schedule_str_arr[i]);
		}
		
		return array;
	}
	
	
	private static boolean ipAddressValidator(final String ip_adr){
		
		if(ip_adr.equalsIgnoreCase("localhost"))
			return true;
		
		 Pattern adr_pattern = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$", Pattern.DOTALL);
		 Matcher matcher = adr_pattern.matcher(ip_adr);
		 return matcher.matches();
	}
}

