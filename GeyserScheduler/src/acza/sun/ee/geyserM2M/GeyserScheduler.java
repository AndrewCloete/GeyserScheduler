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
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Properties;


public class GeyserScheduler {

	private static final Logger logger = LogManager.getLogger(GeyserScheduler.class);
	private static SCLapi nscl;
	
	private static String NSCL_BASE_URL;
	private static String AUTH;
	
	private static String app_ID = "Scheduler";
	
	private static LinkedList<Long> list_of_geysers;
	
	private static int[] DEFAULT_SCHEDULE = new int[96]; //96*15 minute intervals = 24 hours
	
	
	public static void main(String[] args) {
		
		// ---------------------- Reading and sanity checking configuration parameters -------------------------------------------
		Properties configFile = new Properties();
		try {
			configFile.load(GeyserScheduler.class.getClassLoader().getResourceAsStream("config.properties"));
			
			NSCL_BASE_URL = configFile.getProperty("NSCL_BASE_URL");
			AUTH = configFile.getProperty("AUTH");		
			
			//Populate list of geysers to control from config file
			try{
			list_of_geysers = new LinkedList<Long>();
			String[] geyser_list = configFile.getProperty("GEYSER_LIST").split(",");
			for(int i = 0; i < geyser_list.length; i++){
				list_of_geysers.add(new Long(geyser_list[i]));
			}
			}catch(Exception e){
				logger.fatal("Error in parsing EWH list from config.properties. Please use CSV.", e);
				return;
			}
			System.out.println("GeyserScheduler started with parameters: " + configFile.toString());
			//logger.info("GeyserScheduler started with parameters: " + configFile.toString());
			
		} catch (IOException e) {
			logger.fatal("Error in configuration file \"config.properties\"", e);
			return;
		}
		//-------------------------------------------------------------------------------------------------------
		
		
		//Initialise the default schedule
		Arrays.fill(DEFAULT_SCHEDULE, 35); //Set default temperature to 35.
		DEFAULT_SCHEDULE[20] = DEFAULT_SCHEDULE[21] = DEFAULT_SCHEDULE[22] = DEFAULT_SCHEDULE[23] = 60; //5h-6h = 60 degrees
		DEFAULT_SCHEDULE[48] = DEFAULT_SCHEDULE[49] = 45; //12h-12h30 = 45 degrees.
		DEFAULT_SCHEDULE[64] = DEFAULT_SCHEDULE[65] = DEFAULT_SCHEDULE[66] = DEFAULT_SCHEDULE[67] = 55; //16h-17h = 50 degrees
		
		if(AUTH.equalsIgnoreCase("NONE"))
			nscl = new SCLapi(NSCL_BASE_URL);	//OpenMTC
		else
			nscl = new SCLapi(NSCL_BASE_URL, AUTH); //OM2M
		
		try{ //Catch-all 

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
					System.out.println("Posted a setpoint of " + schedule_arr[current_timeslot] + " to geyser_" + geyser_id);
					
				} catch (ParseException e) {
					logger.error("Corrupt SCHEDULE format for Geyser: " + geyser_id, e);
				}
			}

		}catch(Exception e){
			logger.fatal("Unexpexted exception. Check if NSCL is running.", e);
			return;
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
}

