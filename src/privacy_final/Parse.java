/**
 * 
 */
package privacy_final;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISenseKey;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.DocumentPreprocessor;
import javafx.util.Pair;

/**
 * @author Scott Shi
 *
 */
public class Parse {

	private IDictionary wordnetdict;

	private ArrayList<String> sensitiveWord = new ArrayList<>();

	public Parse() {
		// initialize wordnetdict
		wordnetdict = new edu.mit.jwi.Dictionary(new File("data/dict/"));
		try {
			wordnetdict.open();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void parse(String inputFileName, String sensitiveFileName, String outputFileName) throws Exception {

		DocumentPreprocessor dp = new DocumentPreprocessor(inputFileName);
		dp.setSentenceDelimiter("</s>");

		BufferedReader br = new BufferedReader(new FileReader(sensitiveFileName));
		FileWriter fw = new FileWriter(outputFileName);
		BufferedWriter bw = new BufferedWriter(fw);

		String cur_line = null;

		while ((cur_line = br.readLine()) != null) {
			sensitiveWord.add(cur_line);
		}

		for (List<HasWord> sentense : dp) {
			for (HasWord word : sentense) {
				if (sensitiveWord.contains(word.word())) {
					// replace word
					word.setWord(replace(word.word()));
				}
				bw.write(word.word() + " ");
			}
		}
		bw.close();
		fw.close();
	}

	//waiting for your implementation
	private String replace(String s) {
		Map<String, Pair<String, Integer>> signatures = getSignatures(s, "NOUN");
		System.out.println(signatures);
		return s;
	}

	private Map<String, Pair<String, Integer>> getSignatures(String lemma, String pos_name) {

		POS pos = edu.mit.jwi.item.POS.valueOf((pos_name));
		System.out.println(pos);
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
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String inputFileName = "data/casereport.txt";
		String outputFileName = "data/test.txt";

		FileWriter fw = new FileWriter(outputFileName);
		BufferedWriter bw = new BufferedWriter(fw);

		DocumentPreprocessor dp = new DocumentPreprocessor(inputFileName);
		// for each sentence
		for (List<HasWord> sentence : dp) {
			// beginning of a sentence
			bw.write("<s>\n");
			// for each word in the sentence
			for (HasWord w : sentence) {
				bw.write(w.word().toLowerCase() + "\n");
			}
			// end of a sentence
			bw.write("</s>\n");
		}

		bw.close();
		fw.close();
		// TODO Auto-generated method stub
		new Parse().replace("effusion");
	}
}
