package com.astar.i2r.ins;

import java.awt.EventQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
//import org.gstreamer.Gst;
import org.mapsforge.map.reader.ReadBuffer;
import org.opencv.core.Core;

import com.astar.i2r.ins.gui.MainFrame;

/**
 * Hello world!
 *
 */
public class INS {

	private static final Logger log = Logger.getLogger(INS.class.getName());

	/**
	 * Starts the {@code MapViewer}.
	 * 
	 * @param args
	 *            command line args: expects the map files as multiple
	 *            parameters.
	 */
	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		// args = Gst.init("Inertial Navigation", args);
		// Increase read buffer limit
		ReadBuffer.setMaximumBufferSize(6500000);

		String mapFiles = null;
		String vdoFile = null;
		String imuFile = null;

		Options options = new Options();
		options.addOption("h", "help", false, "show help.");
		options.addOption("m", "map", true,
				"The pathes and names of the Mapsforge Binary Map files");
		options.addOption("v", "vdo", true,
				"The path and name of the Video file.");
		options.addOption("i", "imu", true,
				"The path and name of the IMU file.");
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
			if (cmd.hasOption("h"))
				help(options);
			if (cmd.hasOption("m")) {
				mapFiles = cmd.getOptionValue("m");
			}
			if (cmd.hasOption("v")) {
				vdoFile = cmd.getOptionValue("v");
			}
			if (cmd.hasOption("i")) {
				imuFile = cmd.getOptionValue("i");
			}
		} catch (ParseException e) {
			log.log(Level.SEVERE, "Failed to parse comand line properties", e);
			help(options);
		}

		EventQueue.invokeLater(new MyWindow(mapFiles, vdoFile, imuFile));

	}

	private INS() {
		throw new IllegalStateException();
	}

	private static void help(Options options) {

		// This prints out some help
		HelpFormatter formater = new HelpFormatter();
		formater.printHelp("Main", options);
		System.exit(0);

	}

}

class MyWindow implements Runnable {

	private String mapFiles = null;
	private String vdoFile = null;
	private String imuFile = null;

	public MyWindow(String _mapFiles, String _vdoFile, String _imuFile) {
		mapFiles = _mapFiles;
		vdoFile = _vdoFile;
		imuFile = _imuFile;
	}

	public void run() {
		try {
			MainFrame frame = new MainFrame(mapFiles, vdoFile, imuFile);
			frame.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}