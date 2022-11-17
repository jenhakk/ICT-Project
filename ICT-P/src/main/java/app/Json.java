package app;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.collect.Iterators;

public class Json {
	static String savedTranscript = "";

	public static Map<Integer, Object> getJson() {
		JSONParser parser = new JSONParser();
		try {
			// save JSON inside an Object
			Object data = parser.parse(new FileReader("src/main/java/app/incidentassesments.json"));
			// Iterator for going through data object
			Iterator<Object> iterator = ((ArrayList) data).iterator();
			// Iterator for calculating size
			Iterator<Object> iterator2 = ((ArrayList) data).iterator();
			// New hashmap for saving iterators objects
			Map<Integer, Object> listMap = new HashMap<Integer, Object>();
			// loop for calculating how many object is inside the iterator
			int iteratorSize = 0;
			while (iterator2.hasNext()) {
				iteratorSize++;
				iterator2.next();
			}

			while (iterator.hasNext()) {

				for (int i = 0; i < iteratorSize; i++) {
					listMap.put(i, iterator.next());
				}

			}
			// Going through the listMap hashmap and calling method handleHashmap for each
			// object inside this hashmap, number is
			// the number of incident assesment tree (integer) and incident is the
			// object/what is inside in every incident assesment tree

			return listMap;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private static JSONArray handleHashmapNegatives(Object incident, Integer number) {
		JSONObject jobj = (JSONObject) incident;
		String incidenttreenegative = "negative";
//		String incidenttreekeywords="keywords";

		JSONArray msg = (JSONArray) jobj.get(incidenttreenegative);
//		JSONArray msg2=(JSONArray) jobj.get(incidenttreekeywords);
//		System.err.println("Negative keywords "+number+": "+ msg);
//		System.err.println("Keywords "+number+": "+ msg2);

		return msg;
	}

	private static String[] handleHashmapPositives(Object incident, Integer number) {
		JSONObject jobj = (JSONObject) incident;
		String incidentname = "name";
		String incidenttreekeywords = "keywords";

		String incidentn = (String) jobj.get(incidentname);
		System.err.println(incidentn);
		// jos transcript on poiminut puuhun liittyviä-> eka if
		if (incidentn.equalsIgnoreCase("kaatunut puu")) {
			String[] treeKeys = (String[]) jobj.get(incidenttreekeywords);
			System.err.println("tuleeko täältä keyt puulle?  " + treeKeys);
			return treeKeys;
		} else {
			String[] shopLiftKeys = (String[]) jobj.get(incidenttreekeywords);
			System.err.println("tuleeko täältä keyt varastamiselle?  " + shopLiftKeys);
			return shopLiftKeys;
		}

//		System.err.println("Negative keywords "+number+": "+ msg);
//		System.err.println("Keywords "+number+": "+ msg2);

	}

	public static void dataFetch(String transcript) {
		System.err.println("Json-luokan transcript:");
		System.out.println("Transkripti: " + transcript);
		saveTranscriptToString(transcript);
		findKeywords(transcript);
	}

	public static void findKeywords(String transcript) {
		String incidenttreekeywords = "keywords";
		String incidenttreenegativekeywords = "negative";
		System.out.println("no modifications " + transcript);
		transcript = transcript.toLowerCase();
		Map<Integer, Object> listMap = new HashMap<Integer, Object>();
		System.out.println("lowerCaseTranscript? " + transcript);
		listMap = getJson();

		// Getting the incident assesment tree for tree falling to an object
		JSONObject valueForFirstKey = (JSONObject) listMap.get(0);
		System.err.println("RIVI 120 FIRSTKEYVALUE " + valueForFirstKey);
		
		// List contains JSONs keywords for falling tree
		List<String> keyListForTree = (List<String>) valueForFirstKey.get(incidenttreekeywords);
		// List contains JSONs negative words for tree falling
		List<String> keyNegativeListForTree = (List<String>) valueForFirstKey.get(incidenttreenegativekeywords);

		// Getting the incident assesment tree for shoplifting to an object
		Object valueForSecondKey = listMap.get(1);
		//Incident assesment tree for kaupparyöstö:
		JSONObject treeobj2 = (JSONObject) valueForSecondKey;
		// List contains JSONs keywords for shoplifting
		List<String> keyListForSL = (List<String>) treeobj2.get(incidenttreekeywords);
		// List contains JSONs negative words for shoplifting
		List<String> keyNegativeListForSL = (List<String>) treeobj2.get(incidenttreenegativekeywords);
		System.err.println("SECKEYVALUE " + valueForSecondKey);

		// Initializing list for matching words found from transcript
		ArrayList<String> foundTreeWords = new ArrayList<String>();
		ArrayList<String> foundShopliftingWords = new ArrayList<String>();
		int calcFallen = 0;
		int calcShopl = 0;

		// Transcription part

		// Splitting transcription by space into separate list
		String[] splittedList = transcript.split(" ");

		// Looping keywords list
		for (String splittedWord : splittedList) {

			// Looping splitted list

			for (String fallenTreeWord : keyListForTree) {
				// If element of splitted list matches with element of keywords list
				// Printing "equals" and adding it to foundWords list

				if (splittedWord.contains(fallenTreeWord)) {

					// Jos splitted word ei ole negatiivinen eli on esim puu, palautuu false,
					// lisätään listaan foundTreeWords
					if (checkNegativeWords(splittedWord, keyNegativeListForTree) == false) {

						System.out.println("splittedWord ifissä " + splittedWord);
						foundTreeWords.add(splittedWord);
						calcFallen++;
					}

				}

			}

			for (String shopliftingWord : keyListForSL) {
				// If element of splitted list matches with element of keywords list
				// Printing "equals" and adding it to foundWords list
				if (splittedWord.contains(shopliftingWord)) {

					// Jos splitted word ei ole negatiivinen eli on esim puu, palautuu false,
					// lisätään listaan foundTreeWords
					if (checkNegativeWords(splittedWord, keyNegativeListForSL) == false) {

						System.out.println("splittedWord ifissä " + splittedWord);
						foundShopliftingWords.add(splittedWord);
						calcShopl++;
					}

				}
			}

		}
		// ottaa lausutun fraasin vain kerran vaikka tulisi transcriptissä useamman kerran
		// For checking phrases in fallen tree
		for (String keyword : keyListForTree) {
			if (keyword.contains(" ")) {
				boolean m = transcript.contains(keyword);
				System.out.println(transcript + " " + m + " mätsää " + keyword);
				if (m) {
					foundTreeWords.add(keyword);
					calcFallen++;
				}
			}
		}
		// For checking phrases in shoplifting
		for (String keyword : keyListForSL) {
			if (keyword.contains(" ")) {
				boolean m = transcript.contains(keyword);
				System.out.println(transcript + " " + m + " mätsää " + keyword);
				if (m) {
					foundShopliftingWords.add(keyword);
					calcShopl++;
				}
			}
		}

		System.out.println("words in foundTreedWords list: " + foundTreeWords.toString()
				+ " number of words in foundTreeWords: " + calcFallen);
		System.out.println("words in foundShopliftingWords list: " + foundShopliftingWords.toString()
				+ " number of words in foundShopliftingWords: " + calcShopl);

	}

	public static boolean checkNegativeWords(String splittedWord, List<String> negativeKeywords) {
		Iterator<String> negativeIterator = negativeKeywords.iterator();

		String neg = "";
		boolean found = false;
		while (negativeIterator.hasNext()) {

			String negativeWord = negativeIterator.next().toString();
			// System.out.println("splitted " + splittedWord + " negative " + negativeWord);
			if (splittedWord.equals(negativeWord)) {
				// System.out.println("negative word found " + negativeWord);
				neg = negativeWord;
				found = true;
				break;
			} else {
				// System.out.println("not found" + splittedWord);
				found = false;

			}

		}

		return found;
	}

	public static void saveTranscriptToString(String transcript) {

		savedTranscript = savedTranscript + transcript;

		System.out.println("Saved transcript: " + savedTranscript);
	}
}
