package com.dotsarecool.game;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;

public class Util {	
	// wait a bit. also log some stuff
	public static void waitTime(long t) {
		log(true, String.format("Currently at %d API calls.", SMWSpeedruns.API_CALLS));
		log(true, String.format("pending: %d | done: %d | races: %d || users: %d | categories: %d | racers: %d",
				SMWSpeedruns.pending.size(), SMWSpeedruns.done.size(), SMWSpeedruns.races.size(),
				SMWSpeedruns.users.size(), SMWSpeedruns.categories.size(), SMWSpeedruns.racers.size()));
		//for (int i = 0; i < pending.size(); i++) {
		//	log(String.format("     - %s", pending.get(i).toString()));
		//}
		log(true, String.format("Waiting %d seconds...", t/1000));
		//log("...");
		try { Thread.sleep(t); } catch (Exception e) {}
	}
	
	// output to the log file
	public static boolean log(boolean detail, String s) {
		System.out.println(s);
		if (!(detail && !SMWSpeedruns.details)) {
			try (PrintWriter out = new PrintWriter(new FileWriter(new File(SMWSpeedruns.LOG_FILE), true))) {
				out.printf("[%23s] %s%n", SMWSpeedruns.sdf.format(new Date()), s);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	// output a time in a human-readable format
	// takes in the wr time so we can include decimals where needed
	public static String prettyTime(double time, double wr) {
		int sec = (int)time;
		int h = sec / 3600, m = (sec / 60) % 60, s = sec % 60, d = (int)(1000*(time - sec));
		String str = "";
		
		// don't include leading zeros
		if (time >= 3600) { // xx:xx:xx
			str = String.format("%2d:%02d:%02d", h, m, s);
		} else if (time >= 60) { // xx:xx
			str = String.format("%2d:%02d", m, s);
		} else { // xx
			str = String.format("%2d", s);
		}
		
		// only include decimals if the run is short or if the run is close to the wr
		boolean closeToWR = (time / wr) < 1.01;
		boolean shortRun = time < 2*60;
		boolean hasDecimals = time - sec > 0;
		if (hasDecimals && (closeToWR || shortRun)) { // .xxx
			str += String.format(".%03d", d);
		}
		return str.trim();
	}
	
	// join an array of strings together with commas and &s in a human-readable way
	public static String join(String [] a) {
		switch (a.length) {
			case 0: {
				return "";
			}
			case 1: {
				return a[0];
			}
			case 2: {
				return a[0] + " & " + a[1];
			}
			default: {
				String s = a[0];
				for (int i = 1; i < a.length; i++) {
					s += (i == a.length - 1 ? ", & " : ", ") + a[i];
				}
				return s;
			}
		}
	}
	
	// shuffle an array
	// this is probably not a good way to do it, but hey it doesn't really matter that much
	public static String [] shuffle(String [] a) {
		for (int i = 0; i < a.length; i++) {
			int r = SMWSpeedruns.random.nextInt(a.length), s = SMWSpeedruns.random.nextInt(a.length);
			String temp = a[r];
			a[r] = a[s];
			a[s] = temp;
		}
		return a;
	}
	
	// templates for announcing a run
	//   %players% - player name, or list of player names
	//   %article% - a/an depending on the leading digit of %time%
	//   %time% - pretty format of the run's time
	//   %category% - the category name
	public static String [] runTemplates = {
		"Congrats to %players% for %article% %time% in %category%!"
	};
	
	// templates for announcing a wr
	//   %players% - player name, or list of player names
	//   %article% - a/an depending on the leading digit of %time%
	//   %time% - pretty format of the run's time
	//   %category% - the category name
	public static String [] wrTemplates = {
		"Congratulations to %players% for the new %category% world record of %time%!"
	};
	
	// templates for announcing a race
	//   %racers% - list of racer names
	//   %goal% - the goal name
	public static String [] raceTemplates = {
		"%racers% are currently racing %goal%!",
		"A race of %goal% is taking place with racers %racers%!"
	};
}