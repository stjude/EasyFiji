package org.stjude.swingui.boot.proc;

import ij.*;
import ij.gui.*;
import ij.plugin.*; 
import ij.plugin.filter.*;
import emblcmci.*; // add jar of package to class path on compile
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import org.stjude.swingui.boot.panel.InfoPanel;

import java.io.InputStream;
import java.io.File;

public class ModifyButtons implements ActionListener {

/*
// Uncomment to use as FIJI plugin for standalone testing...
public class ModifySliders_ implements PlugIn {

	public void run(String arg) {
			// call below methods from here
	}	
*/
	// class wide scope
	ImagePlus imp; // input imp
	ImagePlus chimp; // active channel of input imp
	int curch; // active channel index
	int nZ; // number of slices
	int nT; // number of frames
	JDialog jd;
	private InfoPanel infoPanel; 

	public void Revert() {
		imp = WindowManager.getCurrentImage(); 
		//IJ.showMessage("Testing testing");
		//System.out.println("Testing testingsssss");
		if (imp == null) {
			IJ.showMessage("No image open.");
			return;
		}
	
		IJ.run(imp, "Revert", "");
	}

	public void Undo() {
		imp = WindowManager.getCurrentImage(); 
		if (imp == null) {
			IJ.showMessage("No image open.");
			return;
		}
		
		Undo.undo();
        imp.updateAndDraw();
	}
	
	public ModifyButtons() {
		
		imp = WindowManager.getCurrentImage(); // active image
		if (imp == null) {
            IJ.showMessage("No image open.");
            return;
        }
		
		// refresh the channel buffer
		int originalChannel = imp.getC();
		// Move to another channel (if there is more than one)
		if (imp.getNChannels() > 1) {
			int tempChannel = (originalChannel == 1) ? 2 : 1; // Switch to a different channel
			imp.setC(tempChannel);
			//imp.updateAndDraw();
		}
		// Move back to the original channel
		imp.setC(originalChannel);

		// Pulls current channel of active image
		curch = imp.getC();
		
		//int[] impdimA = imp.getDimensions();
		nZ = imp.getNSlices();
		nT = imp.getNFrames();
		// ImageStack chimgstk = ChannelSplitter.getChannel(imp, curch);
		// chimp = new ImagePlus("active ch stk", chimgstk);
		
	}



	
	public void histoMatch() { 
		// Directly modifies the ip's of single ch, 3D or 4D stacks
		
		
		new Thread(() -> {
			ImagePlus chimpCopy = new Duplicator().run(imp, curch, curch, 1, nZ, 1, nT); 
		chimpCopy.setTitle("active ch stk");
		ImagePlus chimpdup = new Duplicator().run(chimpCopy);
		BleachCorrection_MH BCMH = null;
		BCMH = new BleachCorrection_MH(chimpdup);
		BCMH.doCorrection();
		chimpdup.show();
		}).start();
				
	}

	public void expFit() { 

		new Thread(() -> {
			ImagePlus chimpCopy = new Duplicator().run(imp, curch, curch, 1, nZ, 1, nT); 
			chimpCopy.setTitle("active ch stk");
			ImagePlus chimpdup = new Duplicator().run(chimpCopy);
			chimpdup.setDimensions(1, this.nZ, this.nT);
			chimpdup.setOpenAsHyperStack(true); 
			chimpdup.setCalibration(imp.getCalibration());
			BleachCorrection_ExpoFit bcef;
			bcef = new BleachCorrection_ExpoFit(chimpdup);
			bcef.core();
			chimpdup.show();
		}).start();

	}
	
	public void rotate() { 
		// Save state for undo
		ModifySliders ms = ModifySliders.getInstance();
		if (ms != null) {
			ms.saveImageState();
		}
		
		// Applies to all channels
		// IJ.doCommand("Rotate...") does not work b/c there are two menu commands called 'Rotate...'!
		// FIX: Would be nice to eventually drive this with a FIJI GUI slider, but a good preview is essential here	
		Rotator rot = new Rotator();
		PlugInFilterRunner pfr = new PlugInFilterRunner(rot,"",""); // String command and String arg are not used by rot in this case

	}

	public boolean crop() { 
		// Applies to all channels
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null || imp.getRoi() == null) {
			IJ.error("No ROI", "Please draw a rectangle selection before cropping.");
			return false;
		}
		
		IJ.setTool(Toolbar.RECTANGLE); // Preselects polyline tool	
		
		// Build GUI.  Ideally this would be written as its own class.
		jd = new JDialog(); // many useful methods inhereted from Dialog and Window
		jd.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		jd.setTitle("Crop Image");
		
		// Layout
		Container contentPane = jd.getContentPane(); // Best practice to 'add' directly to the JDialog's contentPane which is explicitly a Container
		// Panel to hold elements
		JPanel jp = new JPanel();
		jp.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));  // blank space
		jp.setLayout(new BoxLayout(jp, BoxLayout.Y_AXIS)); // a vertical layout on a Container
   
   		// Adds text
		JLabel jla = new JLabel ("Click on Image to Draw Crop Rectangle."); // JLabels are single line only
		jla.setAlignmentX(Component.CENTER_ALIGNMENT); // centers within whatever space is alloted by Layout
		jla.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0)); 
		jp.add(jla);  
		
		JLabel jlb = new JLabel ("When Finished, Press OK.");
		jlb.setAlignmentX(Component.CENTER_ALIGNMENT);
		jlb.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0)); 		
		jp.add(jlb);
		
		// Adds OK button
		JButton b = new JButton ("OK");
		b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.addActionListener(this);
		jp.add(b);
		
		jd.add(jp); 
		
		jd.pack();
		GUI.centerOnImageJScreen(jd);
		jd.setVisible(true); 

		// Processing begins in response to button press and resides within ActionListener method below....
		return true;
	}	
	
	public void subset() { 
		// Applies to all channels
		IJ.doCommand("Make Substack...");
		
	}
	

	public void info() { 
		// Run macro and extract results to InfoPanel
		ImagePlus imp = IJ.getImage();
		if (imp == null) {
			IJ.showMessage("Error", "No image is open.");
			return;
		}
		
		// Close any existing metadata window first
		closeMetadataWindow();
		
		// Run the MetaDataParser2.ijm macro in background thread
		new Thread(() -> {
			try {
				String macroPath = extractResourceToTemp("/scripts/MetaDataParser2.ijm");
				if (macroPath == null) {
					SwingUtilities.invokeLater(() -> {
						IJ.showMessage("Error", "Could not load MetaDataParser2.ijm");
					});
					return;
				}
				
				System.out.println("Running macro: " + macroPath);
				IJ.runMacroFile(macroPath);
				System.out.println("Macro completed");
				
				// Wait briefly for window to be created, then extract data
				Thread.sleep(300);
				
				SwingUtilities.invokeLater(() -> {
					// Debug: List all open frames
					Frame[] frames = Frame.getFrames();
					System.out.println("Total frames open: " + frames.length);
					for (Frame frame : frames) {
						System.out.println("Frame title: " + frame.getTitle());
					}
					
					InfoPanel infoPanel = findInfoPanel();
					System.out.println("InfoPanel found: " + (infoPanel != null));
					
					if (infoPanel != null) {
						String channelInfo = extractChannelInfoFromWindow();
						String systemConfig = extractSystemConfigFromWindow();
						
						System.out.println("Channel info: " + channelInfo.substring(0, Math.min(100, channelInfo.length())));
						System.out.println("System config: " + systemConfig);
						
						if (channelInfo.equals("No metadata window found") || systemConfig.equals("No metadata window found")) {
							infoPanel.setChannelInfo("Error: Could not extract metadata");
							infoPanel.setSystemConfig("Please try again or check if image has metadata");
						} else {
							infoPanel.setChannelInfo(channelInfo);
							infoPanel.setSystemConfig(systemConfig);
							System.out.println("Data set to InfoPanel successfully");
						}
						
						// Close the metadata window
						closeMetadataWindow();
					} else {
						System.out.println("ERROR: InfoPanel not found!");
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
				SwingUtilities.invokeLater(() -> {
					IJ.showMessage("Error", "Failed to run metadata parser: " + e.getMessage());
				});
			}
		}).start();
	}
	
	private String extractChannelInfoFromWindow() {
		// Array.show() creates a TextWindow with the name "image_metadata"
		Frame[] frames = Frame.getFrames();
		for (Frame frame : frames) {
			if (frame.getTitle().equals("image_metadata")) {
				ij.text.TextWindow tw = (ij.text.TextWindow) frame;
				ij.text.TextPanel tp = tw.getTextPanel();
				String text = tp.getText();
				
				// Parse the text - skip header line and first 5 data rows (system config)
				StringBuilder channelInfo = new StringBuilder();
				String[] lines = text.split("\n");
				String[] channelLabels = {"Laser Power", "Laser", "Band Pass", "Gain", "Pinhole"};
				
				int channelNum = 1;
				int propertyIndex = 0;
				
				// Start from row 6 (index 6: header=0, system rows=1-5, channels start at 6)
				for (int i = 6; i < lines.length; i++) {
					String line = lines[i];
					String[] parts = line.split("\t");
					
					if (parts.length >= 1) {
						// Every 5 rows is a new channel
						if (propertyIndex == 0) {
							if (channelNum > 1) {
								channelInfo.append("\n");
							}
							channelInfo.append("Ch").append(channelNum).append("\n");
							channelNum++;
						}
						
						String value = parts.length >= 2 ? parts[1] : parts[0];
						
						// Round Laser and Band Pass to 2 decimal places
						if (propertyIndex == 1) { // Laser
							try {
								String numericPart = value.replaceAll("[^0-9.-]", "");
								if (!numericPart.isEmpty()) {
									double numValue = Double.parseDouble(numericPart);
									String unit = value.replaceAll("[0-9.-]", "").trim();
									value = String.format("%.2f %s", numValue, unit);
								}
							} catch (Exception e) {
								// Keep original value if parsing fails
							}
						} else if (propertyIndex == 2) { // Band Pass (format: "500 - 550 nm")
							try {
								// Split by hyphen or dash to get two numbers
								String[] range = value.split("[-–]");
								if (range.length == 2) {
									String start = range[0].trim().replaceAll("[^0-9.]", "");
									String end = range[1].trim().replaceAll("[^0-9.]", "");
									String unit = value.replaceAll("[0-9.\\s-–]", "").trim();
									
									double startNum = Double.parseDouble(start);
									double endNum = Double.parseDouble(end);
									value = String.format("%.2f - %.2f %s", startNum, endNum, unit);
								}
							} catch (Exception e) {
								// Keep original value if parsing fails
							}
						}
						
						channelInfo.append(channelLabels[propertyIndex]).append(": ").append(value).append("\n");
						
						propertyIndex = (propertyIndex + 1) % 5;
					}
				}
				
				return channelInfo.length() > 0 ? channelInfo.toString() : "No channel data found";
			}
		}
		return "No metadata window found";
	}
	
	private void closeMetadataWindow() {
		Frame[] frames = Frame.getFrames();
		for (Frame frame : frames) {
			if (frame.getTitle().equals("image_metadata")) {
				frame.dispose();
				return;
			}
		}
	}
	
	private String extractSystemConfigFromWindow() {
		// Array.show() creates a TextWindow with the name "image_metadata"
		Frame[] frames = Frame.getFrames();
		for (Frame frame : frames) {
			if (frame.getTitle().equals("image_metadata")) {
				ij.text.TextWindow tw = (ij.text.TextWindow) frame;
				ij.text.TextPanel tp = tw.getTextPanel();
				String text = tp.getText();
				
				// Parse the text - first 5 data rows are system config
				StringBuilder systemConfig = new StringBuilder();
				String[] lines = text.split("\n");
				String[] labels = {"Model", "Objective", "Scan Mode", "Dwell Time", "Voxel Size"};
				
				// Rows 1-5 are system properties (row 0 is header)
				for (int i = 1; i <= 5 && i < lines.length; i++) {
					String value = lines[i].split("\t")[0]; // Get first column value
					systemConfig.append(labels[i-1]).append(": ").append(value).append("\n");
				}
				
				return systemConfig.length() > 0 ? systemConfig.toString() : "No system config found";
			}
		}
		return "No metadata window found";
	}



	public void meta() {
		ImagePlus imp = IJ.getImage();
		if (imp == null) {
			IJ.error("No Image", "Please open an image first.");
			return;
		}
		
		// Run the MetaDataParser2.ijm macro
		String macroPath = extractResourceToTemp("/scripts/MetaDataParser2.ijm");
		if (macroPath != null) {
			IJ.runMacroFile(macroPath);
		} else {
			IJ.error("Error", "Failed to load MetaDataParser2.ijm from resources.");
		}
	}
	
	/**
	 * Extracts a resource file to a temporary location
	 */
	private String extractResourceToTemp(String resourcePath) {
		try {
			InputStream is = getClass().getResourceAsStream(resourcePath);
			if (is == null) {
				System.err.println("Resource not found: " + resourcePath);
				return null;
			}
			
			// Create temp file
			String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
			File tempFile = File.createTempFile("easyfiji_", "_" + fileName);
			tempFile.deleteOnExit();
			
			// Copy resource to temp file
			java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = is.read(buffer)) != -1) {
				fos.write(buffer, 0, bytesRead);
			}
			fos.close();
			is.close();
			
			return tempFile.getAbsolutePath();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private InfoPanel findInfoPanel() {
		// First, try to find the currently focused/visible "Easy Fiji" frame
		for (Window window : Window.getWindows()) {
			if (window instanceof JFrame && window.isVisible() && window.isFocused()) {
				JFrame frame = (JFrame) window;
				if (frame.getTitle().equals("Easy Fiji")) {
					InfoPanel panel = searchForInfoPanel(frame);
					if (panel != null) {
						return panel;
					}
				}
			}
		}
		
		// If no focused frame found, search all visible Easy Fiji frames
		for (Window window : Window.getWindows()) {
			if (window instanceof JFrame && window.isVisible()) {
				JFrame frame = (JFrame) window;
				if (frame.getTitle().equals("Easy Fiji")) {
					InfoPanel panel = searchForInfoPanel(frame);
					if (panel != null) {
						return panel;
					}
				}
			}
		}
		
		// Last resort: search all windows
		for (Window window : Window.getWindows()) {
			if (window instanceof JFrame) {
				JFrame frame = (JFrame) window;
				InfoPanel panel = searchForInfoPanel(frame);
				if (panel != null) {
					return panel;
				}
			}
		}
		return null;
	}
	
	private InfoPanel searchForInfoPanel(JFrame frame) {
		for (Component comp : frame.getContentPane().getComponents()) {
			if (comp instanceof JTabbedPane) {
				JTabbedPane tabs = (JTabbedPane) comp;
				for (int i = 0; i < tabs.getTabCount(); i++) {
					Component tabComponent = tabs.getComponentAt(i);
					if (tabComponent instanceof InfoPanel) {
						return (InfoPanel) tabComponent;
					}
				}
			}
		}
		return null;
	}

	// Responses to JDialog sub-gui buttons...
	public void actionPerformed(ActionEvent e) {  
		jd.setVisible(false);
		jd.dispose();
		
		// The response
		IJ.doCommand("Crop");
	} 

}
