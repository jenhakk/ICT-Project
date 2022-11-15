package app;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.api.server.spi.Client;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.sun.xml.internal.stream.Entity;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.TargetDataLine;

import data.Speech;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

public class InfiniteStreamRecognize {

	private static final int STREAMING_LIMIT = 290000; // ~5 minutes

	public static final String RED = "\033[0;31m";
	public static final String GREEN = "\033[0;32m";
	public static final String YELLOW = "\033[0;33m";

	// Creating shared object
	private static volatile BlockingQueue<byte[]> sharedQueue = new LinkedBlockingQueue();
	private static TargetDataLine targetDataLine;
	private static int BYTES_PER_BUFFER = 6400; // buffer size in bytes

	private static int restartCounter = 0;
	private static ArrayList<ByteString> audioInput = new ArrayList<ByteString>();
	private static ArrayList<ByteString> lastAudioInput = new ArrayList<ByteString>();
	private static int resultEndTimeInMS = 0;
	private static int isFinalEndTime = 0;
	private static int finalRequestEndTime = 0;
	private static boolean newStream = true;
	private static double bridgingOffset = 0;
	private static boolean lastTranscriptWasFinal = false;
	private static StreamController referenceToStreamController;
	private static ByteString tempByteString;

	private static String savedTranscript = "";

	public static void main(String[] args) {
		
	
		/*
		 * s = b.get(Speech.class);
		 * 
		 * String st=b.get(String.class); System.out.println("Object: " + s);
		 * System.out.println("JSON: " + st);
		 */
		
		
		InfiniteStreamRecognizeOptions options = InfiniteStreamRecognizeOptions.fromFlags(args);
		if (options == null) {
			// Could not parse.
			System.out.println("Failed to parse options.");
			System.exit(1);
		}

		try {
			infiniteStreamingRecognize(options.langCode);
			
			
		} catch (Exception e) {
			System.out.println("Exception caught: " + e);
		}
	}

	public static String convertMillisToDate(double milliSeconds) {
		long millis = (long) milliSeconds;
		DecimalFormat format = new DecimalFormat();
		format.setMinimumIntegerDigits(2);
		return String.format("%s:%s /", format.format(TimeUnit.MILLISECONDS.toMinutes(millis)),
				format.format(TimeUnit.MILLISECONDS.toSeconds(millis)
						- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))));
	}

	/** Performs infinite streaming speech recognition */
	public static void infiniteStreamingRecognize(String languageCode) throws Exception {

		// Microphone Input buffering
		class MicBuffer implements Runnable {

			@Override
			public void run() {
				
				System.out.println("ollaanko tryssa?");
				String uri = "http://127.0.0.1:8080/hello";
				
				Speech s = new Speech("1", "Mitä kuuluu?", "Hyvää kuuluu");
				javax.ws.rs.client.Client c = ClientBuilder.newClient();
				WebTarget wt = c.target(uri);
				Builder b = wt.request(); 
				
				
				System.out.println(YELLOW);
				System.out.println("Start speaking...Press Ctrl-C to stop");
				targetDataLine.start();
				System.out.println("haloo" + targetDataLine.isActive());
				System.out.println("haloojaa" + targetDataLine.isOpen());
				byte[] data = new byte[BYTES_PER_BUFFER];
				while (targetDataLine.isOpen()) {
										try {
						int numBytesRead = targetDataLine.read(data, 0, data.length);
						if ((numBytesRead <= 0) && (targetDataLine.isOpen())) {
							continue;
						}
						sharedQueue.put(data.clone());
					} catch (InterruptedException e) {
						System.out.println("Microphone input buffering interrupted : " + e.getMessage());
					}
				}
				
				
			}
		}

		// Creating microphone input buffer thread
		MicBuffer micrunnable = new MicBuffer();
		Thread micThread = new Thread(micrunnable);
		ResponseObserver<StreamingRecognizeResponse> responseObserver = null;
		try (SpeechClient client = SpeechClient.create()) {
			
			System.out.println("tulostellaan");
			System.out.println("are you alive" + micThread.isAlive());
			System.out.println("are you alive" + micThread.interrupted());
			ClientStream<StreamingRecognizeRequest> clientStream;
			responseObserver = new ResponseObserver<StreamingRecognizeResponse>() {

				ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

				public void onStart(StreamController controller) {
					referenceToStreamController = controller;
				}

				public void onResponse(StreamingRecognizeResponse response) {
					responses.add(response);
					StreamingRecognitionResult result = response.getResultsList().get(0);
					Duration resultEndTime = result.getResultEndTime();
					resultEndTimeInMS = (int) ((resultEndTime.getSeconds() * 1000)
							+ (resultEndTime.getNanos() / 1000000));
					double correctedTime = resultEndTimeInMS - bridgingOffset + (STREAMING_LIMIT * restartCounter);

					SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
					if (result.getIsFinal()) {
						System.out.print(GREEN);
						System.out.print("\033[2K\r");
//                System.err.println("Testi "+alternative.getTranscript()+ " Sitten  "+convertMillisToDate(correctedTime)+ "  Loppu");
						System.out.printf("%s: %s [confidence: %.2f]\n", convertMillisToDate(correctedTime),
								alternative.getTranscript(), alternative.getConfidence());
						isFinalEndTime = resultEndTimeInMS;
						lastTranscriptWasFinal = true;
						String transcript = alternative.getTranscript();
//						System.out.println("mikä tämä on " + alternative);
//						System.out.println("response " + response);
						onComplete(transcript);
					} else {
						System.out.print(RED);
						System.out.print("\033[2K\r");
						System.out.printf("%s: %s", convertMillisToDate(correctedTime), alternative.getTranscript());
						lastTranscriptWasFinal = false;
					}
				}

				public void onComplete(String transcript) {

					// Method for finding and matching keywords from splittedList
					findKeywords(transcript);

					// Method for saving all transcripts as one String to be used later if needed
					saveTranscriptToString(transcript);
				}

				public void findKeywords(String transcript) {

					System.out.println("no modifications " + transcript);
					transcript = transcript.toLowerCase();

					System.out.println("lowerCaseTranscript? " + transcript);

					// Lists for negative keywords to ignore
					List<String> negativeFallenKeywords = Arrays.asList("puukko", "puukkoa", "puuliiteri",
							"puuliiteristä", "puuliiterissä", "päällystää", "puimuri");
					List<String> negativeShopliftingKeywords = Arrays.asList("ryöstäytyä", "varasto", "varaslähtö");


					// Lists for keywords to search
					List<String> fallenTreeList = Arrays.asList("puu", "pui", "kaatu", "pääll");
					List<String> shopliftingList = Arrays.asList("ryöst", "varas", "asee");

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

						for (String fallenTreeWord : fallenTreeList) {
							// If element of splitted list matches with element of keywords list
							// Printing "equals" and adding it to foundWords list
							if (splittedWord.contains(fallenTreeWord)) {

								// Jos splitted word ei ole negatiivinen eli on esim puu, palautuu false,
								// lisätään listaan foundTreeWords
								if (checkNegativeWords(splittedWord, negativeFallenKeywords) == false) {

									System.out.println("splittedWord ifissä " + splittedWord);
									foundTreeWords.add(splittedWord);
									calcFallen++;
								}

							}

						}
						for (String shopliftingWord : shopliftingList) {
							// If element of splitted list matches with element of keywords list
							// Printing "equals" and adding it to foundWords list
							if (splittedWord.contains(shopliftingWord)) {

								// Jos splitted word ei ole negatiivinen eli on esim puu, palautuu false,
								// lisätään listaan foundTreeWords
								if (checkNegativeWords(splittedWord, negativeShopliftingKeywords) == false) {

									System.out.println("splittedWord ifissä " + splittedWord);
									foundShopliftingWords.add(splittedWord);
									calcShopl++;
								}

							}
						}

					}

					System.out.println("words in foundTreedWords list: " + foundTreeWords.toString()
							+ " number of words in foundTreeWords: " + calcFallen);
					System.out.println("words in foundShopliftingWords list: " + foundShopliftingWords.toString()
							+ " number of words in foundShopliftingWords: " + calcShopl);

				}

				public boolean checkNegativeWords(String splittedWord, List<String> negativeKeywords) {
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

				public void saveTranscriptToString(String transcript) {

					savedTranscript = savedTranscript + transcript;

					//System.out.println(savedTranscript);
				}

				public void onError(Throwable t) {
				}

				@Override
				public void onComplete() {
					// TODO Auto-generated method stub

				}
			};
			clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);

			RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
					.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16).setLanguageCode(languageCode)
					.setSampleRateHertz(16000).build();

			StreamingRecognitionConfig streamingRecognitionConfig = StreamingRecognitionConfig.newBuilder()
					.setConfig(recognitionConfig).setInterimResults(true).build();

			StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
					.setStreamingConfig(streamingRecognitionConfig).build(); // The first request in a streaming call
																				// has to be a config

			clientStream.send(request);

			try {
				// SampleRate:16000Hz, SampleSizeInBits: 16, Number of channels: 1, Signed:
				// true,
				// bigEndian: false
				AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
				DataLine.Info targetInfo = new Info(TargetDataLine.class, audioFormat); // Set the system information to
																						// read from the microphone
																						// audio
				// stream

				if (!AudioSystem.isLineSupported(targetInfo)) {
					System.out.println("Microphone not supported");
					System.exit(0);
				}
				// Target data line captures the audio stream the microphone produces.
				targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
				targetDataLine.open(audioFormat);
				micThread.start();

				long startTime = System.currentTimeMillis();

				while (true) {

					long estimatedTime = System.currentTimeMillis() - startTime;

					if (estimatedTime >= STREAMING_LIMIT) {

						clientStream.closeSend();
						referenceToStreamController.cancel(); // remove Observer

						if (resultEndTimeInMS > 0) {
							finalRequestEndTime = isFinalEndTime;
						}
						resultEndTimeInMS = 0;

						lastAudioInput = null;
						lastAudioInput = audioInput;
						audioInput = new ArrayList<ByteString>();

						restartCounter++;

						if (!lastTranscriptWasFinal) {
							System.out.print('\n');
						}

						newStream = true;

						clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);

						request = StreamingRecognizeRequest.newBuilder().setStreamingConfig(streamingRecognitionConfig)
								.build();

						System.out.println(YELLOW);
						System.out.printf("%d: RESTARTING REQUEST\n", restartCounter * STREAMING_LIMIT);

						startTime = System.currentTimeMillis();

					} else {

						if ((newStream) && (lastAudioInput.size() > 0)) {
							// if this is the first audio from a new request
							// calculate amount of unfinalized audio from last request
							// resend the audio to the speech client before incoming audio
							double chunkTime = STREAMING_LIMIT / lastAudioInput.size();
							// ms length of each chunk in previous request audio arrayList
							if (chunkTime != 0) {
								if (bridgingOffset < 0) {
									// bridging Offset accounts for time of resent audio
									// calculated from last request
									bridgingOffset = 0;
								}
								if (bridgingOffset > finalRequestEndTime) {
									bridgingOffset = finalRequestEndTime;
								}
								int chunksFromMs = (int) Math.floor((finalRequestEndTime - bridgingOffset) / chunkTime);
								// chunks from MS is number of chunks to resend
								bridgingOffset = (int) Math.floor((lastAudioInput.size() - chunksFromMs) * chunkTime);
								// set bridging offset for next request
								for (int i = chunksFromMs; i < lastAudioInput.size(); i++) {
									request = StreamingRecognizeRequest.newBuilder()
											.setAudioContent(lastAudioInput.get(i)).build();
									clientStream.send(request);
								}
							}
							newStream = false;
						}

						tempByteString = ByteString.copyFrom(sharedQueue.take());

						request = StreamingRecognizeRequest.newBuilder().setAudioContent(tempByteString).build();

						audioInput.add(tempByteString);
					}

					clientStream.send(request);
				}
			} catch (Exception e) {
				System.out.println(e);
			}
		}
	}
}
// [END speech_transcribe_infinite_streaming]