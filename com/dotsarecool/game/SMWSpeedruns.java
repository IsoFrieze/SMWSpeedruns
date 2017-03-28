package com.dotsarecool.game;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
	
	// api strings
	public static String
		API_SRC_RECENTLY_VERIFIED = "http://www.speedrun.com/api/v1/runs?status=verified&orderby=verify-date&direction=desc&game=",
		API_SRC_USER = "http://www.speedrun.com/api/v1/users/",
		API_SRC_CATEGORY = "http://www.speedrun.com/api/v1/categories/",
		API_SRC_RUN = "http://www.speedrun.com/api/v1/runs/",
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
	
	// other global containers/objects
	public static List<String> pending, done, races;
	public static Map<String,String> users, categories, racers;
	public static Twitter twitter;
	public static Random random;
	public static SimpleDateFormat sdf;
	
	public static void main(String[] args) {
		// initialize objects
		pending = new ArrayList<>();
		done = new ArrayList<>();
		races = new ArrayList<>();
		users = new HashMap<>();
		categories = new HashMap<>();
		racers = new HashMap<>();
		random = new Random();
		sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm:ss");
		
		// initialize twitter
		initTwitter();

		Util.log("++-- SMW Speedruns Bot --++");
		Util.log("|| Version 1.2           ||");
		Util.log("|| By @Dotsarecool       ||");
		Util.log("++-----------------------++");
		Util.log("");

		// populate done queues with currently verified runs
		checkForRuns(false, GAME_SMW);
		checkForRuns(false, GAME_SMWEXT);
		
		// main loop
		while (true) {
			// check for verified runs every once in a while
			for (int j = 0; j < CHECK_FOR_RUNS_FREQUENCY; j++) {
				Util.waitTime(1000 * SECONDS_BETWEEN_TWEETS / CHECK_FOR_RUNS_FREQUENCY / 2);
				checkForRuns(true, GAME_SMW);
				Util.waitTime(1000 * SECONDS_BETWEEN_TWEETS / CHECK_FOR_RUNS_FREQUENCY / 2);
				checkForRuns(true, GAME_SMWEXT);
			}
			
			// try to make a tweet
			announceOne();
		}
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
	
	// call the api url and convert it all to a string
	public static String apiCall(String url) throws IOException {
		API_CALLS++;
		return IOUtils.toString(new URL(url), (String)null);
	}
	
	// actually tweet out the string s to the twitter
	public static Status tweet(String s) throws Exception {
		//// THIS IS THE LINE THAT ACTUALLY MAKES THE TWEET
		Status status = twitter.updateStatus(s);
		//// THIS IS THE LINE THAT ACTUALLY MAKES THE TWEET
		Util.log("-->");
		Util.log(String.format("--> %s", s));
		Util.log("-->");
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
				Util.log("No SMW races ongoing at the moment.");
				return false;
			}
			
			// get useful data from the api call
			int racerCount = smwrace.getInt("numentrants");
			String raceStatus = smwrace.getString("statetext");
			String raceId = smwrace.getString("id");
			
			// only tweet it out if there are 3+ racers, it is in progress, and we haven't tweeted it already
			if (racerCount > 2 && raceStatus.equals("In Progress") && !races.contains(raceId)) {
				Util.log(String.format("A SMW race was found: %s", raceId));
				String tweet = createRaceTweet(smwrace);
				tweet(tweet);				
				races.add(raceId);
				return true;
			} else {
				Util.log("A SMW race was found, but it wasn't tweeted out.");
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	// check the SRC api for any runs that were recently verified
	public static boolean checkForRuns(boolean pend, String gameId) {
		Util.log(String.format("Checking for %s verified runs...", pend ? "newly" : "already"));
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
						
						if (pb) {
							pending.add(runId);
						} else {
							done.add(runId);
						}
					}
				}
			}
			
			Util.log(String.format("Found %d %s verified runs.", count, pend ? "newly" : "already"));
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
		Util.log(String.format("Run verified by <%s>", getSRCPlayer(verifiedBy)));
		
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
}