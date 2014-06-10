import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

public class StemWord{

    public static final int KEYWORD_LIMIT = 20;
    public static final int TOPIC_COUNT = 134;

    public static void main(String[] args) throws Exception {
	SolrServer server = new HttpSolrServer(
					       "http://localhost:8983/solr/core0");
	System.out.println("Connected");
	String[] topicIndex = new String[TOPIC_COUNT];
	Double[] initialProb = new Double[TOPIC_COUNT];
	Map<String, Double> probMap = new HashMap<String, Double>();
	Map<String, Double> initProbMap = new HashMap<String, Double>();
	Map<String, ArrayList<String>> kNeighborsMap = 
	    new HashMap<String, ArrayList<String>>();
	String[] tempKeyword = new String[KEYWORD_LIMIT];
	Integer[] keywordIDF = new Integer[KEYWORD_LIMIT];
	trainData(topicIndex, tempKeyword, keywordIDF, probMap, server,
		  initProbMap, kNeighborsMap);
	System.out.println("debugg: Completed trainData");
	int p = 1;
	testResult(probMap, topicIndex, server, initProbMap, kNeighborsMap);
    }

    public static void getInitialProbabilities(SolrServer server,
					       Map<String, Double> initProbMap, String topic, double docsWithTopics)
	throws Exception {
	if (topic.equals("earn")) {
	    System.out.println("debugg: earn");
	}
	SolrQuery probQuery = new SolrQuery("LEWISSPLIT:TRAIN AND TOPICS:"
					    + topic);
	probQuery.setRows(100000);
	QueryResponse response = new QueryResponse();
	response = server.query(probQuery);
	SolrDocumentList docList = response.getResults();
	int size = docList.size();
	initProbMap.put(topic, size / docsWithTopics);
    }

    public static void trainData(String[] topicIndex, String[] tempKeyword,
				 Integer[] keywordIDF, Map<String, Double> probMap,
				 SolrServer server, Map<String, Double> initProbMap,
				 Map<String, ArrayList<String>> kNeighborsMap) {
	SolrQuery query;
	// FacetField tempField;
	// QueryResponse rsp;
	// String tempString;
	int index = 0;
	BufferedReader brReader = null;
	try {
	    String currentTopic;
	    query = new SolrQuery("LEWISSPLIT:TRAIN AND TOPICS:['' TO *]");
	    query.setRows(100000);
	    QueryResponse rs = server.query(query);
	    SolrDocumentList tempDoc = rs.getResults();
	    double docsWithTopics = (double) tempDoc.size();
	    brReader = new BufferedReader(new FileReader("topics.txt"));
	    while ((currentTopic = brReader.readLine()) != null) {
		getInitialProbabilities(server, initProbMap, currentTopic,
					docsWithTopics);
		Integer[] invalidTopic = { 0 };
		topicIndex[index] = currentTopic;
		index += 1;
		query = new SolrQuery("TOPICS:" + currentTopic
				      + " AND LEWISSPLIT:TRAIN");
		// query = new SolrQuery("TOPICS:earn");
		query.setFacet(true);
		// also need to include TOPICS field here as contains keywords
		query.addFacetField("BODY");
		Double total = getQueryResponseDoc(server, query, probMap,
						   invalidTopic, tempKeyword, keywordIDF, currentTopic,
						   kNeighborsMap, true);
		if (total != 0.0) {
		    for (int j = 0; j < KEYWORD_LIMIT; j += 1) {
			if (tempKeyword[j] != null && keywordIDF != null){
			    probMap.put(tempKeyword[j], keywordIDF[j] / total);
			}
		    }
		}
	    }
	    int a = 1;

	} catch (Exception e1) {
	    e1.printStackTrace();
	} finally {
	    try {
		if (brReader != null) {
		    brReader.close();
		}
	    } catch (IOException e2) {
		e2.printStackTrace();
	    }
	}
    }

    public static void testResult(Map<String, Double> probMap,
				  String[] topicIndex, SolrServer server,
				  Map<String, Double> initProbMap, Map<String, 
				  ArrayList<String>> kNeighborsMap) throws Exception {
	String[] docKeywords = new String[KEYWORD_LIMIT];
	Integer[] docFrequency = new Integer[KEYWORD_LIMIT];
	Integer[] uselessTopicCount = { 0 };
	int documentIndex = 0, allDocCount = 0;
	int correctInstance = 0;
	for (int j = 0; j < TOPIC_COUNT; j++) {
	    String topicName = topicIndex[j]; 
	    SolrQuery query = new SolrQuery("LEWISSPLIT:TEST AND TOPICS:" + topicName);
	    //SolrQuery query = new SolrQuery("LEWISSPLIT:TEST AND TOPICS:coffee");
	    query.setFields("NEWID,TOPICS");
	    query.setRows(10000);
	    QueryResponse response = server.query(query);
	    SolrDocumentList testDocList = response.getResults();
	    documentIndex = 0;
	    while (documentIndex < testDocList.size()) {
		SolrDocument testDocument = testDocList.get(documentIndex);
		Object q = testDocument.get("NEWID");
		Object tempTopic = testDocument.getFieldValue("TOPICS");
		ArrayList<String> realTopics = (ArrayList<String>) tempTopic;
		System.out.println("NEWID :" + q.toString());
		String docTopicName = "";
		SolrQuery docQuery = new SolrQuery("NEWID:" + q.toString());
		// SolrQuery docQuery = new SolrQuery("NEWID:21109");
		docQuery.setFacet(true);
		docQuery.addFacetField("BODY");
		getQueryResponseDoc(server, docQuery, probMap,
				    uselessTopicCount, docKeywords, docFrequency,
				    docTopicName, kNeighborsMap, false);
		ArrayList<String>predictionNB = 
		implementNaiveBayes(topicIndex, docKeywords, docFrequency,
				    probMap, initProbMap);
				
		//ArrayList<String> predictionKN = implementKNeighbors(kNeighborsMap, docKeywords,
		//					  topicIndex);
				
		documentIndex += 1;
		allDocCount += 1;
		boolean truth = false;
		for (int p = 0; p < predictionNB.size(); p += 1){
			if(predictionNB.get(p) != null){
				if(realTopics.contains(predictionNB.get(p))){
				    truth = true;
				}
			}
		}
		if (truth){
			System.out.println("NEWID: " + q.toString());
		    System.out.println("Prediction: " + predictionNB + "Correct");
		    correctInstance += 1;
		}
		/*
		if (realTopics.contains(predictionKN)){
		    System.out.println("NEWID: " + q.toString());
		    System.out.println("Prediction: " + predictionKN + "Correct");
		    correctInstance += 1;
		}
		*/
		
	    
	   }
	}
	System.out.println("Correct :" + correctInstance);
	System.out.println("Total :" + allDocCount);
    }

    /**
     * Calculate the probability that a document belongs to a certain topic
     * using Naive Bayes.
     * 
     * @param topicIndex
     *            Array containing topic names
     * @param docKeywords
     *            Array containing 20 keywords from Solr Document
     * @param docFrequency
     *            Array containing probability of each keyword
     * @param probMap
     *            Hashmap containing probabilities for 20 keywords according to
     *            topics
     */
    public static ArrayList<String> implementNaiveBayes(String[] topicIndex,
					     String[] docKeywords, Integer[] docFrequency,
					     Map<String, Double> probMap, Map<String, Double> initProbMap) {

	/*
	 * Iterator it = probMap.entrySet().iterator(); while (it.hasNext()){
	 * Map.Entry k = (Map.Entry) it.next(); String temp =
	 * k.getKey().toString(); System.out.println(temp); if
	 * (temp.matches("^((?=earn+).)*$")){
	 * System.out.println(k.getKey().toString()); } }
	 */

	Double[] resultProb = new Double[TOPIC_COUNT];
	double topicProb = 0.0, max = 0.0;
	int maxIndex = 0;
	String topic;
	for (int i = 0; i < TOPIC_COUNT; i += 1) {
	    if (topicIndex[i] != null) {
		topic = topicIndex[i];
		topicProb = initProbMap.get(topic);
		for (int j = 0; j < KEYWORD_LIMIT; j += 1) {
		    if (docKeywords[j] != null) {
			String key = topic + docKeywords[j];
			if (probMap.containsKey(key)) {
			    topicProb = topicProb * docFrequency[j]
				* probMap.get(key);
			} else {
			    topicProb = topicProb * docFrequency[j]
				* 0.00000000001;
			}
		    }
		}
		resultProb[i] = topicProb;
		if (topicProb > max) {
		    max = topicProb;
		    maxIndex = i;
		}
		// System.out.println("Debugg");
	    }
	}
	//System.out.println("Topic:" + topicIndex[maxIndex]);
	Double [] dupResultProb = (Double[]) resultProb.clone();
	List<Double> values = Arrays.asList(dupResultProb);
	ArrayList<String> possiblePredictions = new ArrayList<String>();
	ArrayList<Integer> trackList = new ArrayList<Integer>();
	Collections.sort(values);
	int n = values.size();
	int wordCount = 0;
	for(int i = 1; i <= 3; i++) {
		Double val = values.get(n-i);
		for(int j = 0; j < resultProb.length; j++) {
			if(resultProb[j] == val && !trackList.contains(j))  {
				System.out.println("Topic  " + j+1 + " : " + topicIndex[j] + val);
				possiblePredictions.add(topicIndex[j]);
				trackList.add(j);
				wordCount += 1;
			}
			if (wordCount >= 3){
				break;
			}
		}
		if (wordCount >= 3){
			break;
		}
	}
    return possiblePredictions;
	
	//return topicIndex[maxIndex];
    }
	

    public static Double getQueryResponseDoc(SolrServer server,
					     SolrQuery docQuery, Map<String, Double> probMap,
					     Integer[] invalidTopic, String[] tempKeyword, Integer[] keywordIDF,
					     String currentTopic, Map<String, ArrayList<String>> kNeighborsMap, 
					     boolean training) throws Exception {

	QueryResponse response = new QueryResponse();
	response = server.query(docQuery);
	List<FacetField> docs = response.getFacetFields();
	Iterator<FacetField> itr = docs.iterator();
	FacetField tempField = itr.next();
	List<Count> tempList = tempField.getValues();
	Double total = 0.0;
	int counter = 0;
	int i = 0;
	while (counter < 20 && i < tempList.size()) {
	    if (i == 80 && counter < 5) {
		invalidTopic[0] = 1;
		break;
	    }
	    FacetField.Count check = tempList.get(i);
	    String tempString = check.toString();
			
	    String a = tempString.substring(0, tempString.indexOf(" "));
	    String b = tempString.substring(tempString.indexOf(" ") + 2,
					    tempString.length() - 1);
			
	    if (a.matches("^[a-zA-Z]+$")) {
		if (training){
		    // add that to neighbors map
		    if (a != null){
			if (kNeighborsMap.containsKey(currentTopic)){
			    kNeighborsMap.get(currentTopic).add("+" + a);
			} else {
			    ArrayList<String> neighborList = new ArrayList<String>();
			    neighborList.add("+" + a);
			    kNeighborsMap.put(currentTopic, neighborList);
			}
		    }
		}
		tempKeyword[counter] = currentTopic + "+" + a;
		keywordIDF[counter] = Integer.parseInt(b);
		total = total + Double.parseDouble(b);
		counter += 1;
		// String tempString = (String)
		// tempDoc.getFieldValue("BODY");
	    }
	    i += 1;
	}
	return total;
    }

	
    public static ArrayList<String> implementKNeighbors(Map<String, ArrayList<String>> kNeighborsMap,
					     String[] docKeywords, String[] topicIndex){
	ArrayList<String> keywordList = new ArrayList<String>(Arrays.asList(docKeywords));
	int max = 0, maxIndex = 5;
	Integer [] similarityMatrix = new Integer[TOPIC_COUNT];
	for (int i = 0; i < TOPIC_COUNT; i += 1){
	    String topicName = topicIndex[i];
	    int similarity = 0;
	    for (int j = 0; j < keywordList.size(); j += 1){
		if (keywordList.get(j) != null && kNeighborsMap.get(topicName) != null){
		    if (kNeighborsMap.get(topicName).contains(keywordList.get(j))){
			similarity += 1;
		    }
		}
	    }
	    similarityMatrix[i] = similarity;
	    if (similarity > max){
		max = similarity;
		maxIndex = i;
	    }
	   // System.out.println("Debugg: k neighbors after one document");
	}
	
	
	Integer [] dupsimilarityMatrix = (Integer[])similarityMatrix.clone();
	List<Integer> values = Arrays.asList(dupsimilarityMatrix);
	ArrayList<String> possiblePredictions = new ArrayList<String>();
	ArrayList<Integer> trackList = new ArrayList<Integer>();
	Collections.sort(values);
	int n = values.size();
	int wordCount = 0;
	for(int i = 1; i <= 3; i++) {
		Integer val = values.get(n-i);
		for(int j = 0; j < similarityMatrix.length; j++) {
			if(similarityMatrix[j] == val && !trackList.contains(j))  {
				System.out.println("Topic  " + j+1 + " : " + topicIndex[j] + val);
				possiblePredictions.add(topicIndex[j]);
				trackList.add(j);
				wordCount += 1;
			}
			if (wordCount >= 3){
				break;
			}
		}
		if (wordCount >= 3){
			break;
		}
	}
    return possiblePredictions;
    }
}
