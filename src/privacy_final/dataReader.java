package privacy_final;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

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

public class dataReader {
	
	private HashMap<String, String> sw = new HashMap<>();
	
	private int count = 0;

	private IDictionary wordnetdict;
	
	public dataReader() {
	
		// initialize wordnetdict
		wordnetdict = new edu.mit.jwi.Dictionary(new File("data/dict/"));
		try {
			wordnetdict.open();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

	private void read(String inputDataFilePath, String sensitiveDataFilePath, String outputDataFilePath) throws Exception {
		
		DocumentPreprocessor dp = new DocumentPreprocessor(inputDataFilePath);
		dp.setSentenceDelimiter("</s>");
		
		BufferedReader br = new BufferedReader(new FileReader(sensitiveDataFilePath));
		FileWriter fw = new FileWriter(outputDataFilePath);
		BufferedWriter bw = new BufferedWriter(fw);

		String cur_line = null;

		while ((cur_line = br.readLine()) != null) {
			String[] str = cur_line.split(" ");
			//key:lemme v:pos-tag
			sw.put(str[0],str[1]);
		}
		for (List<HasWord> sentense : dp) {
			HashMap<Integer,Pair<String, String>> temp = new HashMap<>();
			for (int i = 0; i < sentense.size()-2; i++) {
				bw.write(sentense.get(i+1).word().toLowerCase()+" ");
				if(sw.containsKey(sentense.get(i+1).word().toLowerCase())) {
					count++;
					temp.put(i, new Pair(sentense.get(i+1).word(), sw.get(sentense.get(i+1).word())));
				}
			}
			bw.write("\n"+count+"\n");
			count = 0;
			Iterator<Entry<Integer, Pair<String, String>>> it = temp.entrySet().iterator();
			while(it.hasNext()) {
				Entry<Integer, Pair<String, String>> entry = it.next();
				bw.write("#"+entry.getKey()+" "+entry.getValue().getKey()+" "+entry.getValue().getValue()+" ");
				Map<String, Pair<String, Integer>> signatures = getSignatures(entry.getValue().getKey(), entry.getValue().getValue());
				bw.write(StringUtils.join(signatures.keySet().toArray(),","));
				bw.newLine();
				System.out.println(signatures);
			}
		}
	}
	
	private Map<String, Pair<String, Integer>> getSignatures(String lemma, String pos_name) {

		POS pos = edu.mit.jwi.item.POS.valueOf((pos_name));
		System.out.println(lemma+" "+pos);
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
	
	public static void main(String[] args) throws Exception {
		String inputFileName = "data/test.txt";
		String outputFileName = "data/dataFormat.txt";
		String sensitiveDataFilePath = "data/sensitive.txt";

		dataReader reader = new dataReader();
		reader.read(inputFileName, sensitiveDataFilePath, outputFileName);
		System.out.println(reader.sw);
	}
}
