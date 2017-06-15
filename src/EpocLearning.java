import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

public class EpocLearning {
	
	static String TRAIN_DIR =  "D:/Extras/UCF/4_Spring_2017/training/";
	String trainFile;
	int trainClass;
	BufferedWriter trainOut;
	String modelFile;
	
	public EpocLearning(String trainFile, int trainClass, String modelFile) throws IOException {
		this.trainFile = TRAIN_DIR + trainFile;
		trainOut = new BufferedWriter(new FileWriter(this.trainFile));
		this.trainClass = trainClass;	
		this.modelFile = TRAIN_DIR + modelFile;
	}
	
	public void writeTrainDataLine(int[] data) throws IOException
	{
		int posCnt = 0;
		
		if (dataIsEmpty(data))
			return;
		
		trainOut.write("" + trainClass);
		
		for (int i = 0; i < data.length; i++) {
			posCnt++;
			
			if (data[i] == 0)
				continue;				
			
			trainOut.write(" " + posCnt + ":" + data[i]);
		}
		
		trainOut.newLine();
		trainOut.flush();
	}
	
	public void writeTrainDataLine(double[] alphaArr, double[] betaHighArr, double[] betaLowArr) throws IOException {
		
		int posCnt = 0;
		
		if (dataIsEmpty(alphaArr) || dataIsEmpty(betaLowArr) || dataIsEmpty(betaHighArr))
			return;
		
		trainOut.write("" + trainClass);
		
		posCnt = writeBand(trainOut, alphaArr, posCnt);
		posCnt = writeBand(trainOut, betaHighArr, posCnt);
		posCnt = writeBand(trainOut, betaLowArr, posCnt);
		
		trainOut.newLine();
		trainOut.flush();		
	}

	private static int writeBand(BufferedWriter trainOut, double[] bandArr, int posCount) throws IOException
	{
		for (int i = 0; i < 14; i++) {
			posCount++;
			
			if (bandArr[i] == 0.0)
				continue;				
			
			trainOut.write(" " + posCount + ":" + bandArr[i]);
		}
		
		return posCount;
	}
	

	public double getPrediction(int testClass, int[] data) throws IOException
	{
		if (dataIsEmpty(data))
			return 0.0;
		
		svm_model model = svm.svm_load_model(modelFile);
		if (model == null)
			return 0.0;
		
		if(svm.svm_check_probability_model(model)!=0)
		{
			svm_predict.info("Model supports probability estimates, but disabled in prediction.\n");
		}
		
		String input = "" + testClass;
		
		for (int i = 0; i < data.length; i++) {
			if (data[i] == 0)
				continue;
			input += " " + i + ":" + data[i];
		}
		
		return predict(input, model);
	}
	
	public double getPrediction(double[] alphaArr, double[] betaHighArr, double[] betaLowArr) throws IOException {
		
		if (dataIsEmpty(alphaArr) || dataIsEmpty(betaLowArr) || dataIsEmpty(betaHighArr))
			return 0.0;
		
		svm_model model = svm.svm_load_model(modelFile);
		if (model == null)
			return 0.0;
		
		if(svm.svm_check_probability_model(model)!=0)
		{
			svm_predict.info("Model supports probability estimates, but disabled in prediction.\n");
		}
		
		String input = "";
		
		input = generateTestInput(alphaArr, input);
		input = generateTestInput(betaHighArr, input);
		input = generateTestInput(betaLowArr, input);
		
		return predict(input, model);
	}

	private String generateTestInput(double[] arr, String input) {
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] == 0)
				continue;
			input += " " + i + ":" + arr[i];
		}
		return input;
	}
	
	private boolean dataIsEmpty(int[] data) {
		
		for (int i = 0; i < data.length; i++) {
			if (data[i] != 0)
				return false;
		}
		
		return true;		
	}	
	
	private boolean dataIsEmpty(double[] data) {
		
		for (int i = 0; i < data.length; i++) {
			if (data[i] != 0.0)
				return false;
		}
		
		return true;
	}
	
	private double predict(String line, svm_model model) throws IOException
	{
		if(line == null) 
			return 0.0;

		StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");
		
		int m = st.countTokens()/2;
		svm_node[] x = new svm_node[m];
		for(int j=0;j < m; j++)
		{
			x[j] = new svm_node();
			x[j].index = atoi(st.nextToken());
			x[j].value = atof(st.nextToken());
		}

		return svm.svm_predict(model,x);
	}
	
	private static double atof(String s)
	{
		return Double.valueOf(s).doubleValue();
	}

	private static int atoi(String s)
	{
		return Integer.parseInt(s);
	}
}
