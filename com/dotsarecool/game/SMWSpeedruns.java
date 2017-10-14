package com.dotsarecool.game;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class SMWSpeedruns {
	// where to save the log file
	public static String LOG_FILE = "out.log";
	
	// whether to show detailed notes in the log file
	public static boolean details = false;
	
	// flag to interrupt the regular cycle and start over
	public static boolean restart = false;
	
	// api strings
	public static String
		API_SRC_RECENTLY_VERIFIED = "https://www.speedrun.com/api/v1/runs?status=verified&orderby=verify-date&direction=desc&game=",
		API_SRC_USER = "https://www.speedrun.com/api/v1/users/",
		API_SRC_CATEGORY = "https://www.speedrun.com/api/v1/categories/",
		API_SRC_RUN = "https://www.speedrun.com/api/v1/runs/",
		API_SRL_RACES = "https://api.speedrunslive.com/races",
		API_SRL_PLAYER = "https://api.speedrunslive.com/players/";
	
	// smw category/game ids
	public static String 
		GAME_SMW = "pd0wq31e",
		GAME_SMWEXT = "268n5y6p",
		RACE_SMW = "smw",
		RACE_SMWHACKS = "smwhacks";
	
	// number of total api calls, used to make sure we're not calling too often
	public static int API_CALLS = 0;
	
	// the frequency a tweet is composed
	public static int SECONDS_BETWEEN_TWEETS = 30 * 60;
	
	// the frequency newly verified runs are checked
	public static int CHECK_FOR_RUNS_FREQUENCY = 5;
	
	// the maximum age of a run to still be announced
	public static int RUN_MAX_AGE_SECONDS = 4 * 7 * 24 * 60 * 60;
	
	// other global containers/objects
	public static List<String> pending, done, races;
	public static Map<String,String> users, categories, racers;
	public static Twitter twitter;
	public static Random random;
	public static SimpleDateFormat sdf;
	
	// the system tray icon object
	final public static TrayIcon ti = new TrayIcon(new ImageIcon("img/icon.png").getImage());
	
	public static void main(String[] args) {
		for (String s : args) {
			if (s.equals("-d")) {
				details = true;
			} else {
				try (PrintWriter out = new PrintWriter(new FileWriter(new File(s), true))) {
					LOG_FILE = s;
				} catch (Exception e) { }
			}			
		}
		
		if (init() && start()) {
			// main loop
			while (true) {
				// check for verified runs every once in a while
				for (int j = 0; !restart && j < CHECK_FOR_RUNS_FREQUENCY; j++) {
					Util.waitTime(1000 * SECONDS_BETWEEN_TWEETS / CHECK_FOR_RUNS_FREQUENCY / 2);
					checkForRuns(true, GAME_SMW);
					Util.waitTime(1000 * SECONDS_BETWEEN_TWEETS / CHECK_FOR_RUNS_FREQUENCY / 2);
					checkForRuns(true, GAME_SMWEXT);
				}
				
				// try to make a tweet
				if (!restart) {
					announceOne();
				}
				
				restart = false;
			}
		}
		
		Util.log(false, "The program has exited. Check your internet connection.");
	}
	
	// initialize everything. return false if something goes wrong.
	public static boolean init() {
		// initialize objects
		pending = new ArrayList<>();
		done = new ArrayList<>();
		races = new ArrayList<>();
		users = new HashMap<>();
		categories = new HashMap<>();
		racers = new HashMap<>();
		random = new Random();
		sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm:ss");

		// initialize twitter, populate done queues with currently verified runs
		// if any of these fail, exit the program because it won't run properly
		return initTwitter();
	}
	
	// initialize all the twitter4j stuff
	public static boolean initTwitter() {
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true);
		cb.setOAuthConsumerKey(Keys.consumerKey);
		cb.setOAuthConsumerSecret(Keys.consumerSecret);
		cb.setOAuthAccessToken(Keys.accessToken);
		cb.setOAuthAccessTokenSecret(Keys.accessTokenSecret);
		TwitterFactory tf = new TwitterFactory(cb.build());
		twitter = tf.getInstance();
		return true;
	}
	
	// everything has been initialized and we are guaranteed to actually start the program
	public static boolean start() {
		Util.log(false, "++-- SMW Speedruns Bot --++");
		Util.log(false, "|| Version 1.3.3         ||");
		Util.log(false, "|| By @Dotsarecool       ||");
		Util.log(false, "++-----------------------++");
		Util.log(false, String.format("Logging to '%s'.", LOG_FILE));
		Util.log(false, "");
		
		// create a fancy system tray icon with exit option
		try {
			final SystemTray tray = SystemTray.getSystemTray();
			final PopupMenu pop = new PopupMenu();
			final MenuItem goNow = new MenuItem("Announce Next");
			goNow.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					restart = true;
					announceOne();
				}
			});
			final MenuItem ex = new MenuItem("Exit");
			ex.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (pending.size() > 0) {
						String msg = String.format("There are %s run(s) waiting to be Tweeted. Are you sure you want to exit?", pending.size());
						int r = JOptionPane.showConfirmDialog(null, msg, "Exit", JOptionPane.OK_CANCEL_OPTION);
						if (r != JOptionPane.YES_OPTION) {
							return;
						}
					}
					Util.log(false, "Exited.");
					tray.remove(ti);
					System.exit(0);
				}
			});
			ti.setToolTip("SMWSpeedruns");
			pop.add(goNow);
			pop.add(ex);
			ti.setPopupMenu(pop);
			tray.add(ti);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		checkForRuns(false, GAME_SMW);
		checkForRuns(false, GAME_SMWEXT);
		
		return true;
	}
	
	// call the api url and convert it all to a string
	public static String apiCall(String url) throws IOException {
		API_CALLS++;
		return IOUtils.toString(new URL(url), (String)null);
	}
	
	// actually tweet out the string s to the twitter
	public static Status tweet(String s) throws Exception {
		Status status = null;
		//// THIS IS THE LINE THAT ACTUALLY MAKES THE TWEET
		status = twitter.updateStatus(s);
		//// THIS IS THE LINE THAT ACTUALLY MAKES THE TWEET
		Util.log(true, "-->");
		Util.log(false, String.format("--> %s", s));
		Util.log(true, "-->");
		return status;
	}
	
	// try to tweet something, races are prioritized
	public static boolean announceOne() {
		if (!announceARace() && pending.size() > 0) {
			try {
				String id = pending.get(0);
				String tweet = createRunTweet(id);
				tweet(tweet);		
				pending.remove(0);
				done.add(id);
				updateTrayIcon();
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	// check if there are any races ongoing, if so build a tweet for it
	public static boolean announceARace() {
		try {
			// call the SRL api for list of races
			String json = apiCall(API_SRL_RACES);
			JSONObject o = new JSONObject(json);
			JSONArray racelist = o.getJSONArray("races");
			
			// run through them all and look for smw or smwhacks races
			JSONObject smwrace = null;
			for (int i = 0; i < racelist.length(); i++) {
				String gameAbbr = racelist.getJSONObject(i).getJSONObject("game").getString("abbrev");
				if (gameAbbr.equals(RACE_SMW) || gameAbbr.equals(RACE_SMWHACKS)) {
					smwrace = racelist.getJSONObject(i);
				}
			}
			
			// didn't find any :(
			if (smwrace == null) {
				Util.log(true, "No SMW races ongoing at the moment.");
				return false;
			}
			
			// get useful data from the api call
			int racerCount = smwrace.getInt("numentrants");
			String raceStatus = smwrace.getString("statetext");
			String raceId = smwrace.getString("id");
			
			// only tweet it out if there are 3+ racers, it is in progress, and we haven't tweeted it already
			if (racerCount > 2 && raceStatus.equals("In Progress") && !races.contains(raceId)) {
				Util.log(true, String.format("A SMW race was found: %s", raceId));
				String tweet = createRaceTweet(smwrace);
				tweet(tweet);				
				races.add(raceId);
				return true;
			} else {
				Util.log(true, "A SMW race was found, but it wasn't tweeted out.");
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	// check the SRC api for any runs that were recently verified
	public static boolean checkForRuns(boolean pend, String gameId) {
		Util.log(true, String.format("Checking for %s verified runs...", pend ? "newly" : "already"));
		int count = 0;
		try {
			// call the SRC api (gives the last 20 recently verified runs)
			String json = apiCall(API_SRC_RECENTLY_VERIFIED + gameId);
			JSONObject o = new JSONObject(json);
			JSONArray runs = o.getJSONArray("data");
			
			// run through all the runs
			for (int i = 0; i < runs.length(); i++) {
				JSONObject run = runs.getJSONObject(i);
				String runId = run.getString("id");
				
				// add to pending queue if we haven't seen it yet
				if (!pending.contains(runId) && !done.contains(runId)) {
					count++;
					
					if (!pend) {
						// on boot, dump everything into done queue first
						done.add(runId);
					} else {
						// check and see if this is the runner's pb, if not don't keep this run
						// only works for solo runs, and for runs with players with SRC accounts
						boolean pb;
						String player1Type = run.getJSONArray("players").getJSONObject(0).getString("rel");
						if (player1Type.equals("user")) {
							String player1Id = run.getJSONArray("players").getJSONObject(0).getString("id");
							pb = isAPersonalBest(runId, player1Id);
						} else {
							pb = true;
						}
						// check to see if this run is relatively new, if it is old don't announce it
						String runDate = run.getString("date");
						long secs = (System.currentTimeMillis() - new SimpleDateFormat("yyyy-MM-dd").parse(runDate).getTime()) / 1000;
						boolean old = secs > RUN_MAX_AGE_SECONDS;
						
						if (pb && !old) {
							pending.add(runId);
						} else {
							done.add(runId);
						}
					}
				}
			}
			
			Util.log(true, String.format("Found %d %s verified runs.", count, pend ? "newly" : "already"));
			updateTrayIcon();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	// build a tweet for an SRL race
	public static String createRaceTweet(JSONObject race) throws Exception {
		// get some useful info for the tweet
		String raceId = race.getString("id");
		String link = "http://www.speedrunslive.com/race/?id=" + raceId;
		String [] racerNames = Util.shuffle(JSONObject.getNames(race.getJSONObject("entrants")));
		for (int i = 0; i < racerNames.length && i < 3; i++) {
			racerNames[i] = getSRLPlayer(racerNames[i]);
		}
		
		// join all the names together, max of 3
		String names;
		if (racerNames.length <= 3) {
			names = Util.join(racerNames);
		} else {
			String others = String.format("%d others", racerNames.length - 2);
			String [] subset = new String[] { racerNames[0], racerNames[1], others };
			names = Util.join(subset);
		}
		
		// if its a rando race, rename the goal because rando goals are hella long
		String goal = race.getString("goal");
		if (goal.toLowerCase().contains("randomizer")) {
			goal = "SMW Randomizer";
		}
		
		// create from template
		String mainTweet = Util.raceTemplates[random.nextInt(Util.raceTemplates.length)]
			.replace("%racers%", names)
			.replace("%goal%", goal);
		
		if (mainTweet.length() + link.length() + 1 > 140) {
			return mainTweet;
		} else {
			return String.format("%s %s", mainTweet, link);
		}
	}
	
	// build a tweet for a submitted run
	public static String createRunTweet(String runId) throws Exception {
		// call the SRC api
		String json = apiCall(API_SRC_RUN + runId);
		JSONObject o = new JSONObject(json);
		JSONObject run = o.getJSONObject("data");
		
		// log who verified this run
		String verifiedBy = run.getJSONObject("status").getString("examiner");
		Util.log(false, String.format("Run verified by <%s>", getSRCPlayer(verifiedBy)));
		
		// get some useful info
		JSONArray players = run.getJSONArray("players");
		String [] playersNames = new String[players.length()];
		for (int i = 0; i < playersNames.length; i++) {
			JSONObject p = players.getJSONObject(i);
			playersNames[i] = p.getString("rel").equals("user") ? getSRCPlayer(p.getString("id")) : p.getString("name");
		}
		String playerNames = Util.join(playersNames);
		String cId = run.getString("category");
		String categoryName = getSRCCategory(cId);
		double runTime = run.getJSONObject("times").getDouble("realtime_t");
		
		// get the wr for this category so we can check if its a new wr
		json = apiCall(API_SRC_CATEGORY + cId + "/records");
		JSONObject wr = (new JSONObject(json)).getJSONArray("data").getJSONObject(0).getJSONArray("runs").getJSONObject(0).getJSONObject("run");
		double wrTime = wr.getJSONObject("times").getDouble("realtime_t");
		String prettyTime = Util.prettyTime(runTime, wrTime);
		
		// being picky
		String article = (prettyTime.indexOf("8:") == 0 || prettyTime.indexOf("11:") == 0 || prettyTime.indexOf("18:") == 0) ? "an" : "a";
		
		// get a link to the run's video if it exists, otherwise just link the SRC page
		String link = null;
		if (!run.isNull("videos") && !run.getJSONObject("videos").isNull("links") && run.getJSONObject("videos").getJSONArray("links").length() > 0) {
			link = run.getJSONObject("videos").getJSONArray("links").getJSONObject(0).getString("uri");
		} else {
			link = run.getString("weblink");
		}
		
		String [] templateType = runTime == wrTime ? Util.wrTemplates : Util.runTemplates;
		String mainTweet = templateType[random.nextInt(templateType.length)]
			.replace("%players%", playerNames)
			.replace("%category%", categoryName)
			.replace("%article%", article)
			.replace("%time%", prettyTime);
		
		if (link == null || mainTweet.length() + link.length() + 1 > 140) {
			return mainTweet;
		} else {
			return String.format("%s %s", mainTweet, link);
		}
	}
	
	// check if the run is a personal best for player
	public static boolean isAPersonalBest(String runId, String playerId) throws Exception {
		String json = apiCall(API_SRC_USER + playerId + "/personal-bests");
		JSONObject o = new JSONObject(json);
		JSONArray checkRuns = o.getJSONArray("data");
		for (int j = 0; j < checkRuns.length(); j++) {
			JSONObject r = checkRuns.getJSONObject(j).getJSONObject("run");
			String checkRunId = r.getString("id");
			if (checkRunId.equals(runId)) {
				return true;
			}
		}
		return false;
	}
	
	// given an SRC user id, get the user's twitter handle or user name
	public static String getSRCPlayer(String id) {
		// see if we already have a copy of it, to save api calls
		if (users.containsKey(id)) {
			return users.get(id);
		}
		try {
			// call the SRC api
			String json = apiCall(API_SRC_USER + id);
			JSONObject o = new JSONObject(json);
			JSONObject user = o.getJSONObject("data");
			
			// check if user has a twitter handle set
			String name;
			if (!user.isNull("twitter")) {
				name = "@" + user.getJSONObject("twitter").getString("uri").substring(24);
			} else {
				name = user.getJSONObject("names").getString("international");
			}
			
			// save this username for if we need it again later
			users.put(id, name);
			return name;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// given an SRC category id, get the category name
	public static String getSRCCategory(String id) {
		// see if we already have a copy of it, to save api calls
		if (categories.containsKey(id)) {
			return categories.get(id);
		}
		try {
			// call the SRC api
			String json = apiCall(API_SRC_CATEGORY + id);
			JSONObject o = new JSONObject(json);
			JSONObject category = o.getJSONObject("data");
			
			String name = category.getString("name");

			// save this category name for if we need it again later
			categories.put(id, name);
			return name;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// given an SRL user id, get the user's twitter handle or user name
	public static String getSRLPlayer(String racer) {
		// see if we already have a copy of it, to save api calls
		if (racers.containsKey(racer)) {
			return racers.get(racer);
		}
		try {
			// call the SRL api
			String json = apiCall(API_SRL_PLAYER + racer);
			JSONObject user = new JSONObject(json);
			
			// check if user has a twitter handle set
			String name = user.getString("twitter");
			if (name.length() > 0) {
				name = "@" + name;
			} else {
				name = racer;
			}
			
			// save this username for if we need it again later
			racers.put(racer, name);
			return name;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// update the tray icon to show how many runs are currently pending
	public static void updateTrayIcon() {
		BufferedImage bi = new BufferedImage(16, 16, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D g = (Graphics2D)bi.getGraphics();
		int runs = pending.size();

		g.drawImage(new ImageIcon("img/icon.png").getImage(), runs > 0 ? 1 : 0, 0, null);
		if (runs > 0) {
			g.drawImage(new ImageIcon("img/bubble.png").getImage(), 0, 0, null);
			g.setColor(new Color(200, 200, 255));
			g.setFont(new Font("Consolas", Font.PLAIN, 9));
			g.drawString(runs > 8 ? "+" : ("" + runs), 1, 7);
		}
		
		ti.setImage(bi);
	}
}