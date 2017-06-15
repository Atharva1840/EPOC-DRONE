import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;

import de.yadrone.base.ARDrone;
import de.yadrone.base.IARDrone;
import de.yadrone.base.command.CommandManager;
import de.yadrone.base.navdata.BatteryListener;
import de.yadrone.base.navdata.NavDataManager;

import com.emotiv.Iedk.*;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.*;

public class EpocDrone extends JPanel implements KeyListener {

	private static final long serialVersionUID = 6707071768838413083L;

	private static String droneAction = "null";
	private static String droneBatteryLevel = "null";

	static IARDrone drone = new ARDrone();
	static CommandManager droneCmd;
	static NavDataManager droneNav;

	static int droneSpeed = 20;
	static int droneDoFor = 200;
	static long epocCognitiveSampleTime = 2000;

	static boolean useDrone = true;
	static boolean useEmotiv = true;
	static boolean isTraining = false;
	static boolean inCognitiveMode = true;

	private static String expressiveModelFile = "expressive.train.model";
	private static String cognitiveModelFile = "cognitive.train.model";

	private static int commandCount = 1;
	private static int cognitiveSampleCount = 0;

	static long smileTimeMillis = 0;
	static long smileTimeGap = 5000; 

	static long blinkTimeMillis = 0;
	static long blinkTimeGap = 1000;

	static JFrame f = new JFrame();

	static BatteryListener btListener = new BatteryListener() {

		@Override
		public void voltageChanged(int arg0) {
		}

		@Override
		public void batteryLevelChanged(int level) {
			droneBatteryLevel = "" + level;
		}
	};

	private static boolean hovered;

	public EpocDrone() {
		this.setPreferredSize(new Dimension(700, 300));
		addKeyListener(this);
	}

	public void addNotify() {
		super.addNotify();
		requestFocus();
	}

	public void paintComponent(Graphics g) {
		g.clearRect(0, 0, getWidth(), getHeight());
		g.setFont(new Font("Courier", Font.BOLD,30));
		g.drawString("Action: " + droneAction, 10, 200);
		g.drawString("Battery level: " + droneBatteryLevel, 10, 150);
		g.drawString("Epoc In Cognitive mode: " + inCognitiveMode, 10, 100);		
	}

	public static void main(String[] args) throws InterruptedException, IOException {

		f.getContentPane().add(new EpocDrone());
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.pack();
		f.setVisible(true);

		if (useDrone) {
			droneFunc();
		}

		if (useEmotiv) {
			emotivFunc();
		}

		f.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				if (useDrone)
					drone.stop();
			}
		});
	}

	private static void droneFunc() {
		drone.start();

		droneCmd = drone.getCommandManager();

		droneCmd.setOutdoor(true, true);

		droneNav = drone.getNavDataManager();
		droneNav.addBatteryListener(btListener);
	}

	private static void emotivFunc() throws IOException, InterruptedException {
		Pointer eEvent = Edk.INSTANCE.IEE_EmoEngineEventCreate();
		Pointer eState = Edk.INSTANCE.IEE_EmoStateCreate();
		IntByReference userID = null;
		int state = 0;

		if (Edk.INSTANCE.IEE_EngineConnect("Emotiv Systems-5") != EdkErrorCode.EDK_OK.ToInt()) {
			System.out.println("Emotiv Engine start up failed.");
			return;
		}

		EpocLearning epocExpressiveTrainerPrep = new EpocLearning("leftWink.train", 2, expressiveModelFile);
		EpocLearning epocCognitiveTrainerPrep = new EpocLearning("Relax.train", 1, cognitiveModelFile);

		while (true) {
			state = Edk.INSTANCE.IEE_EngineGetNextEvent(eEvent);

			if (state == EdkErrorCode.EDK_OK.ToInt()) {
				int eventType = Edk.INSTANCE.IEE_EmoEngineEventGetType(eEvent);
				Edk.INSTANCE.IEE_EmoEngineEventGetUserId(eEvent, userID);
				Edk.INSTANCE.IEE_EmoEngineEventGetEmoState(eEvent, eState);

				// Check if the mode is being changed
				if (eventType == Edk.IEE_Event_t.IEE_EmoStateUpdated.ToInt()
						&& EmoState.INSTANCE.IS_FacialExpressionGetSmileExtent(eState) == 1.0f) {
					// System.out.println("Smiling");
					int[] inputData = new int[6];
					int testClass = 6;

					inputData[5] = 1;

					double prediction = epocExpressiveTrainerPrep.getPrediction(testClass, inputData);

					if (prediction == 6.0 && System.currentTimeMillis() > smileTimeMillis) {
						inCognitiveMode = !inCognitiveMode;
						System.out.println(commandCount + ": Mode change- InCognitive: " + inCognitiveMode);
						f.repaint();
						smileTimeMillis = System.currentTimeMillis() + smileTimeGap;
					}
				}

				// Check for the hover signal
				if (eventType == Edk.IEE_Event_t.IEE_EmoStateUpdated.ToInt()
						&& EmoState.INSTANCE.IS_FacialExpressionIsBlink(eState) == 1) {

					if (blinkTimeMillis > System.currentTimeMillis()) {
						System.out.println("Hover..");
						epocDroneHover();
						hovered = true;
					} else {
						blinkTimeMillis = System.currentTimeMillis() + blinkTimeGap;
					}
				}

				if (!hovered) {
					// Depending in the mode, perform the training/action
					if (inCognitiveMode) {
						// Thread.sleep(epocCognitiveSampleTime);
						emotivCognitiveAction(eEvent, eState, epocCognitiveTrainerPrep);
					} else {
						if (eventType == Edk.IEE_Event_t.IEE_EmoStateUpdated.ToInt()) {
							emotivExpressiveAction(eEvent, eState, epocExpressiveTrainerPrep);
						}
					}
				}

				hovered = false;

			} else if (state != EdkErrorCode.EDK_NO_EVENT.ToInt()) {
				System.out.println("Internal error in Emotiv Engine!");
				break;
			}
		}

		Edk.INSTANCE.IEE_EngineDisconnect();
		System.out.println("Emotiv Disconnected!");
	}

	private static void emotivCognitiveAction(Pointer eEvent, Pointer eState, EpocLearning epocCognitiveTrainerPrep)
			throws IOException {

		IntByReference userID = new IntByReference(0);
		DoubleByReference alpha = new DoubleByReference(0);
		DoubleByReference low_beta = new DoubleByReference(0);
		DoubleByReference high_beta = new DoubleByReference(0);
		DoubleByReference gamma = new DoubleByReference(0);
		DoubleByReference theta = new DoubleByReference(0);

		double alphaArr[] = new double[14];
		double betaHighArr[] = new double[14];
		double betaLowArr[] = new double[14];

		int result = EdkErrorCode.EDK_HEADSET_NOT_AVAILABLE.ToInt();

		for (int i = 3; i < 17; i++) {
			result = Edk.INSTANCE.IEE_GetAverageBandPowers(userID.getValue(), i, theta, alpha, low_beta, high_beta,
					gamma);
			if (result == EdkErrorCode.EDK_OK.ToInt()) {
				// System.out.print(alpha.getValue()); System.out.print(", ");
				// System.out.print(low_beta.getValue()); System.out.print(",
				// ");
				// System.out.print(high_beta.getValue()); System.out.print(",
				// ");

				alphaArr[i - 3] = alpha.getValue();
				betaHighArr[i - 3] = high_beta.getValue();
				betaLowArr[i - 3] = low_beta.getValue();
			}
		}

		if (result == EdkErrorCode.EDK_OK.ToInt()) {
			if (isTraining) {
				// System.out.println("Alpha: " + alphaArr);
				epocCognitiveTrainerPrep.writeTrainDataLine(alphaArr, betaHighArr, betaLowArr);
			} else {
				if (cognitiveSampleCount % 5 == 0) {
					// cognitiveSampleCount = 0;
					double prediction = epocCognitiveTrainerPrep.getPrediction(alphaArr, betaHighArr, betaLowArr);

					if (prediction != 0.0) {
						System.out.println(commandCount + ": Cognitive class: " + prediction);
						commandCount++;
						epocDroneAction(prediction, eState);
					}
				}

				cognitiveSampleCount++;
			}
		}
	}

	private static void emotivExpressiveAction(Pointer eEvent, Pointer eState, EpocLearning epocExpressiveLearning)
			throws IOException {
		Edk.INSTANCE.IEE_EmoEngineEventGetEmoState(eEvent, eState);
		EmoState es = EmoState.INSTANCE;
		int[] inputData = new int[6];
		int testClass = 0;

		if (EmoState.INSTANCE.IS_FacialExpressionIsBlink(eState) == 1) {
			// System.out.println("Blink");
			inputData[0] = 1;
			testClass = 1;
		}
		if (es.IS_FacialExpressionIsLeftWink(eState) == 1) {
			// System.out.println("LeftWink");
			inputData[1] = 1;
			testClass = 2;
		}
		if (es.IS_FacialExpressionIsRightWink(eState) == 1) {
			// System.out.println("RightWink");
			inputData[2] = 1;
			testClass = 3;
		}
		if (es.IS_FacialExpressionIsLookingLeft(eState) == 1) {
			// System.out.println("LookingLeft");
			inputData[3] = 1;
			testClass = 4;
		}
		if (es.IS_FacialExpressionIsLookingRight(eState) == 1) {
			// System.out.println("LookingRight");
			inputData[4] = 1;
			testClass = 5;
		}
		// if (es.IS_FacialExpressionGetSmileExtent(eState) == 1.0f) {
		// // System.out.println("Smiling");
		// inputData[5] = 1;
		// testClass = 6;
		// }

		if (isTraining)
			epocExpressiveLearning.writeTrainDataLine(inputData);
		else {
			double prediction = epocExpressiveLearning.getPrediction(testClass, inputData);

			if (prediction != 0.0 && prediction != 1.0) {
				// (testClass == 2 || testClass == 3 || testClass == 6)) {
				System.out.println(commandCount + ": Expressive Class: " + prediction);
				commandCount++;

				epocDroneAction(prediction, eState);
			}
		}

	}

	private static void epocDroneAction(double prediction, Pointer eState) {

		if (inCognitiveMode) {
			// Busy
			if (prediction == 2.0) {
				epocDroneGoUp();
			}

			// Relax
			else if (prediction == 1.0) {
				epocDroneGoDown();
			}
		} else {
			// Left wink
			if (prediction == 2.0) {
				epocDroneForward();
			}

			// Right wink
			else if (prediction == 3.0) {
				epocDroneBackward();
			}

			// Left look
			else if (prediction == 4.0)
				epocDroneTurnLeft();

			// Right look
			else if (prediction == 5.0)
				epocDroneTurnRight();
		}

		f.repaint();
	}

	@Override
	public void keyPressed(KeyEvent e) {
		int key = e.getKeyCode();

		if (key == KeyEvent.VK_UP) {
			epocDroneForward();
		} else if (key == KeyEvent.VK_DOWN) {
			epocDroneBackward();
		} else if (key == KeyEvent.VK_LEFT) {
			epocDroneTurnLeft();
		} else if (key == KeyEvent.VK_RIGHT) {
			epocDroneTurnRight();
		} else if (key == KeyEvent.VK_T) {
			epocDroneTakeoff();
		} else if (key == KeyEvent.VK_L) {
			epocDroneLand();
		} else if (key == KeyEvent.VK_A) {
			epocDroneGoLeft();
		} else if (key == KeyEvent.VK_D) {
			epocDroneGoRight();
		} else if (key == KeyEvent.VK_W) {
			epocDroneGoUp();
		} else if (key == KeyEvent.VK_S) {
			epocDroneGoDown();
		} else if (key == KeyEvent.VK_H) {
			epocDroneHover();
		} else if (key == KeyEvent.VK_SPACE) {
			inCognitiveMode = !inCognitiveMode;
		}

		f.repaint();
	}

	private static void epocDroneHover() {
		droneAction = "HOVER";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.hover();
	}

	static void epocDroneGoUp() {
		droneAction = "UP";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.up(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();
	}

	static void epocDroneGoDown() {
		droneAction = "DOWN";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.down(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
		// epocHover();
		// repaint();
	}

	@Override
	public void keyTyped(KeyEvent e) {

	}

	static void epocDroneTakeoff() {
		droneAction = "Takeoff";
		f.repaint();

		if (!useDrone)
			return;

		// droneCmd.setMaxAltitude(20000);
//		drone.reset();
		droneCmd.takeOff().doFor(5000);
		// droneCmd.up(droneSpeed);
//		droneCmd.hover();
		 
	}

	static void epocDroneLand() {
		droneAction = "Land";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.landing();
		// drone.stop();
		f.repaint();
	}

	static void epocDroneForward() {
		droneAction = "FORWARD";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.forward(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();
	}

	static void epocDroneBackward() {
		droneAction = "BACKWARD";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.backward(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();
	}

	static void epocDroneTurnLeft() {
		droneAction = "TURN LEFT";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.spinLeft(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();
	}

	static void epocDroneTurnRight() {
		droneAction = "TURN RIGHT";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.spinRight(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();

	}

	static private void epocDroneGoLeft() {
		droneAction = "GO LEFT";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.goLeft(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();

	}

	static private void epocDroneGoRight() {
		droneAction = "GO RIGHT";
		f.repaint();

		if (!useDrone)
			return;

		droneCmd.goRight(droneSpeed);// .doFor(droneDoFor);
		// droneCmd.hover();
	}
}
