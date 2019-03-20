package privacy_final;
/**
 * Implement the Lesk algorithm for Word Sense Disambiguation (WSD)
 */
import java.util.*;
import java.util.Map.Entry;
import com.sun.javafx.scene.traversal.Algorithm;
import java.io.*;
import javafx.util.Pair;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import edu.mit.jwi.*;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.item.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.Tree;
//import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import net.sf.extjwnl.JWNLException;
//import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;

public class Lesk {

	/**
	 * Each entry is a sentence where there is at least a word to be disambiguate.
	 * E.g., testCorpus.get(0) is Sentence object representing "It is a full scale,
	 * small, but efficient house that can become a year' round retreat complete in
	 * every detail."
	 **/
	private ArrayList<Sentence> testCorpus = new ArrayList<Sentence>();

	/**
	 * Each entry is a list of locations (integers) where a word needs to be
	 * disambiguate. The index here is in accordance to testCorpus. E.g.,
	 * ambiguousLocations.get(0) is a list [13] ambiguousLocations.get(1) is a list
	 * [10, 28]
	 **/
	private ArrayList<ArrayList<Integer>> ambiguousLocations = new ArrayList<ArrayList<Integer>>();
	private ArrayList<ArrayList<Integer>> ambiguousLoc = new ArrayList<ArrayList<Integer>>();
	private ArrayList<ArrayList<ArrayList<Integer>>> all_ambiguousLocations = new ArrayList<ArrayList<ArrayList<Integer>>>();
	
	/**
	 * Each entry is a list of pairs, where each pair is the lemma and POS tag of an
	 * ambiguous word. E.g., ambiguousWords.get(0) is [(become, VERB)]
	 * ambiguousWords.get(1) is [(take, VERB), (apply, VERB)]
	 */
	private ArrayList<ArrayList<Pair<String, String>>> ambiguousWords = new ArrayList<ArrayList<Pair<String, String>>>();

	/**
	 * Each entry is a list of maps, each of which maps from a sense key to
	 * similarity(context, signature) E.g., predictions.get(1) = [{take%2:30:01:: ->
	 * 0.9, take%2:38:09:: -> 0.1}, {apply%2:40:00:: -> 0.1}]
	 */
	private ArrayList<ArrayList<HashMap<String, Double>>> predictions = new ArrayList<ArrayList<HashMap<String, Double>>>();
	
	/**
	 * best Sense of all words in test corpse
	 * E.g., bestSence.get(1) = best sense of confusing words in first sentence
	 * [{take%2:30:01::, apply%2:40:00:: }]
	 */
	private ArrayList<ArrayList<String>> bestSense = new ArrayList<ArrayList<String>>();
	
	
	/**
	 * Each entry is a list of ground truth senses for the ambiguous locations. Each
	 * String object can contain multiple synset ids, separated by comma. E.g.,
	 * groundTruths.get(0) is a list of strings
	 * ["become%2:30:00::,become%2:42:01::"] groundTruths.get(1) is a list of
	 * strings
	 * ["take%2:30:01::,take%2:38:09::,take%2:38:10::,take%2:38:11::,take%2:42:10::",
	 * "apply%2:40:00::"]
	 */
	private ArrayList<ArrayList<String>> groundTruths = new ArrayList<ArrayList<String>>();

	/* This section contains the NLP tools */

	private Set<String> POS = new HashSet<String>(Arrays.asList("ADJECTIVE", "ADVERB", "NOUN", "VERB"));

	private IDictionary wordnetdict;

	private StanfordCoreNLP pipeline;

	private Set<String> stopwords;

	private Set<String> POSTags;

	public Lesk() throws FileNotFoundException {
		// initialize stopwords
		stopwords = new HashSet<>();
		File file = new File("data/stopwords.txt");
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		try {
			while ((line = br.readLine()) != null) {
				stopwords.add(line);
			}
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// initialize wordnetdict
		wordnetdict = new edu.mit.jwi.Dictionary(new File("data/dict/"));
		try {
			wordnetdict.open();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// initialize pipeline
		// creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER,
		// parsing, and coreference resolution
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		pipeline = new StanfordCoreNLP(props);

		// initialize POSTags
		POSTags = new HashSet<String>() {
			{
				add("NOUN");
				add("VERB");
				add("ADJECTIVE");
				//add("RB");
			}
		};

	}

	/**
	 * Convert a pos tag in the input file to a POS tag that WordNet can recognize
	 * (JWI needs this). We only handle adjectives, adverbs, nouns and verbs.
	 * 
	 * @param pos:
	 *            a POS tag from an input file.
	 * @return JWI POS tag.
	 */
	private String toJwiPOS(String pos) {
		if (pos.equals("ADJECTIVE")) {
			return "ADJECTIVE";
		} else if (pos.equals("ADV")) {
			return "ADVERB";
		} else if (pos.equals("NOUN") || pos.equals("VERB")) {
			return pos;
		} else {
			return null;
		}
	}

	/**
	 * This function fills up testCorpus, ambiguousLocations and groundTruths lists
	 * 
	 * @param filename
	 */
	public void readTestData(String filename) throws Exception {

		ArrayList<Integer> al = new ArrayList<>();
		ArrayList<Pair<String, String>> aw = new ArrayList<>();
		ArrayList<String> gt = new ArrayList<>();

		int count_sentence = 0;
		String line = null;
		BufferedReader br = new BufferedReader(new FileReader(filename));
		while ((line = br.readLine()) != null) {
			ArrayList<Integer> temp1 = new ArrayList<>();
			ArrayList<Pair<String, String>> temp2 = new ArrayList<>();
			ArrayList<String> temp3 = new ArrayList<>();
			String[] str = line.split(" ");
			if (!str[0].matches(".?\\d+")) {
				Sentence s = new Sentence();
				count_sentence++;
				if (al.size() != 0) {
					temp1.addAll(al);
					temp2.addAll(aw);
					temp3.addAll(gt);
					ambiguousLocations.add(temp1);
					ambiguousLoc.add(temp1);
					ambiguousWords.add(temp2);
				}
				al.clear();
				aw.clear();
				gt.clear();
				for (String w : str) {
					Word word = new Word(w);
					s.addWord(word);
				}
				testCorpus.add(s);
				all_ambiguousLocations.add(ambiguousLoc);
				ambiguousLoc.clear();
			}
			if (str[0].contains("#")) {
				str[0] = str[0].replaceAll("#", "");
				al.add(Integer.parseInt(str[0]));
				aw.add(new Pair(str[1], str[2]));
				gt.add(str[3]);
			}
		}
		br.close();
		
		ArrayList<Integer> temp1 = new ArrayList<>();
		ArrayList<Pair<String, String>> temp2 = new ArrayList<>();
		ArrayList<String> temp3 = new ArrayList<>();
		temp1.addAll(al);
		temp2.addAll(aw);
		temp3.addAll(gt);
		ambiguousLocations.add(temp1);
		ambiguousLoc.add(temp1);
		ambiguousWords.add(temp2);
		all_ambiguousLocations.add(ambiguousLoc);
		
		 //System.out.println(groundTruths);
	} 

	/**
	 * Create signatures of the senses of a pos-tagged word.
	 * 
	 * 1. use lemma and pos to look up IIndexWord using Dictionary.getIndexWord() 2.
	 * use IIndexWord.getWordIDs() to find a list of word ids pertaining to this
	 * (lemma, pos) combination. 3. Each word id identifies a sense/synset in
	 * WordNet: use Dictionary's getWord() to find IWord 4. Use the getSynset() api
	 * of IWord to find ISynset Use the getSenseKey() api of IWord to find ISenseKey
	 * (such as charge%1:04:00::) 5. Use the getGloss() api of the ISynset interface
	 * to get the gloss String 6. Use the
	 * Dictionary.getSenseEntry(ISenseKey).getTagCount() to find the frequencies of
	 * the synset.d
	 * 
	 * @param args
	 *            lemma: word form to be disambiguated pos_name: POS tag of the
	 *            wordform, must be in {ADJECTIVE, ADVERB, NOUN, VERB}.
	 * 
	 */
	private Map<String, Pair<String, Integer>> getSignatures(String lemma, String pos_name) {

		POS pos = edu.mit.jwi.item.POS.valueOf((pos_name));
//		System.out.println(pos);
		IIndexWord iIndexWord = wordnetdict.getIndexWord(lemma, pos);
		List<IWordID> wordIDs = iIndexWord.getWordIDs();
		Map<String, Pair<String, Integer>> map = new HashMap<String, Pair<String, Integer>>();
		Pair pair;
		for (IWordID iWordID : wordIDs) {
			IWord word = wordnetdict.getWord(iWordID);
			ISynset iSynset = word.getSynset();
			ISenseKey iSenseKey = word.getSenseKey();
			String gloss = iSynset.getGloss();
			int tagCount = wordnetdict.getSenseEntry(iSenseKey).getTagCount();
			pair = new Pair<String, Integer>(gloss, tagCount);
			map.put(iSenseKey.toString(), pair);
		}
		return map;
	}
	
	/**
	 * compute similarity between two bags-of-words.
	 * 
	 * @param bag1
	 *            first bag of words
	 * @param bag2
	 *            second bag of words
	 * @param sim_opt
	 *            COSINE or JACCARD similarity
	 * @return similarity score
	 */
	private double similarity(ArrayList<String> bag1, ArrayList<String> bag2) {
			Set result = new HashSet<String>();
			// overlap set
			result.addAll(bag1);
			result.removeAll(bag2);
			double sim = result.size();

			return sim / (double) (Math.sqrt(bag1.size()) * (Math.sqrt(bag2.size())));

	}

	/**
	 * This is the WSD function that prediction what senses are more likely.
	 * 
	 * @param context_option:
	 *            one of {ALL_WORDS, ALL_WORDS_R, WINDOW, POS}
	 * @param window_size:
	 *            an odd positive integer > 1
	 * @param sim_option:
	 *            one of {COSINE, JACCARD}
	 * @throws Exception
	 */
	public void predict(int window_size) throws Exception {
		for (int i = 0; i < testCorpus.size(); i++) {
			Sentence sentence = testCorpus.get(i);
//			ArrayList<Integer> locationList = ambiguousLocations.get(i);
			ArrayList<Pair<String, String>> wordList = ambiguousWords.get(i);
			ArrayList<HashMap<String, Double>> temp = new ArrayList<>();
			ArrayList<String> bestSen = new ArrayList<String>();
			for (Pair<String, String> pair : wordList) {
				double bestSim = 0;
				String bestStr = null;
				String target = pair.getKey();
				ArrayList<String> context = getContext(sentence, target);

				// System.out.println(context);

				String pos = toJwiPOS(pair.getValue());
				Map<String, Pair<String, Integer>> signatures = getSignatures(pair.getKey(), pos);

				// System.out.println(signatures);

				HashMap<String, Double> hm = new HashMap<>();
				for (String str : signatures.keySet()) {
					String gloss = signatures.get(str).getKey();
//					System.out.println(str);
					double similarity = similarity(getSignatureContext(window_size,gloss), context);
					hm.put(str, similarity);
//					System.out.println(gloss+" "+similarity);
					if(similarity > bestSim) {
						bestSim = similarity;
						bestStr = str;
					}
				}
				temp.add(hm);
				bestSen.add(bestStr);
			}
			System.out.println("current position : "+i);
			predictions.add(temp);
			bestSense.add(bestSen);
		}
	}
	private void getHypernym (Synset word) {
		PointerTargetNodeList hypernym= new PointerTargetNodeList();
		try {
			hypernym = PointerUtils.getDirectHypernyms(word);
		} catch (JWNLException e) {
  			e.printStackTrace();
		}
	}
	
	private int getHyponym (Synset word) {
		int num = 0;
		try {
			//get # of siblings of a word
			num = PointerUtils.getCoordinateTerms(word).size();
		} catch (JWNLException e) {
			e.printStackTrace();
		}
		return num;
	}
	private double[] getEntropy_w (int[] hypoNum) {
		double[] hw = new double[hypoNum.length];
		for(int i = 0; i < hypoNum.length; i++) {
			hw[i] = -(Math.log(1/hypoNum[i]) / Math.log(2));
		}
		return hw;
	}
	private double getEntropy_d (double prob) {
		double hd = 1;
		hd = -(Math.log(prob) / Math.log(2));
		return hd;
	}
	private double getProb (int[] hypoNum) {
		double prob = 1;
		for(int i = 0; i < hypoNum.length; i++) {
			prob = prob * (1/hypoNum[i]);
		}
		return prob;
	}
	//word: original word set before get their hypernym
	private double getCost (ArrayList<Synset> word, int threshold) {
		double cost = 0;
		double prob = 0;
		double hd = 0;
		double[] hw = new double[word.size()];
		int[] hypoNum = new int[word.size()];
		
		for(int i = 0; i < word.size(); i++) {
			hypoNum[i] = getHyponym(word.get(i));
		}
		prob = getProb(hypoNum);
		hd = getEntropy_d(prob);
		hw = getEntropy_w(hypoNum);
		
		for(int i = 0; i < word.size(); i++) {
			double d = hw[i] - (Math.log(threshold)/Math.log(2))/word.size();
			d = Math.pow(d, 2);
			cost = cost + d;
			d = 0;
		}
		
		cost = ((0.5/Math.pow(word.size(), 2)) * Math.pow(hd - Math.log(threshold)/Math.log(2),2)) + (cost * 0.5/word.size());
		return cost;
	}
	
    public static void getHypernyms(IDictionary dict, String sensitive, POS pos, int n){
    	//get specific synset
        IIndexWord idxWord = dict.getIndexWord(sensitive, pos); //get IndexWord of sensitive word
        IWordID wordID = idxWord.getWordIDs().get(n); // get ID of sense n
        IWord word = dict.getWord(wordID); 
        ISynset synset = word.getSynset(); 
        
        //get hypernym
        List<ISynsetID> hypernyms =synset.getRelatedSynsets(Pointer.HYPERNYM);
        // print hypernym 
        List <IWord > words ;
        for( ISynsetID sid : hypernyms ){
            words = dict.getSynset(sid).getWords(); 
            System.out.print(sid + "{");
            for( Iterator<IWord > i = words.iterator(); i.hasNext();){
               System.out.print(i.next().getLemma ());
               if(i. hasNext ()){
                   System.out.print(", ");
               }
            }
            System .out . println ("}");
        }
     }
    
	private ArrayList<String> getSignatureContext(int window_size, String text) throws Exception {
		
		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);

		// run all Annotators on this text
		pipeline.annotate(document);
		
		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and has values
		// with custom types
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);

		ArrayList<String> list = new ArrayList<>();
		Sentence s = new Sentence();
		for (CoreMap sentence : sentences) {
			// traversing the words in the current sentence
			// a CoreLabel is a CoreMap with additional token-specific methods
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				// this is the text of the token
				String word = token.get(TextAnnotation.class);
				// this is the POS tag of the token
				String pos = token.get(PartOfSpeechAnnotation.class);
				s.addWord(new Word(word, pos));
				// this is the NER label of the token
				// String ne = token.get(NamedEntityTagAnnotation.class);
			}

			// this is the parse tree of the current sentence
			// Tree tree = sentence.get(TreeAnnotation.class);
			// System.out.println(tree);

		}
		list = getContext(s, null);

		// This is the coreference link graph
		// Each chain stores a set of mentions that link to each other,
		// along with a method for getting the most representative mention
		// Both sentence and token offsets start at 1!
		// Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);

		return list;
	}

	private ArrayList<String> getContext(Sentence sentence, String target)
			throws Exception {
		ArrayList<String> context = new ArrayList<>();
		/*
		if (context_option.equalsIgnoreCase("ALL_WORDS")) {
			context = sentence.getAllWords();
		}
		if (context_option.equalsIgnoreCase("ALL_WORDS_R")) {
		*/
		// case ALL_WORDS_R:
			for (Word word : sentence) {
				if (!stopwords.contains(word.getLemme())) {
					context.add(word.getLemme());
				}
			}

		return context;
	}
	
	/**
	 * @param args[0]
	 *            file name of a test corpus
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Lesk model = new Lesk();
		try {
			model.readTestData("data/dataFormat.txt");
		} catch (Exception e) {
			// System.out.println(args[0]);
			e.printStackTrace();
		}
		int window_size = 3;

		model.predict(window_size);
		
		//System.out.println(model.predictions);
		

	}
}
