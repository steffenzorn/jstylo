package edu.drexel.psal.jstylo.generics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.drexel.psal.jstylo.analyzers.WekaAnalyzer;
import edu.drexel.psal.jstylo.analyzers.WriteprintsAnalyzer;
import edu.drexel.psal.jstylo.generics.Logger.LogOut;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.SMO;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * 
 * JStylo SimpleAPI Version .3<br>
 * 
 * A simple API for the inner JStylo functionality.<br>
 * Provides four constructors at the moment (eventually more) <br>
 * After the SimpleAPI is constructed, users need only call prepareInstances(),
 * (sometimes, depending on constructor) prepareAnalyzer(), and run().<br>
 * After fetch the relevant information with the correct get method<br>
 * @author Travis Dutko
 */
/*
 * TODO list:
 * 
 * 1) Add the ability to load pre-made objects (problem sets, cfds)
 * 
 */
public class SimpleAPI {

	///////////////////////////////// Data
	
	//which evaluation to perform enumeration
	public static enum analysisType {CROSS_VALIDATION,TRAIN_TEST_UNKNOWN,TRAIN_TEST_KNOWN};
	
	//Persistant/necessary data
	InstancesBuilder ib; //does the feature extraction
	String classifierPath; //creates analyzer
	Analyzer analysisDriver; //does the train/test/crossVal
	analysisType selected; //type of evaluation
	int numFolds; //folds for cross val (defaults to 10)
	
	//Result Data
	Map<String,Map<String, Double>> trainTestResults;
	Evaluation crossValResults;
	
	///////////////////////////////// Constructors
	
	/**
	 * SimpleAPI constructor. Does not support classifier arguments
	 * @param psXML path to the XML containing the problem set
	 * @param cfdXML path to the XML containing the cumulativeFeatureDriver/feature set
	 * @param numThreads number of calculation threads to use for parallelization
	 * @param classPath path to the classifier to use (of format "weka.classifiers.functions.SMO")
	 * @param type type of analysis to perform
	 */
	public SimpleAPI(String psXML, String cfdXML, int numThreads, String classPath, analysisType type){
		
		ib = new InstancesBuilder(psXML,cfdXML,true,false,numThreads);
		classifierPath = classPath;
		selected = type;
		numFolds = 10;
	}
	
	/**
	 * SimpleAPI constructor. Does not support classifier arguments
	 * @param psXML path to the XML containing the problem set
	 * @param cfdXML path to the XML containing the cumulativeFeatureDriver/feature set
	 * @param numThreads number of calculation threads to use for parallelization
	 * @param classPath path to the classifier to use (of format "weka.classifiers.functions.SMO")
	 * @param type type of analysis to perform
	 * @param nf number of folds to use
	 */
	public SimpleAPI(String psXML, String cfdXML, int numThreads, String classPath, analysisType type, int nf){
		
		ib = new InstancesBuilder(psXML,cfdXML,true,false,numThreads);
		classifierPath = classPath;
		selected = type;
		numFolds = nf;
	}
	
	/**
	 * Constructor for use with a weka classifier. Do not call prepare Analyzer if using this constructor
	 * @param psXML
	 * @param cfdXML
	 * @param numThreads
	 * @param classifier
	 * @param type
	 */
	public SimpleAPI(String psXML, String cfdXML, int numThreads, Classifier classifier, analysisType type){
		ib = new InstancesBuilder(psXML,cfdXML,true,false,numThreads);
		analysisDriver = new WekaAnalyzer(classifier);
		selected = type;
		numFolds = 10;
	}
	
	/**
	 * Constructor for use with a weka classifier. Do not call prepare Analyzer if using this constructor
	 * @param psXML
	 * @param cfdXML
	 * @param numThreads
	 * @param classifier
	 * @param type
	 * @param nf number of folds to use
	 */
	public SimpleAPI(String psXML, String cfdXML, int numThreads, Classifier classifier, analysisType type, int nf){
		ib = new InstancesBuilder(psXML,cfdXML,true,false,numThreads);
		analysisDriver = new WekaAnalyzer(classifier);
		selected = type;
		numFolds = nf;
	}
	
	///////////////////////////////// Methods
	
	/**
	 * Prepares the instances objects (stored within the InstancesBuilder)
	 */
	public void prepareInstances() {

		try {
			ib.extractEventsThreaded(); //extracts events from documents
			ib.initializeRelevantEvents(); //creates the List<EventSet> to pay attention to
			ib.initializeAttributes(); //creates the attribute list to base the Instances on
			ib.createTrainingInstancesThreaded(); //creates train Instances
			ib.createTestInstancesThreaded(); //creates test Instances (if present)
			ib.calculateInfoGain(); //calculates infoGain
		} catch (Exception e) {
			System.out.println("Failed to prepare instances");
			e.printStackTrace();
		}

	}

	/**
	 * Prepares the analyzer for classification
	 */
	public void prepareAnalyzer() {
		try {
			Object tmpObject = null;
			tmpObject = Class.forName(classifierPath).newInstance(); //creates the object from the string

			if (tmpObject instanceof Classifier) { //if it's a weka classifier
				analysisDriver = new WekaAnalyzer(Class.forName(classifierPath) //make a wekaAnalyzer
						.newInstance());
			} else if (tmpObject instanceof WriteprintsAnalyzer) { //otherwise it's a writeprints analyzer
				analysisDriver = new WriteprintsAnalyzer(); 
			}
		} catch (Exception e) {
			System.out.println("Failed to prepare Analyzer");
			e.printStackTrace();
		}
	}
	
	/**
	 * Applies infoGain to the training and testing instances
	 * @param n the number of features/attributes to keep
	 */
	public void applyInfoGain(int n){
		try {
			ib.applyInfoGain(n);
		} catch (Exception e) {
			System.out.println("Failed to apply infoGain");
			e.printStackTrace();
		}
	}
	
	/**
	 * Perform the actual analysis
	 */
	public void run(){
		
		//switch based on the enum
		switch (selected) {
	
		//do a cross val
		case CROSS_VALIDATION:
			crossValResults = analysisDriver.runCrossValidation(ib.getTrainingInstances(), numFolds, 0);
			break;

		// do a train/test
		case TRAIN_TEST_UNKNOWN:
			trainTestResults = analysisDriver.classify(ib.getTrainingInstances(), ib.getTestInstances(), ib.getProblemSet().getAllTestDocs());
			break;

		//do both
		case TRAIN_TEST_KNOWN:
			crossValResults = analysisDriver.runCrossValidation(ib.getTrainingInstances(), numFolds, 0);
			ib.getProblemSet().removeAuthor("_Unknown_");
			trainTestResults = analysisDriver.classify(ib.getTrainingInstances(), ib.getTestInstances(), ib.getProblemSet().getAllTestDocs());
			break;
		
		//should not occur
		default:
			System.out.println("Unreachable. Something went wrong somewhere.");
			break;
		}
		
	}
	
	///////////////////////////////// Setters/Getters
	
	/**
	 * Change the number of folds to use in cross validation
	 * @param n number of folds to use from now on
	 */
	public void setNumFolds(int n){
		numFolds = n;
	}
	
	/**
	 * @return the Instances object describing the training documents
	 */
	public Instances getTrainingInstances(){
		return ib.getTrainingInstances();
	}
	
	/**
	 * @return the Instances object describing the test documents
	 */
	public Instances getTestInstances(){
		return ib.getTestInstances();
	}
	
	/**
	 * @return the infoGain data (not in human readable form lists indices and usefulness)
	 */
	public double[][] getInfoGain(){
		return ib.getInfoGain();
	}
	
	/**
	 * Returns a string of features, in order of most to least useful, with their infogain values<br>
	 * @param showZeroes whether or not to show features that have a 0 as their infoGain value
	 * @return the string representing the infoGain
	 */
	public String getReadableInfoGain(boolean showZeroes){
		String infoString = ">-----InfoGain information: \n\n";
		Instances trainingInstances = ib.getTrainingInstances();
		double[][] infoGain = ib.getInfoGain();
		for (int i = 0; i<infoGain.length; i++){
			if (!showZeroes && (infoGain[i][0]==0))
				break;
			
			infoString+=String.format("> %-50s   %f\n",
					trainingInstances.attribute((int)infoGain[i][1]).name(),
					infoGain[i][0]);
		}
		
		return infoString;
	}
	
	/**
	 * @return Map containing train/test results
	 */
	public Map<String,Map<String, Double>> getTrainTestResults(){
		return trainTestResults;
	}
	
	/**
	 * @return Evaluation containing train/test statistics
	 */
	public Evaluation getTrainTestEval(){
		try {
			Instances train = ib.getTrainingInstances();
			Instances test = ib.getTestInstances();
			test.setClassIndex(test.numAttributes()-1);

			return analysisDriver.getTrainTestEval(train,test);
		} catch (Exception e) {
			Logger.logln("Failed to build evaluation");
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * @return Evaluation containing cross validation results
	 */
	public Evaluation getCrossValEval(){
		return crossValResults;
	}
	
	/**
	 * @return String containing accuracy, metrics, and confusion matrix from cross validation
	 */
	public String getCrossValStatString() {
		
		try {
			Evaluation eval = getCrossValEval();
			String resultsString = "";
			resultsString += eval.toSummaryString(false) + "\n";
			resultsString += eval.toClassDetailsString() + "\n";
			resultsString += eval.toMatrixString() + "\n";
			return resultsString;
		
		} catch (Exception e) {
			System.out
					.println("Failed to get cross validation statistics string");
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @return String containing accuracy and confusion matrix from train/test.
	 * @throws Exception 
	 */
	public String getTrainTestStatString() {
		
		Evaluation eval = getTrainTestEval();
		try {
			return eval.toSummaryString() + "\n" + eval.toClassDetailsString() + "\n" + eval.toMatrixString();
		} catch (Exception e) {
			Logger.logln("Failed to generate stat string!", LogOut.STDERR);
			return null;
		}
	}
	
	/**
	 * @return The accuracy of the given test in percentage format
	 */
	public String getClassificationAccuracy(){
		String results = "";
		
		if (selected == analysisType.CROSS_VALIDATION){
			
			Evaluation crossEval = getCrossValEval();
			String summary = crossEval.toSummaryString();
			int start = summary.indexOf("Correctly classified Instances");
			int end = summary.indexOf("%");
			results+=summary.substring(start,end+1)+"\n";
			
		} else if (selected == analysisType.TRAIN_TEST_KNOWN ){
			String source = getTrainTestStatString();
					
			int start = source.indexOf("Correctly classified");
			int end = source.indexOf("%");

			results += source.substring(start,end+1);
			
		} else {
			Evaluation eval = getTrainTestEval();
			String summary = eval.toSummaryString();
			int start = summary.indexOf("Correctly classified Instances");
			int end = summary.indexOf("%");
			results+=summary.substring(start,end+1)+"\n";

		}
		
		return results;
	}
	
	/**
	 * @return the weka clsasifier being used by the analyzer. Will break something if you try to call it on a non-weka analyzer
	 */
	public Classifier getUnderlyingClassifier(){
		return analysisDriver.getClassifier();
	}
	
	public void writeArff(String path, Instances insts){
		InstancesBuilder.writeToARFF(path,insts);
	}
	
	
	///////////////////////////////// Main method for testing purposes
	/*
	public static void main(String[] args){
		
		SimpleAPI test = new SimpleAPI(
				"./jsan_resources/problem_sets/drexel_1_train_test_new.xml",
				"./jsan_resources/feature_sets/writeprints_feature_set_limited.xml",
				8, "weka.classifiers.functions.SMO",
				analysisType.TRAIN_TEST_UNKNOWN);

		test.prepareInstances();
		test.prepareAnalyzer();
		test.run();
		
		test.writeArff("./training.arff",test.getTrainingInstances());
		test.writeArff("./testing.arff",test.getTestInstances());
		
	}*/
}
