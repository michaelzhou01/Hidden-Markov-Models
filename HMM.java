import java.io.*;
import java.util.*;
import java.lang.Math;

public class HMM {
	public Map<String, Map<String, Double>> transitions;
	public Map<String, Map<String, Double>> observations;
	
	//constructor to initialize maps
	public HMM() {
		transitions = new HashMap<String, Map<String, Double>>();
		observations = new HashMap<String, Map<String, Double>>();
	}

	public String viterbi(String line) {
		double penalty = -100.0; //unseen-word penalty
		
		String[] words = line.split(" ");
		List<Map<String, String>> backtraces = new ArrayList<Map<String, String>>();

		//basically following the pseudocode given on day 20 of notes
		//handle the start case specially
		Set<String> curr_states = new HashSet<String>();
		curr_states.add("start");
		Map<String, Double> curr_scores = new HashMap<String, Double>();
		curr_scores.put("start", 0.0);
		//loop through all the words
		for (int i = 0; i < words.length; i++) { 
			Map<String, String> backtrace = new HashMap<String, String>();

			Set<String> next_states = new HashSet<String>();
			Map<String, Double> next_scores = new HashMap<String, Double>();

			for (String curr_state : curr_states) {
				for (String next_state : transitions.get(curr_state).keySet()) {
					//slowly build up next_states and next_scores
					next_states.add(next_state);
					double next_score;
					if (observations.get(next_state).containsKey(words[i])) { //if word has been seen in this part of speech
						next_score = curr_scores.get(curr_state) + transitions.get(curr_state).get(next_state) + observations.get(next_state).get(words[i]);
					}
					else { //if word not seen in this part of speech
						next_score = curr_scores.get(curr_state) + transitions.get(curr_state).get(next_state) + penalty;
					}

					if (!next_scores.containsKey(next_state) || next_score > next_scores.get(next_state)) {
						next_scores.put(next_state, next_score);
						backtrace.put(next_state, curr_state);
					}
				}
			}
			
			curr_states = next_states; //update these for the next word
			curr_scores = next_scores;
			backtraces.add(backtrace); //add backtrace to backtraces at the after each word 
		}
		
		//now we want to put the most likely sequence in a String
		//first find the last state by finding the best curr_state at the end
		String last_state = null;
		for (String state : curr_states) {
			if (last_state == null) last_state = state;
			else if (curr_scores.get(state) > curr_scores.get(last_state)) last_state = state;
		}
		
		//create sequence (currently backwards) from the backtraces
		List<String> sequence = new ArrayList<String>();
		for (int i = words.length - 1; i >= 0; i--) {
			sequence.add(last_state);
			last_state = backtraces.get(i).get(last_state);
		}
		
		String parts_of_speech = "";
		//create String from sequence (loop through backwards to get forwards sequence)
		for (int i = words.length - 1; i >= 0; i--) {
			parts_of_speech += sequence.get(i) + " ";
		}
		
		return parts_of_speech;
	}

	//creates the transitions and observation maps given sentences and tags training files
	public void trainModel(String train_sentences_path, String train_tags_path) {
		//create transitions map first
		//read tags file
		BufferedReader tags_input = null;
		try {
			tags_input = new BufferedReader(new FileReader(train_tags_path));
		} 

		catch (FileNotFoundException e) {
			System.err.println("Can't find file.\n" + e.getMessage());
		}
		
		//read lines and first build up the transitions map with numbers instead of probabilities (convert after)
		transitions.put("start", new HashMap<String, Double>()); //we put it here rather than an if statement later for efficiency reasons
		String line = null;
		try {
			while ((line = tags_input.readLine()) != null) {
				String[] tags = line.split(" ");
				
				//first handle the start differently
				if (!transitions.get("start").containsKey(tags[0])) {
					transitions.get("start").put(tags[0], 0.0);
				}
				
				transitions.get("start").put(tags[0], transitions.get("start").get(tags[0]) + 1);
				
				//now loop through all the tags except the last
				for (int i = 0; i < tags.length - 1; i++) { 
					if (!transitions.containsKey(tags[i])) { //create the key in outer map if not there already
						transitions.put(tags[i], new HashMap<String, Double>());
					}
					
					if (!transitions.get(tags[i]).containsKey(tags[i + 1])) { //create key in inner map if not there already
						transitions.get(tags[i]).put(tags[i + 1], 0.0);
					}
					
					//increment number in map by 1
					transitions.get(tags[i]).put(tags[i + 1], transitions.get(tags[i]).get(tags[i + 1]) + 1);
				}
			}
		}
		
		catch (IOException e) {
			System.err.println("Can't read line properly" + e.getMessage());
		}

		//try to close the file
		try {
			tags_input.close();
		}

		catch (IOException e) {
			System.err.println("Can't close file.\n" + e.getMessage());
		}
		
		//now convert the numbers to logs
		//loop through all keys (parts of speech) of outer map
		for (String pos_out : transitions.keySet()) {
			int tot = 0;
			
			//loop through all the keys (parts of speech) of inner map
			//first time - find the sum of inner values for each outer key
			for (String pos_in : transitions.get(pos_out).keySet()) {
				tot += transitions.get(pos_out).get(pos_in);
			}
			
			//second time - convert to fractions (probabilities) then logs
			for (String pos_in : transitions.get(pos_out).keySet()) {
				transitions.get(pos_out).put(pos_in, Math.log(transitions.get(pos_out).get(pos_in) / tot));
			}
		}
		


		//now create observations map
		//code is similar to creation of transitions map but now with data from both files
		BufferedReader sentences_input = null;
		try {
			sentences_input = new BufferedReader(new FileReader(train_sentences_path));
		} 

		catch (FileNotFoundException e) {
			System.err.println("Can't find file.\n" + e.getMessage());
		}
		
		try {
			tags_input = new BufferedReader(new FileReader(train_tags_path));
		} 

		catch (FileNotFoundException e) {
			System.err.println("Can't find file.\n" + e.getMessage());
		}

		//read lines and first build up the transitions map with numbers instead of probabilities (convert after)
		//read the lines of both files and builds up the initial observations map
		//assumes that the training data is formatted correctly
		String line_sents;
		String line_tags;
		try {
			while ((line_sents = sentences_input.readLine()) != null) {
				line_tags = tags_input.readLine(); 
				
				String[] words = line_sents.split(" ");
				String[] tags = line_tags.split(" ");
				for (int i = 0; i < tags.length; i++) { //loop through all the words
					//lowercase the words
					words[i] = words[i].toLowerCase();
					
					if (!observations.containsKey(tags[i])) { //create the key in outer map if not there already
						observations.put(tags[i], new HashMap<String, Double>());
					}

					if (!observations.get(tags[i]).containsKey(words[i])) { //create key in inner map if not there already
						observations.get(tags[i]).put(words[i], 0.0);
					}

					//increment number in map by 1
					observations.get(tags[i]).put(words[i], observations.get(tags[i]).get(words[i]) + 1);
				}
			}
		}

		catch (IOException e) {
			System.err.println("Can't read line properly" + e.getMessage());
		}

		//try to close the files
		try {
			tags_input.close();
		}

		catch (IOException e) {
			System.err.println("Can't close file.\n" + e.getMessage());
		}

		try {
			sentences_input.close();
		}

		catch (IOException e) {
			System.err.println("Can't close file.\n" + e.getMessage());
		}

		//now convert the numbers in observation map to logs
		//loop through all keys (parts of speech) of outer map
		for (String pos_out : observations.keySet()) {
			int tot = 0;

			//loop through all the keys (words) of inner map
			//first time - find the sum of inner values for each outer key
			for (String word : observations.get(pos_out).keySet()) {
				tot += observations.get(pos_out).get(word);
			}

			//second time - convert to fractions (probabilities) then logs
			for (String word : observations.get(pos_out).keySet()) {
				observations.get(pos_out).put(word, Math.log(observations.get(pos_out).get(word) / tot));
			}
		}
	}

	//gives tags for user input
	public void consoleTagging() {
		Scanner s = new Scanner(System.in);;
		String line;
		while (true) { //keep on running while the user keeps providing input
			System.out.println("Hello! Please provide an input, or 'q' to quit.");
			line = s.nextLine(); //reads sentence user provides 
			if (line.length() < 2 && line.charAt(0) == 'q') break; //quit if user enters "q"
			System.out.println(viterbi(line)); //prints the parts of speech from Viterbi method
		}
		
		s.close();
	}
	
	//tests our output against the real output
	public void fileTest(String test_sentences_path, String test_tags_path) {
		//first create our output file
		String test_tags_attempt_path = "texts/test-tags-attempt.txt";
		
		BufferedReader sentences_input = null;
		try {
			sentences_input = new BufferedReader(new FileReader(test_sentences_path));
		} 

		catch (FileNotFoundException e) {
			System.err.println("Can't find file.\n" + e.getMessage());
		}
		
		BufferedWriter tags_output = null;
		try {
			tags_output = new BufferedWriter(new FileWriter(test_tags_attempt_path));
		}

		catch(IOException e) {
			System.err.print("Can't write to file properly.\n" + e.getMessage());
		}

		//read lines and write out the parts of speech for each sentence
		String line = null;
		try {
			while ((line = sentences_input.readLine()) != null) {
				tags_output.write(viterbi(line) + "\n"); //just write to the file what the viterbi method returns + new line
			}
		}
		catch (IOException e) {
			System.err.println("Can't read line properly" + e.getMessage());
		}

		//try to close the file reader
		try {
			sentences_input.close();
		}

		catch (IOException e) {
			System.err.println("Can't close file.\n" + e.getMessage());
		}

		//try to close the file writer
		try {
			tags_output.close();
		}

		catch (IOException e) {
			System.err.println("Can't close file.\n" + e.getMessage());
		}
		
		
		
		//now compare the two files
		//keep track of how many we got right and wrong
		int correct = 0;
		int wrong = 0;
		
		//read correct tags file
		BufferedReader tags_input = null;
		try {
			tags_input = new BufferedReader(new FileReader(test_tags_path));
		} 

		catch (FileNotFoundException e) {
			System.err.println("Can't find file.\n" + e.getMessage());
		}
		
		//read our tags file
		BufferedReader tags_attempt_input = null;
		try {
			tags_attempt_input = new BufferedReader(new FileReader(test_tags_attempt_path));
		} 

		catch (FileNotFoundException e) {
			System.err.println("Can't find file.\n" + e.getMessage());
		}

		//read lines compare them
		String line_tags;
		String line_tags_attempt;
		try {
			//read both lines
			while ((line_tags = tags_input.readLine()) != null) {
				line_tags_attempt = tags_attempt_input.readLine();
				
				//split both lines
				String[] pos_tags = line_tags.split(" ");
				String[] pos_tags_attempted = line_tags_attempt.split(" ");
				
				//compare all the elements of the array
				for (int i = 0; i < pos_tags.length; i++) {
					if (pos_tags[i].equals(pos_tags_attempted[i])) correct++;
					else wrong++;
				}
			}
		}
		
		catch (IOException e) {
			System.err.println("Can't read line properly" + e.getMessage());
		}

		//try to close the file readers
		try {
			tags_input.close();
		}

		catch (IOException e) {
			System.err.println("Can't close file.\n" + e.getMessage());
		}

		try {
			tags_attempt_input.close();
		}

		catch (IOException e) {
			System.err.println("Can't close file.\n" + e.getMessage());
		}
		
		//print out how many correct and incorrect tags
		System.out.println(correct + " tags were correct.");
		System.out.println(wrong + " tags were wrong.");
	}

	public static void main(String[] args) {
		String train_sentences_path = "texts/brown-train-sentences.txt";
		String train_tags_path = "texts/brown-train-tags.txt";
		String test_sentences_path = "texts/brown-test-sentences.txt";
		String test_tags_path = "texts/brown-test-tags.txt";

		//testing
		HMM newHMM = new HMM();
		newHMM.trainModel(train_sentences_path, train_tags_path);
		newHMM.fileTest(test_sentences_path, test_tags_path);
		newHMM.consoleTagging();
	}
}
